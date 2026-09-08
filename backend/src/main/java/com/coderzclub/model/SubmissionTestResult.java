package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;

/** Per-testcase result. Hidden cases intentionally have no input or expected output fields. */
@Document(collection = "submission_test_results")
@CompoundIndex(name = "job_testcase_attempt_index", def = "{'jobId': 1, 'testcaseIndex': 1, 'attemptCount': 1}", unique = true)
@CompoundIndex(name = "job_testcase_type", def = "{'jobId': 1, 'testcaseType': 1}")
public class SubmissionTestResult {
    @Id
    private String id;
    private String jobId;
    private int testcaseIndex;
    private Integer attemptCount;
    private TestcaseType testcaseType;
    private boolean passed;
    private Long runtime;
    private Long memory;
    private String errorType;
    private String errorMessage;
    private String input;
    private String expectedOutput;
    private String actualOutput;
    @Indexed(expireAfter = "30d")
    private Date createdAt = new Date();

    public enum TestcaseType { PUBLIC, HIDDEN }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public int getTestcaseIndex() { return testcaseIndex; }
    public void setTestcaseIndex(int testcaseIndex) { this.testcaseIndex = testcaseIndex; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public TestcaseType getTestcaseType() { return testcaseType; }
    public void setTestcaseType(TestcaseType testcaseType) { this.testcaseType = testcaseType; }
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
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }
    public String getActualOutput() { return actualOutput; }
    public void setActualOutput(String actualOutput) { this.actualOutput = actualOutput; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}