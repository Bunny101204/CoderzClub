package com.coderzclub.dto;

public class TestResultResponse {
    // For public tests
    private String input;
    private String expectedOutput;
    private String actualOutput;
    private Boolean passed;
    private Long runtime;
    private Long memory;
    private String errorType;
    private String errorMessage;

    // For hidden test summaries
    private String type; // e.g., "hidden"
    private String status; // e.g., "FAILED" or "PASSED"
    private String message;

    public TestResultResponse() {}

    // Getters and setters
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }

    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }

    public String getActualOutput() { return actualOutput; }
    public void setActualOutput(String actualOutput) { this.actualOutput = actualOutput; }

    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }

    public Long getRuntime() { return runtime; }
    public void setRuntime(Long runtime) { this.runtime = runtime; }

    public Long getMemory() { return memory; }
    public void setMemory(Long memory) { this.memory = memory; }

    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
