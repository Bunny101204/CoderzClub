package com.coderzclub.dto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ContestRunResponse {
    private String runId;
    private String status;
    private Date createdAt;
    private Date startedAt;
    private Date completedAt;
    private Integer completedTests;
    private Integer totalTests;
    private String errorCode;
    private String errorMessage;
    private List<PublicResult> results = new ArrayList<>();

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date startedAt) { this.startedAt = startedAt; }
    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }
    public Integer getCompletedTests() { return completedTests; }
    public void setCompletedTests(Integer completedTests) { this.completedTests = completedTests; }
    public Integer getTotalTests() { return totalTests; }
    public void setTotalTests(Integer totalTests) { this.totalTests = totalTests; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public List<PublicResult> getResults() { return results; }
    public void setResults(List<PublicResult> results) { this.results = results; }

    public static class PublicResult {
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
}
