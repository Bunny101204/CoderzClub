package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Document(collection = "submission_jobs")
public class SubmissionJob {
    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String problemId;

    private String code;
    private String language;
    private Integer languageId; // Judge0 language ID

    // Job status
    private JobStatus status = JobStatus.PENDING;
    private Date createdAt = new Date();
    private Date startedAt;
    private Date completedAt;

    // Test cases to run
    private List<TestCase> publicTestCases;
    private List<TestCase> hiddenTestCases;

    // Results
    private List<TestResult> testResults;
    private String finalResult; // ACCEPTED, WRONG_ANSWER, etc.
    private String errorMessage;
    private Long totalRuntime; // Max runtime across all tests
    private Long totalMemory;  // Max memory across all tests

    // Progress tracking
    private Integer completedTests = 0;
    private Integer totalTests = 0;

    // For observability
    private Map<String, Object> metadata;

    public enum JobStatus {
        PENDING,     // Job created, waiting to be picked up
        RUNNING,     // Job is being executed
        COMPLETED,   // Job finished successfully
        FAILED,      // Job failed (network error, etc.)
        CANCELLED    // Job was cancelled
    }

    public static class TestCase {
        private String input;
        private String expectedOutput;
        private String explanation;

        public TestCase() {}

        public TestCase(String input, String expectedOutput, String explanation) {
            this.input = input;
            this.expectedOutput = expectedOutput;
            this.explanation = explanation;
        }

        // Getters and setters
        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
        public String getExpectedOutput() { return expectedOutput; }
        public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
    }

    public static class TestResult {
        private String input;
        private String expectedOutput;
        private String actualOutput;
        private boolean passed;
        private Long runtime;
        private Long memory;
        private String errorType;
        private String errorMessage;
        private Map<String, Object> executionDetails;

        public TestResult() {}

        // Getters and setters
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
        public Map<String, Object> getExecutionDetails() { return executionDetails; }
        public void setExecutionDetails(Map<String, Object> executionDetails) { this.executionDetails = executionDetails; }
    }

    // Default constructor
    public SubmissionJob() {}

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getProblemId() { return problemId; }
    public void setProblemId(String problemId) { this.problemId = problemId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Integer getLanguageId() { return languageId; }
    public void setLanguageId(Integer languageId) { this.languageId = languageId; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date startedAt) { this.startedAt = startedAt; }

    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }

    public List<TestCase> getPublicTestCases() { return publicTestCases; }
    public void setPublicTestCases(List<TestCase> publicTestCases) { this.publicTestCases = publicTestCases; }

    public List<TestCase> getHiddenTestCases() { return hiddenTestCases; }
    public void setHiddenTestCases(List<TestCase> hiddenTestCases) { this.hiddenTestCases = hiddenTestCases; }

    public List<TestResult> getTestResults() { return testResults; }
    public void setTestResults(List<TestResult> testResults) { this.testResults = testResults; }

    public String getFinalResult() { return finalResult; }
    public void setFinalResult(String finalResult) { this.finalResult = finalResult; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Long getTotalRuntime() { return totalRuntime; }
    public void setTotalRuntime(Long totalRuntime) { this.totalRuntime = totalRuntime; }

    public Long getTotalMemory() { return totalMemory; }
    public void setTotalMemory(Long totalMemory) { this.totalMemory = totalMemory; }

    public Integer getCompletedTests() { return completedTests; }
    public void setCompletedTests(Integer completedTests) { this.completedTests = completedTests; }

    public Integer getTotalTests() { return totalTests; }
    public void setTotalTests(Integer totalTests) { this.totalTests = totalTests; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}