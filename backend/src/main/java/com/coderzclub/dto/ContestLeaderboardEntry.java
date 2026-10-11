package com.coderzclub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public class ContestLeaderboardEntry {
    private int rank;
    private String username;
    private int totalPoints;
    private int solvedCount;
    /** Viewer-specific; omitted from Redis page cache. */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean currentUser;

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }
    public int getSolvedCount() { return solvedCount; }
    public void setSolvedCount(int solvedCount) { this.solvedCount = solvedCount; }
    public boolean isCurrentUser() { return currentUser; }
    public void setCurrentUser(boolean currentUser) { this.currentUser = currentUser; }
}
