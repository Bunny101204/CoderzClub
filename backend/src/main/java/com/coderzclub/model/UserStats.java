package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;

@Document(collection = "user_stats")
public class UserStats {
    @Id private String userId;
    private long totalSubmissions;
    private long acceptedSubmissions;
    private long rejectedSubmissions;
    private Date lastSubmissionAt;
    private int currentStreak;
    private int longestStreak;
    private int totalPoints;
    private int problemsSolved;
    private Date updatedAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public long getTotalSubmissions() { return totalSubmissions; }
    public void setTotalSubmissions(long value) { totalSubmissions = value; }
    public long getAcceptedSubmissions() { return acceptedSubmissions; }
    public void setAcceptedSubmissions(long value) { acceptedSubmissions = value; }
    public long getRejectedSubmissions() { return rejectedSubmissions; }
    public void setRejectedSubmissions(long value) { rejectedSubmissions = value; }
    public Date getLastSubmissionAt() { return lastSubmissionAt; }
    public void setLastSubmissionAt(Date value) { lastSubmissionAt = value; }
    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int value) { currentStreak = value; }
    public int getLongestStreak() { return longestStreak; }
    public void setLongestStreak(int value) { longestStreak = value; }
    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int value) { totalPoints = value; }
    public int getProblemsSolved() { return problemsSolved; }
    public void setProblemsSolved(int value) { problemsSolved = value; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date value) { updatedAt = value; }
}