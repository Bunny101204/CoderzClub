package com.coderzclub.dto;

import com.coderzclub.model.Problem;
import com.coderzclub.model.TestCase;

import java.util.List;
import java.util.stream.Collectors;

public class ProblemListResponse {
    private String id;
    private String title;
    private String statement;
    private List<String> tags;
    private String difficulty;
    private String category;
    private boolean premium;
    private int points;
    private int estimatedTime;
    private String executionMode;
    private List<TestCase> publicTestCases;

    public ProblemListResponse() {}

    public ProblemListResponse(Problem problem) {
        this.id = problem.getId();
        this.title = problem.getTitle();
        this.statement = problem.getStatement();
        this.tags = problem.getTags();
        this.difficulty = problem.getDifficulty();
        this.category = problem.getCategory();
        this.premium = problem.isPremium();
        this.points = problem.getPoints();
        this.estimatedTime = problem.getEstimatedTime();
        this.executionMode = problem.getExecutionMode();
        this.publicTestCases = problem.getPublicTestCases() == null ? null : problem.getPublicTestCases().stream()
                .map(tc -> {
                    TestCase sanitized = new TestCase();
                    sanitized.setInput(tc.getInput());
                    sanitized.setOutput(tc.getOutput());
                    sanitized.setExplanation(tc.getExplanation());
                    return sanitized;
                })
                .collect(Collectors.toList());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public boolean isPremium() { return premium; }
    public void setPremium(boolean premium) { this.premium = premium; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public int getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(int estimatedTime) { this.estimatedTime = estimatedTime; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    public List<TestCase> getPublicTestCases() { return publicTestCases; }
    public void setPublicTestCases(List<TestCase> publicTestCases) { this.publicTestCases = publicTestCases; }
}
