package com.coderzclub.dto;

import com.coderzclub.model.ContestAudienceType;
import com.coderzclub.model.ContestScoringMode;
import com.coderzclub.model.ContestStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ContestAdminResponse {
    private String id;
    private String title;
    private String description;
    private Instant startAt;
    private Instant endAt;
    private boolean published;
    private ContestStatus status;
    private ContestAudienceType audienceType;
    private List<String> batchIds = new ArrayList<>();
    private List<String> problemIds = new ArrayList<>();
    private int problemCount;
    private List<ContestProblemSummary> problems = new ArrayList<>();
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private ContestScoringMode scoringMode = ContestScoringMode.POINTS_FIRST_ACCEPTED;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public ContestStatus getStatus() { return status; }
    public void setStatus(ContestStatus status) { this.status = status; }
    public ContestAudienceType getAudienceType() { return audienceType; }
    public void setAudienceType(ContestAudienceType audienceType) { this.audienceType = audienceType; }
    public List<String> getBatchIds() { return batchIds; }
    public void setBatchIds(List<String> batchIds) { this.batchIds = batchIds; }
    public List<String> getProblemIds() { return problemIds; }
    public void setProblemIds(List<String> problemIds) { this.problemIds = problemIds; }
    public int getProblemCount() { return problemCount; }
    public void setProblemCount(int problemCount) { this.problemCount = problemCount; }
    public List<ContestProblemSummary> getProblems() { return problems; }
    public void setProblems(List<ContestProblemSummary> problems) { this.problems = problems; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public ContestScoringMode getScoringMode() { return scoringMode; }
    public void setScoringMode(ContestScoringMode scoringMode) { this.scoringMode = scoringMode; }
}
