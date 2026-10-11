package com.coderzclub.dto;

import com.coderzclub.model.ContestAudienceType;

import java.time.Instant;
import java.util.List;

public class ContestWriteRequest {
    private String title;
    private String description;
    private Instant startAt;
    private Instant endAt;
    private ContestAudienceType audienceType;
    private List<String> batchIds;
    private List<String> problemIds;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public ContestAudienceType getAudienceType() { return audienceType; }
    public void setAudienceType(ContestAudienceType audienceType) { this.audienceType = audienceType; }
    public List<String> getBatchIds() { return batchIds; }
    public void setBatchIds(List<String> batchIds) { this.batchIds = batchIds; }
    public List<String> getProblemIds() { return problemIds; }
    public void setProblemIds(List<String> problemIds) { this.problemIds = problemIds; }
}
