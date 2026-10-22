package com.coderzclub.dto;

import com.coderzclub.model.ContestAudienceType;
import com.coderzclub.model.ContestScoringMode;
import com.coderzclub.model.ContestStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ContestUserResponse {
    private String id;
    private String title;
    private String description;
    private Instant startAt;
    private Instant endAt;
    private ContestStatus status;
    private ContestAudienceType audienceType;
    private int problemCount;
    private List<ContestProblemSummary> problems = new ArrayList<>();
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
    public ContestStatus getStatus() { return status; }
    public void setStatus(ContestStatus status) { this.status = status; }
    public ContestAudienceType getAudienceType() { return audienceType; }
    public void setAudienceType(ContestAudienceType audienceType) { this.audienceType = audienceType; }
    public int getProblemCount() { return problemCount; }
    public void setProblemCount(int problemCount) { this.problemCount = problemCount; }
    public List<ContestProblemSummary> getProblems() { return problems; }
    public void setProblems(List<ContestProblemSummary> problems) { this.problems = problems; }
    public ContestScoringMode getScoringMode() { return scoringMode; }
    public void setScoringMode(ContestScoringMode scoringMode) { this.scoringMode = scoringMode; }
}
