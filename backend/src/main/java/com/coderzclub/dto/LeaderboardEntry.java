package com.coderzclub.dto;

public class LeaderboardEntry {
    private String id;
    private String username;
    private String profilePicture;
    private int totalPoints;
    private int problemsSolved;
    private long rank;

    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getUsername() { return username; }
    public void setUsername(String value) { username = value; }
    public String getProfilePicture() { return profilePicture; }
    public void setProfilePicture(String value) { profilePicture = value; }
    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int value) { totalPoints = value; }
    public int getProblemsSolved() { return problemsSolved; }
    public void setProblemsSolved(int value) { problemsSolved = value; }
    public long getRank() { return rank; }
    public void setRank(long value) { rank = value; }
}