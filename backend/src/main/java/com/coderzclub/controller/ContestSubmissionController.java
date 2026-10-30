package com.coderzclub.controller;

import com.coderzclub.config.AuthenticatedUser;
import com.coderzclub.dto.ContestSubmissionJobRequest;
import com.coderzclub.dto.SubmissionJobResponse;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ContestSubmissionAdmissionService;
import com.coderzclub.service.ExecutionUserFacing;
import com.coderzclub.service.IdempotencyClaim;
import com.coderzclub.service.IdempotencyOperations;
import com.coderzclub.service.IncompatibleExecutionException;
import com.coderzclub.service.MutationIdempotencyService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/contests")
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class ContestSubmissionController {
    private static final Logger logger = LoggerFactory.getLogger(ContestSubmissionController.class);

    private final ContestSubmissionAdmissionService admissionService;
    private final UserRepository userRepository;
    private final IdempotencyOperations idempotency;

    public ContestSubmissionController(
        ContestSubmissionAdmissionService admissionService,
        UserRepository userRepository
    ) {
        this(admissionService, userRepository, (IdempotencyOperations) null);
    }

    public ContestSubmissionController(
        ContestSubmissionAdmissionService admissionService,
        UserRepository userRepository,
        IdempotencyOperations idempotency
    ) {
        this.admissionService = admissionService;
        this.userRepository = userRepository;
        this.idempotency = idempotency;
    }

    @Autowired
    public ContestSubmissionController(
        ContestSubmissionAdmissionService admissionService,
        UserRepository userRepository,
        ObjectProvider<IdempotencyOperations> idempotency
    ) {
        this(admissionService, userRepository, idempotency.getIfAvailable());
    }

    @PostMapping("/{contestId}/problems/{problemId}/submission-jobs")
    public ResponseEntity<?> createContestSubmissionJob(
        @PathVariable String contestId,
        @PathVariable String problemId,
        @Valid @RequestBody ContestSubmissionJobRequest request,
        @RequestHeader(value = MutationIdempotencyService.HEADER, required = false) String idempotencyKey
    ) {
        User user = AuthenticatedUser.current(userRepository)
            .filter(actor -> !actor.isDeleted())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        IdempotencyClaim claim = IdempotencyClaim.absent();
        boolean releaseClaim = false;
        if (idempotency != null) {
            claim = idempotency.begin(user.getId(), "contest-submission", idempotencyKey, contestId + ":" + problemId);
            if (claim.replay()) {
                return acceptedJob(claim.resourceId(), null);
            }
            if (claim.inProgress()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(conflictBody(claim));
            }
            releaseClaim = claim.owned();
        }
        if (idempotency != null && releaseClaim && !idempotency.stillHeld(claim)) {
            releaseClaim = false;
            claim = idempotency.begin(user.getId(), "contest-submission", idempotencyKey, contestId + ":" + problemId);
            if (claim.replay()) {
                return acceptedJob(claim.resourceId(), null);
            }
            if (!claim.owned()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(conflictBody(claim));
            }
            releaseClaim = true;
        }
        if (releaseClaim) {
            request.setIdempotencyKey(idempotencyKey);
        }
        try {
            SubmissionJob job = admissionService.admit(user, contestId, problemId, request);
            if (releaseClaim) {
                releaseClaim = false;
                try {
                    idempotency.complete(claim, job.getId());
                } catch (RuntimeException completeError) {
                    logger.warn("Could not record contest idempotency result for job {}", job.getId());
                }
            }
            return acceptedJob(job.getId(), job);
        } catch (IncompatibleExecutionException incompatible) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", incompatible.getMessage(),
                "reason", incompatible.getReason()
            ));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (com.coderzclub.service.ContestAdmissionException e) {
            throw e;
        } catch (com.coderzclub.service.SubmissionAdmissionHttpException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to create contest submission job", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create submission job"));
        } finally {
            if (releaseClaim && idempotency != null) {
                idempotency.abandon(claim);
            }
        }
    }

    private ResponseEntity<?> acceptedJob(String jobId, SubmissionJob job) {
        SubmissionJobResponse resp = new SubmissionJobResponse();
        resp.setJobId(jobId);
        resp.setReference(jobId);
        resp.setStatus(job == null || job.getStatus() == null ? "QUEUED" : job.getStatus().toString());
        if (job != null) {
            resp.setCreatedAt(job.getCreatedAt());
            resp.setCodingDurationSeconds(job.getCodingDurationSeconds());
        }
        resp.setCorrelationId(ExecutionUserFacing.correlationId());
        return ResponseEntity.accepted().body(resp);
    }

    private Map<String, Object> conflictBody(IdempotencyClaim claim) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("error", "This attempt is already being accepted.");
        if (claim != null && claim.resourceId() != null) {
            body.put("jobId", claim.resourceId());
        }
        if (claim != null && claim.operationKey() != null) {
            body.put("operationKey", claim.operationKey());
        }
        return body;
    }
}
