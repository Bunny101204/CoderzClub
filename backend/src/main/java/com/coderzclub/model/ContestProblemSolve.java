package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Authoritative once-per-problem award identity for a contest participant.
 */
@Document(collection = "contest_problem_solves")
@CompoundIndex(
    name = "contestId_userId_problemId_unique_idx",
    def = "{'contestId': 1, 'userId': 1, 'problemId': 1}",
    unique = true
)
public class ContestProblemSolve {
    @Id
    private String id;
    private String contestId;
    private String userId;
    private String problemId;
    private String contestProblemSnapshotId;
    private int awardedPoints;
    private String awardedSubmissionJobId;
    private String awardedSubmissionId;
    private Instant contestSubmittedAt;
    private Instant createdAt;
    private Instant appliedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getContestId() { return contestId; }
    public void setContestId(String contestId) { this.contestId = contestId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getProblemId() { return problemId; }
    public void setProblemId(String problemId) { this.problemId = problemId; }
    public String getContestProblemSnapshotId() { return contestProblemSnapshotId; }
    public void setContestProblemSnapshotId(String contestProblemSnapshotId) {
        this.contestProblemSnapshotId = contestProblemSnapshotId;
    }
    public int getAwardedPoints() { return awardedPoints; }
    public void setAwardedPoints(int awardedPoints) { this.awardedPoints = awardedPoints; }
    public String getAwardedSubmissionJobId() { return awardedSubmissionJobId; }
    public void setAwardedSubmissionJobId(String awardedSubmissionJobId) {
        this.awardedSubmissionJobId = awardedSubmissionJobId;
    }
    public String getAwardedSubmissionId() { return awardedSubmissionId; }
    public void setAwardedSubmissionId(String awardedSubmissionId) {
        this.awardedSubmissionId = awardedSubmissionId;
    }
    public Instant getContestSubmittedAt() { return contestSubmittedAt; }
    public void setContestSubmittedAt(Instant contestSubmittedAt) {
        this.contestSubmittedAt = contestSubmittedAt;
    }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
}
