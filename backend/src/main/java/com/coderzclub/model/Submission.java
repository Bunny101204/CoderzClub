package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;
import java.util.Map;

@Document(collection = "submissions")
public class Submission {
    @Id
    private String id;
    private String userId;
    private String problemId;
    private String code;
    private String language;
    private String result;  // ACCEPTED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED, RUNTIME_ERROR, COMPILATION_ERROR, MEMORY_LIMIT_EXCEEDED
    private String output;
    private Date createdAt = new Date();
    
    // Enhanced fields for detailed submission tracking
    private Long runtime;              // Execution time in milliseconds
    private Long memory;               // Memory used in bytes
    private String errorMessage;       // Compilation or runtime error message
    private String stderr;             // Standard error output
    private String verdict;            // Detailed verdict from Judge0
    private Integer passedTestCases;   // Number of test cases passed
    private Integer totalTestCases;    // Total number of test cases
    private Map<String, Object> executionDetails; // Full Judge0 response for debugging

    // Default constructor
    public Submission() {}

    // Constructor with all fields
    public Submission(String id, String userId, String problemId, String code, String language, String result, String output, Date createdAt) {
        this.id = id;
        this.userId = userId;
        this.problemId = problemId;
        this.code = code;
        this.language = language;
        this.result = result;
        this.output = output;
        this.createdAt = createdAt;
    }

    // Builder pattern implementation
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String userId;
        private String problemId;
        private String code;
        private String language;
        private String result;
        private String output;
        private Date createdAt = new Date();
        private Long runtime;
        private Long memory;
        private String errorMessage;
        private String stderr;
        private String verdict;
        private Integer passedTestCases;
        private Integer totalTestCases;
        private Map<String, Object> executionDetails;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder problemId(String problemId) {
            this.problemId = problemId;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder output(String output) {
            this.output = output;
            return this;
        }

        public Builder createdAt(Date createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder runtime(Long runtime) {
            this.runtime = runtime;
            return this;
        }

        public Builder memory(Long memory) {
            this.memory = memory;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder stderr(String stderr) {
            this.stderr = stderr;
            return this;
        }

        public Builder verdict(String verdict) {
            this.verdict = verdict;
            return this;
        }

        public Builder passedTestCases(Integer passedTestCases) {
            this.passedTestCases = passedTestCases;
            return this;
        }

        public Builder totalTestCases(Integer totalTestCases) {
            this.totalTestCases = totalTestCases;
            return this;
        }

        public Builder executionDetails(Map<String, Object> executionDetails) {
            this.executionDetails = executionDetails;
            return this;
        }

        public Submission build() {
            Submission submission = new Submission(id, userId, problemId, code, language, result, output, createdAt);
            submission.setRuntime(runtime);
            submission.setMemory(memory);
            submission.setErrorMessage(errorMessage);
            submission.setStderr(stderr);
            submission.setVerdict(verdict);
            submission.setPassedTestCases(passedTestCases);
            submission.setTotalTestCases(totalTestCases);
            submission.setExecutionDetails(executionDetails);
            return submission;
        }
    }

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

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    // Enhanced getters and setters
    public Long getRuntime() { return runtime; }
    public void setRuntime(Long runtime) { this.runtime = runtime; }

    public Long getMemory() { return memory; }
    public void setMemory(Long memory) { this.memory = memory; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getStderr() { return stderr; }
    public void setStderr(String stderr) { this.stderr = stderr; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public Integer getPassedTestCases() { return passedTestCases; }
    public void setPassedTestCases(Integer passedTestCases) { this.passedTestCases = passedTestCases; }

    public Integer getTotalTestCases() { return totalTestCases; }
    public void setTotalTestCases(Integer totalTestCases) { this.totalTestCases = totalTestCases; }

    public Map<String, Object> getExecutionDetails() { return executionDetails; }
    public void setExecutionDetails(Map<String, Object> executionDetails) { this.executionDetails = executionDetails; }
} 