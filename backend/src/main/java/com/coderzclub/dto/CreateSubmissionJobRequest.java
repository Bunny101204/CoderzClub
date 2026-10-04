package com.coderzclub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a submission job.
 * Enforces strict size limits to prevent abuse.
 */
public class CreateSubmissionJobRequest {
    @NotBlank(message = "problemId is required")
    private String problemId;

    @NotBlank(message = "code is required")
    @Size(min = 1, max = 100000, message = "code must be between 1 and 100000 characters")
    private String code;

    private String language;

    @NotNull(message = "languageId is required")
    @Min(value = 1, message = "languageId must be a positive integer")
    @Max(value = 91, message = "languageId must be a valid Judge0 language ID (max 91)")
    private Integer languageId;

    /** Informational client timer; ignored when invalid. Not used for judging. */
    private Integer codingDurationSeconds;

    public String getProblemId() { return problemId; }
    public void setProblemId(String problemId) { this.problemId = problemId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Integer getLanguageId() { return languageId; }
    public void setLanguageId(Integer languageId) { this.languageId = languageId; }

    public Integer getCodingDurationSeconds() { return codingDurationSeconds; }
    public void setCodingDurationSeconds(Integer codingDurationSeconds) {
        this.codingDurationSeconds = com.coderzclub.service.CodingDuration.sanitize(codingDurationSeconds);
    }
}
