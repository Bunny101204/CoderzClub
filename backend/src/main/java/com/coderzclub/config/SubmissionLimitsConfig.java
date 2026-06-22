package com.coderzclub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for submission security and performance limits
 */
@Component
@ConfigurationProperties(prefix = "submission")
public class SubmissionLimitsConfig {

    // Code size validation
    private int maxCodeLength = 100000;
    private int minCodeLength = 1;

    // Output truncation limits (bytes)
    private int maxStdoutLength = 1048576; // 1 MB
    private int maxStderrLength = 262144;  // 256 KB
    private int maxCompileOutputLength = 262144; // 256 KB

    // Testcase limits
    private int maxTestCasesPerProblem = 500;
    private int maxTestCaseInputLength = 1048576; // 1 MB
    private int maxTestCaseOutputLength = 1048576; // 1 MB

    // Execution limits
    private int maxExecutionTimeSeconds = 15;
    private int maxMemoryKb = 262144; // 256 MB

    // Getters
    public int getMaxCodeLength() {
        return maxCodeLength;
    }

    public void setMaxCodeLength(int maxCodeLength) {
        this.maxCodeLength = maxCodeLength;
    }

    public int getMinCodeLength() {
        return minCodeLength;
    }

    public void setMinCodeLength(int minCodeLength) {
        this.minCodeLength = minCodeLength;
    }

    public int getMaxStdoutLength() {
        return maxStdoutLength;
    }

    public void setMaxStdoutLength(int maxStdoutLength) {
        this.maxStdoutLength = maxStdoutLength;
    }

    public int getMaxStderrLength() {
        return maxStderrLength;
    }

    public void setMaxStderrLength(int maxStderrLength) {
        this.maxStderrLength = maxStderrLength;
    }

    public int getMaxCompileOutputLength() {
        return maxCompileOutputLength;
    }

    public void setMaxCompileOutputLength(int maxCompileOutputLength) {
        this.maxCompileOutputLength = maxCompileOutputLength;
    }

    public int getMaxTestCasesPerProblem() {
        return maxTestCasesPerProblem;
    }

    public void setMaxTestCasesPerProblem(int maxTestCasesPerProblem) {
        this.maxTestCasesPerProblem = maxTestCasesPerProblem;
    }

    public int getMaxTestCaseInputLength() {
        return maxTestCaseInputLength;
    }

    public void setMaxTestCaseInputLength(int maxTestCaseInputLength) {
        this.maxTestCaseInputLength = maxTestCaseInputLength;
    }

    public int getMaxTestCaseOutputLength() {
        return maxTestCaseOutputLength;
    }

    public void setMaxTestCaseOutputLength(int maxTestCaseOutputLength) {
        this.maxTestCaseOutputLength = maxTestCaseOutputLength;
    }

    public int getMaxExecutionTimeSeconds() {
        return maxExecutionTimeSeconds;
    }

    public void setMaxExecutionTimeSeconds(int maxExecutionTimeSeconds) {
        this.maxExecutionTimeSeconds = maxExecutionTimeSeconds;
    }

    public int getMaxMemoryKb() {
        return maxMemoryKb;
    }

    public void setMaxMemoryKb(int maxMemoryKb) {
        this.maxMemoryKb = maxMemoryKb;
    }
}
