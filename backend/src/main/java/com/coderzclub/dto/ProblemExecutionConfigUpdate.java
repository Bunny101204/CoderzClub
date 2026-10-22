package com.coderzclub.dto;

public class ProblemExecutionConfigUpdate {
    private String executionMode;
    private String testcaseVersion;

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public String getTestcaseVersion() {
        return testcaseVersion;
    }

    public void setTestcaseVersion(String testcaseVersion) {
        this.testcaseVersion = testcaseVersion;
    }
}
