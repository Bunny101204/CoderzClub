package com.coderzclub.dto;

public class ContestRunAcceptedResponse {
    private String runId;
    private String status;

    public ContestRunAcceptedResponse() {}

    public ContestRunAcceptedResponse(String runId, String status) {
        this.runId = runId;
        this.status = status;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
