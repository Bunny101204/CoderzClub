package com.coderzclub.dto;

import com.coderzclub.model.TestCase;

import java.util.List;

public class ProblemContentUpdate {
    private String title;
    private String statement;
    private String difficulty;
    private String category;
    private List<String> tags;
    private String inputFormat;
    private String outputFormat;
    private String constraints;
    private String exampleInput;
    private String exampleOutput;
    private String exampleExplanation;
    private List<TestCase> publicTestCases;
    private List<TestCase> hiddenTestCases;
    private String bundleId;
    @com.fasterxml.jackson.annotation.JsonProperty("isPremium")
    private Boolean premium;
    private Integer points;
    private Integer estimatedTime;
    private String executionMode;
    private String testcaseVersion;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getInputFormat() { return inputFormat; }
    public void setInputFormat(String inputFormat) { this.inputFormat = inputFormat; }
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
    public String getConstraints() { return constraints; }
    public void setConstraints(String constraints) { this.constraints = constraints; }
    public String getExampleInput() { return exampleInput; }
    public void setExampleInput(String exampleInput) { this.exampleInput = exampleInput; }
    public String getExampleOutput() { return exampleOutput; }
    public void setExampleOutput(String exampleOutput) { this.exampleOutput = exampleOutput; }
    public String getExampleExplanation() { return exampleExplanation; }
    public void setExampleExplanation(String exampleExplanation) { this.exampleExplanation = exampleExplanation; }
    public List<TestCase> getPublicTestCases() { return publicTestCases; }
    public void setPublicTestCases(List<TestCase> publicTestCases) { this.publicTestCases = publicTestCases; }
    public List<TestCase> getHiddenTestCases() { return hiddenTestCases; }
    public void setHiddenTestCases(List<TestCase> hiddenTestCases) { this.hiddenTestCases = hiddenTestCases; }
    public String getBundleId() { return bundleId; }
    public void setBundleId(String bundleId) { this.bundleId = bundleId; }
    public Boolean getPremium() { return premium; }
    public void setPremium(Boolean premium) { this.premium = premium; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public Integer getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(Integer estimatedTime) { this.estimatedTime = estimatedTime; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    public String getTestcaseVersion() { return testcaseVersion; }
    public void setTestcaseVersion(String testcaseVersion) { this.testcaseVersion = testcaseVersion; }
}
