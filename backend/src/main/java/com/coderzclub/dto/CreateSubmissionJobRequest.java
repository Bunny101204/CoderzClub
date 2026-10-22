package com.coderzclub.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a practice submission job.
 * Enforces strict size limits to prevent abuse.
 */
public class CreateSubmissionJobRequest extends SubmissionExecutionRequest {
    @NotBlank(message = "problemId is required")
    private String problemId;

    public String getProblemId() { return problemId; }
    public void setProblemId(String problemId) { this.problemId = problemId; }
}
