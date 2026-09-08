package com.coderzclub.dto;

import java.util.Date;
import java.util.List;

public class SubmissionJobResponse {
    private String jobId;
    private String status;
    private Date createdAt;
    private Date startedAt;
    private Date completedAt;
    private Object progress;
    private String result;
    private Long runtime;
    private Long memory;
    private List<TestResultResponse> testResults;
    private String error;

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date startedAt) { this.startedAt = startedAt; }

    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }

    public Object getProgress() { return progress; }
    public void setProgress(Object progress) { this.progress = progress; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public Long getRuntime() { return runtime; }
    public void setRuntime(Long runtime) { this.runtime = runtime; }

    public Long getMemory() { return memory; }
    public void setMemory(Long memory) { this.memory = memory; }

    public List<TestResultResponse> getTestResults() { return testResults; }
    public void setTestResults(List<TestResultResponse> testResults) { this.testResults = testResults; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
