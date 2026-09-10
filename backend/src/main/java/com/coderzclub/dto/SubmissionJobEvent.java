package com.coderzclub.dto;

import com.coderzclub.model.SubmissionJob;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubmissionJobEvent {
    private String eventId;
    private String jobId;
    private SubmissionJob.JobStatus status;
    private Integer completedTests;
    private Integer totalTests;
    private Integer attemptCount;
    private String result;
    private Long runtime;
    private Long memory;
    private String error;
    private Instant timestamp;

    public static SubmissionJobEvent from(SubmissionJob job) {
        return from(job, job.getStatus());
    }

    public static SubmissionJobEvent from(SubmissionJob job, SubmissionJob.JobStatus status) {
        SubmissionJobEvent event = new SubmissionJobEvent();
        event.eventId = java.util.UUID.randomUUID().toString();
        event.jobId = job.getId();
        event.status = status;
        event.completedTests = job.getCompletedTests();
        event.totalTests = job.getTotalTests();
        event.attemptCount = job.getAttemptCount();
        event.timestamp = Instant.now();
        if (status == SubmissionJob.JobStatus.COMPLETED || status == SubmissionJob.JobStatus.FAILED
            || status == SubmissionJob.JobStatus.TIMEOUT || status == SubmissionJob.JobStatus.CANCELLED) {
            event.result = job.getFinalResult();
            event.runtime = job.getTotalRuntime();
            event.memory = job.getTotalMemory();
            event.error = safeError(job.getErrorMessage() != null ? job.getErrorMessage() : job.getLastError());
        }
        return event;
    }

    private static String safeError(String value) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public SubmissionJob.JobStatus getStatus() { return status; }
    public void setStatus(SubmissionJob.JobStatus status) { this.status = status; }
    public Integer getCompletedTests() { return completedTests; }
    public void setCompletedTests(Integer completedTests) { this.completedTests = completedTests; }
    public Integer getTotalTests() { return totalTests; }
    public void setTotalTests(Integer totalTests) { this.totalTests = totalTests; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Long getRuntime() { return runtime; }
    public void setRuntime(Long runtime) { this.runtime = runtime; }
    public Long getMemory() { return memory; }
    public void setMemory(Long memory) { this.memory = memory; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
