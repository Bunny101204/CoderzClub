package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-participant contest leaderboard read model. Not a User document.
 */
@Document(collection = "contest_scores")
@CompoundIndexes({
    @CompoundIndex(
        name = "contestId_userId_unique_idx",
        def = "{'contestId': 1, 'userId': 1}",
        unique = true
    ),
    @CompoundIndex(
        name = "contestId_totalPoints_solvedCount_userId_idx",
        def = "{'contestId': 1, 'totalPoints': -1, 'solvedCount': -1, 'userId': 1}"
    )
})
public class ContestScore {
    @Id
    private String id;
    private String contestId;
    private String userId;
    private int totalPoints;
    private int solvedCount;
    private List<String> solvedProblemIds = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getContestId() { return contestId; }
    public void setContestId(String contestId) { this.contestId = contestId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }
    public int getSolvedCount() { return solvedCount; }
    public void setSolvedCount(int solvedCount) { this.solvedCount = solvedCount; }
    public List<String> getSolvedProblemIds() { return solvedProblemIds; }
    public void setSolvedProblemIds(List<String> solvedProblemIds) {
        this.solvedProblemIds = solvedProblemIds;
    }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
