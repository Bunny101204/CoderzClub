package com.coderzclub.service;

import com.coderzclub.dto.ContestRunResponse;
import com.coderzclub.model.ContestRunJob;
import com.coderzclub.model.User;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContestRunQueryService {
    private final MongoTemplate mongoTemplate;

    public ContestRunQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public ContestRunResponse getVisible(String runId, User actor, Authentication authentication) {
        if (runId == null || runId.isBlank() || actor == null || actor.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found");
        }
        ContestRunJob job = mongoTemplate.findById(runId, ContestRunJob.class);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found");
        }
        if (!canView(actor, authentication, job)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found");
        }
        return toResponse(job);
    }

    static boolean canView(User actor, Authentication authentication, ContestRunJob job) {
        if (job.getUserId() != null && job.getUserId().equals(actor.getId())) {
            return true;
        }
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                || "ADMIN".equals(authority.getAuthority()));
    }

    static ContestRunResponse toResponse(ContestRunJob job) {
        ContestRunResponse response = new ContestRunResponse();
        response.setRunId(job.getId());
        response.setStatus(job.getStatus() == null ? null : job.getStatus().name());
        response.setCreatedAt(job.getCreatedAt());
        response.setStartedAt(job.getStartedAt());
        response.setCompletedAt(job.getCompletedAt());
        response.setCompletedTests(job.getCompletedTests());
        response.setTotalTests(job.getTotalTests());
        response.setErrorCode(job.getErrorCode());
        response.setErrorMessage(job.getErrorMessage());
        List<ContestRunResponse.PublicResult> results = new ArrayList<>();
        if (job.getStatus() == ContestRunJob.Status.COMPLETED && job.getPublicResults() != null) {
            for (ContestRunJob.PublicTestResult source : job.getPublicResults()) {
                ContestRunResponse.PublicResult item = new ContestRunResponse.PublicResult();
                item.setInput(source.getInput());
                item.setExpectedOutput(source.getExpectedOutput());
                item.setActualOutput(source.getActualOutput());
                item.setPassed(source.isPassed());
                item.setRuntime(source.getRuntime());
                item.setMemory(source.getMemory());
                item.setErrorType(source.getErrorType());
                item.setErrorMessage(source.getErrorMessage());
                results.add(item);
            }
        }
        response.setResults(results);
        return response;
    }
}
