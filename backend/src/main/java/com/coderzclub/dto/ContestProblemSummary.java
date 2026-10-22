package com.coderzclub.dto;

import com.coderzclub.model.Problem;

import java.util.List;

/**
 * Safe contest problem reference. Never includes statements, tests, solutions, or admin fields.
 */
public class ContestProblemSummary {
    private String id;
    private Integer numericId;
    private String title;
    private String difficulty;
    private String category;
    private int points;
    private List<String> tags;

    public static ContestProblemSummary from(Problem problem) {
        ContestProblemSummary summary = new ContestProblemSummary();
        if (problem == null) {
            return summary;
        }
        summary.setId(problem.getId());
        summary.setNumericId(problem.getNumericId());
        summary.setTitle(problem.getTitle());
        summary.setDifficulty(problem.getDifficulty());
        summary.setCategory(problem.getCategory());
        summary.setPoints(problem.getPoints());
        summary.setTags(problem.getTags());
        return summary;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Integer getNumericId() { return numericId; }
    public void setNumericId(Integer numericId) { this.numericId = numericId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
