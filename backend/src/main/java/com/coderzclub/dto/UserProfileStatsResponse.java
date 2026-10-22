package com.coderzclub.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserProfileStatsResponse {
    private int totalPoints;
    private int uniqueProblemsSolved;
    private double successRate;
    private String successRateDefinition;
    private long acceptedJudgedSubmissions;
    private long completedStudentSubmissions;
    private int currentStreak;
    private int longestStreak;
    private Map<String, Integer> difficultySolved = new LinkedHashMap<>();
    private List<Map<String, Object>> activity = new ArrayList<>();
    private String activityTimezone = "UTC";

    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }
    public int getUniqueProblemsSolved() { return uniqueProblemsSolved; }
    public void setUniqueProblemsSolved(int uniqueProblemsSolved) { this.uniqueProblemsSolved = uniqueProblemsSolved; }
    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }
    public String getSuccessRateDefinition() { return successRateDefinition; }
    public void setSuccessRateDefinition(String successRateDefinition) { this.successRateDefinition = successRateDefinition; }
    public long getAcceptedJudgedSubmissions() { return acceptedJudgedSubmissions; }
    public void setAcceptedJudgedSubmissions(long acceptedJudgedSubmissions) { this.acceptedJudgedSubmissions = acceptedJudgedSubmissions; }
    public long getCompletedStudentSubmissions() { return completedStudentSubmissions; }
    public void setCompletedStudentSubmissions(long completedStudentSubmissions) { this.completedStudentSubmissions = completedStudentSubmissions; }
    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }
    public int getLongestStreak() { return longestStreak; }
    public void setLongestStreak(int longestStreak) { this.longestStreak = longestStreak; }
    public Map<String, Integer> getDifficultySolved() { return difficultySolved; }
    public void setDifficultySolved(Map<String, Integer> difficultySolved) { this.difficultySolved = difficultySolved; }
    public List<Map<String, Object>> getActivity() { return activity; }
    public void setActivity(List<Map<String, Object>> activity) { this.activity = activity; }
    public String getActivityTimezone() { return activityTimezone; }
    public void setActivityTimezone(String activityTimezone) { this.activityTimezone = activityTimezone; }

    public int getTotalProblemsSolved() {
        return uniqueProblemsSolved;
    }
}
