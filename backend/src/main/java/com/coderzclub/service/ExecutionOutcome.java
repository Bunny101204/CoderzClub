package com.coderzclub.service;

import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.SubmissionJob;

import java.util.ArrayList;
import java.util.List;

public class ExecutionOutcome {
    private List<SubmissionJob.TestResult> results = new ArrayList<>();
    private ExecutionMode configuredMode;
    private ExecutionMode executionModeUsed;
    private String fallbackReason;
    private int logicalTestcases;
    private int providerExecutions;

    public List<SubmissionJob.TestResult> getResults() { return results; }
    public void setResults(List<SubmissionJob.TestResult> results) { this.results = results; }
    public ExecutionMode getConfiguredMode() { return configuredMode; }
    public void setConfiguredMode(ExecutionMode configuredMode) { this.configuredMode = configuredMode; }
    public ExecutionMode getExecutionModeUsed() { return executionModeUsed; }
    public void setExecutionModeUsed(ExecutionMode executionModeUsed) { this.executionModeUsed = executionModeUsed; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
    public int getLogicalTestcases() { return logicalTestcases; }
    public void setLogicalTestcases(int logicalTestcases) { this.logicalTestcases = logicalTestcases; }
    public int getProviderExecutions() { return providerExecutions; }
    public void setProviderExecutions(int providerExecutions) { this.providerExecutions = providerExecutions; }
}
