package com.coderzclub.controller;

import com.coderzclub.config.AuthenticatedUser;
import com.coderzclub.dto.ContestSubmissionJobRequest;
import com.coderzclub.dto.SubmissionJobResponse;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ContestSubmissionAdmissionService;
import com.coderzclub.service.ExecutionUserFacing;
import com.coderzclub.service.IncompatibleExecutionException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    public ContestSubmissionController(
        ContestSubmissionAdmissionService admissionService,
        UserRepository userRepository
    ) {
        this.admissionService = admissionService;
        this.userRepository = userRepository;
    }

    @PostMapping("/{contestId}/problems/{problemId}/submission-jobs")
    public ResponseEntity<?> createContestSubmissionJob(
        @PathVariable String contestId,
        @PathVariable String problemId,
        @Valid @RequestBody ContestSubmissionJobRequest request
    ) {
        User user = AuthenticatedUser.current(userRepository)
            .filter(actor -> !actor.isDeleted())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        try {
            SubmissionJob job = admissionService.admit(user, contestId, problemId, request);
            SubmissionJobResponse resp = new SubmissionJobResponse();
            resp.setJobId(job.getId());
            resp.setReference(job.getId());
            resp.setStatus(job.getStatus().toString());
            resp.setCreatedAt(job.getCreatedAt());
            resp.setCorrelationId(ExecutionUserFacing.correlationId());
            resp.setCodingDurationSeconds(job.getCodingDurationSeconds());
            return ResponseEntity.accepted().body(resp);
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
        }
    }
}
