package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "submission_job_summaries")
public class SubmissionJobSummary {
    @Id private String jobId;
    private String userId;
    private String problemId;
    private SubmissionJob.JobStatus status;
    private String finalResult;
    private Long totalRuntime;
    private Long totalMemory;
    private Integer completedTests;
    private Integer totalTests;
    private Integer attemptCount;
    private Date createdAt;
    private Date completedAt;
    @Indexed(expireAfter = "0")
    private Date expireAt;

    public String getJobId() { return jobId; }
    public void setJobId(String value) { jobId = value; }
    public String getUserId() { return userId; }
    public void setUserId(String value) { userId = value; }
    public String getProblemId() { return problemId; }
    public void setProblemId(String value) { problemId = value; }
    public SubmissionJob.JobStatus getStatus() { return status; }
    public void setStatus(SubmissionJob.JobStatus value) { status = value; }
    public String getFinalResult() { return finalResult; }
    public void setFinalResult(String value) { finalResult = value; }
    public Long getTotalRuntime() { return totalRuntime; }
    public void setTotalRuntime(Long value) { totalRuntime = value; }
    public Long getTotalMemory() { return totalMemory; }
    public void setTotalMemory(Long value) { totalMemory = value; }
    public Integer getCompletedTests() { return completedTests; }
    public void setCompletedTests(Integer value) { completedTests = value; }
    public Integer getTotalTests() { return totalTests; }
    public void setTotalTests(Integer value) { totalTests = value; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer value) { attemptCount = value; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date value) { createdAt = value; }
    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date value) { completedAt = value; }
    public Date getExpireAt() { return expireAt; }
    public void setExpireAt(Date value) { expireAt = value; }
}
