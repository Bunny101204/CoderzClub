package com.coderzclub.controller;

import com.coderzclub.config.AuthenticatedUser;
import com.coderzclub.dto.ContestRunAcceptedResponse;
import com.coderzclub.dto.ContestRunRequest;
import com.coderzclub.dto.ContestRunResponse;
import com.coderzclub.model.ContestRunJob;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ContestAdmissionException;
import com.coderzclub.service.ContestRunAdmissionService;
import com.coderzclub.service.ContestRunQueryService;
import com.coderzclub.service.IncompatibleExecutionException;
import com.coderzclub.service.RunLimitExceededException;
import com.coderzclub.service.SubmissionAdmissionHttpException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class ContestRunController {
    private static final Logger logger = LoggerFactory.getLogger(ContestRunController.class);

    private final ContestRunAdmissionService admissionService;
    private final ContestRunQueryService queryService;
    private final UserRepository userRepository;

    public ContestRunController(
        ContestRunAdmissionService admissionService,
        ContestRunQueryService queryService,
        UserRepository userRepository
    ) {
        this.admissionService = admissionService;
        this.queryService = queryService;
        this.userRepository = userRepository;
    }

    @PostMapping("/api/contests/{contestId}/problems/{problemId}/runs")
    public ResponseEntity<?> createRun(
        @PathVariable String contestId,
        @PathVariable String problemId,
        @Valid @RequestBody ContestRunRequest request
    ) {
        User user = AuthenticatedUser.current(userRepository)
            .filter(actor -> !actor.isDeleted())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        try {
            ContestRunJob job = admissionService.admit(user, contestId, problemId, request);
            return ResponseEntity.accepted().body(new ContestRunAcceptedResponse(
                job.getId(),
                job.getStatus() == null ? ContestRunJob.Status.QUEUED.name() : job.getStatus().name()
            ));
        } catch (IncompatibleExecutionException incompatible) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", incompatible.getMessage(),
                "reason", incompatible.getReason()
            ));
        } catch (RunLimitExceededException limited) {
            return RateLimitResponses.from(limited);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (ContestAdmissionException e) {
            throw e;
        } catch (SubmissionAdmissionHttpException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to admit contest run", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Run service is unavailable. Please retry."));
        }
    }

    @GetMapping("/api/contest-runs/{runId}")
    public ContestRunResponse getRun(@PathVariable String runId) {
        User user = AuthenticatedUser.current(userRepository)
            .filter(actor -> !actor.isDeleted())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        return queryService.getVisible(runId, user, SecurityContextHolder.getContext().getAuthentication());
    }
}
