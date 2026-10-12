package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Ephemeral contest Run job. Not a Submission and never scored.
 */
@Document(collection = "contest_run_jobs")
@CompoundIndex(name = "status_leaseExpiresAt_idx", def = "{'status': 1, 'leaseExpiresAt': 1}")
@CompoundIndex(name = "status_createdAt_idx", def = "{'status': 1, 'createdAt': 1}")
public class ContestRunJob {
    @Id
    private String id;
    private String userId;
    private String contestId;
    private String problemId;
    private String contestProblemSnapshotId;
    private String code;
    private String language;
    private Integer languageId;
    private Status status;
    private Date createdAt;
    private Date startedAt;
    private Date completedAt;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String lockedBy;
    private Date leaseExpiresAt;
    private String errorCode;
    private String errorMessage;
    private Integer completedTests;
    private Integer totalTests;
    private List<PublicTestResult> publicResults = new ArrayList<>();
    /**
     * Mongo TTL deletion time. Queue max age decides whether a waiting run is still worth
     * executing; this field only removes the ephemeral document later.
     */
    @Indexed(expireAfter = "0")
    private Date expiresAt;

    public enum Status {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public static class PublicTestResult {
        private String input;
        private String expectedOutput;
        private String actualOutput;
        private boolean passed;
        private Long runtime;
        private Long memory;
        private String errorType;
        private String errorMessage;

        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
        public String getExpectedOutput() { return expectedOutput; }
        public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }
        public String getActualOutput() { return actualOutput; }
        public void setActualOutput(String actualOutput) { this.actualOutput = actualOutput; }
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public Long getRuntime() { return runtime; }
        public void setRuntime(Long runtime) { this.runtime = runtime; }
        public Long getMemory() { return memory; }
        public void setMemory(Long memory) { this.memory = memory; }
        public String getErrorType() { return errorType; }
        public void setErrorType(String errorType) { this.errorType = errorType; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getContestId() { return contestId; }
    public void setContestId(String contestId) { this.contestId = contestId; }
    public String getProblemId() { return problemId; }
    public void setProblemId(String problemId) { this.problemId = problemId; }
    public String getContestProblemSnapshotId() { return contestProblemSnapshotId; }
    public void setContestProblemSnapshotId(String contestProblemSnapshotId) { this.contestProblemSnapshotId = contestProblemSnapshotId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Integer getLanguageId() { return languageId; }
    public void setLanguageId(Integer languageId) { this.languageId = languageId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date startedAt) { this.startedAt = startedAt; }
    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }
    public Date getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(Date leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getCompletedTests() { return completedTests; }
    public void setCompletedTests(Integer completedTests) { this.completedTests = completedTests; }
    public Integer getTotalTests() { return totalTests; }
    public void setTotalTests(Integer totalTests) { this.totalTests = totalTests; }
    public List<PublicTestResult> getPublicResults() { return publicResults; }
    public void setPublicResults(List<PublicTestResult> publicResults) { this.publicResults = publicResults; }
    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }

    public boolean isTerminal() {
        return status == Status.COMPLETED || status == Status.FAILED;
    }
}
