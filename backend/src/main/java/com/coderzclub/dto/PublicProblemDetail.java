package com.coderzclub.dto;

import com.coderzclub.model.Parameter;
import com.coderzclub.model.Problem;
import com.coderzclub.model.TestCase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Student-visible practice problem detail. This is the only problem document
 * stored in the shared public Redis cache. It has no hidden tests, author id,
 * contest eligibility, drafts, or solved state.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublicProblemDetail {
    private String id;
    private Integer numericId;
    private String title;
    private String statement;
    private List<String> tags;
    private Date createdAt;
    private String executionMode;
    private List<TestCase> publicTestCases;
    private String testcaseVersion;
    private String inputFormat;
    private String outputFormat;
    private String constraints;
    private List<String> hints;
    private String exampleInput;
    private String exampleOutput;
    private String exampleExplanation;
    private String template;
    private String language;
    private String className;
    private String functionName;
    private List<Parameter> parameters;
    private String returnType;
    private List<TestCase> testCases;
    private String bundleId;
    private String difficulty;
    private boolean premium;
    private int points;
    private int estimatedTime;
    private String category;
    private String availability;

    public PublicProblemDetail() {}

    public static PublicProblemDetail fromPublicProblem(Problem problem) {
        PublicProblemDetail detail = new PublicProblemDetail();
        detail.id = problem.getId();
        detail.numericId = problem.getNumericId();
        detail.title = problem.getTitle();
        detail.statement = problem.getStatement();
        detail.tags = problem.getTags() == null ? null : List.copyOf(problem.getTags());
        detail.createdAt = problem.getCreatedAt();
        detail.executionMode = problem.getExecutionMode() == null ? null : problem.getExecutionMode().name();
        detail.publicTestCases = copyTests(problem.getPublicTestCases());
        detail.testcaseVersion = problem.getTestcaseVersion();
        detail.inputFormat = problem.getInputFormat();
        detail.outputFormat = problem.getOutputFormat();
        detail.constraints = problem.getConstraints();
        detail.hints = problem.getHints() == null ? null : List.copyOf(problem.getHints());
        detail.exampleInput = problem.getExampleInput();
        detail.exampleOutput = problem.getExampleOutput();
        detail.exampleExplanation = problem.getExampleExplanation();
        detail.template = problem.getTemplate();
        detail.language = problem.getLanguage();
        detail.className = problem.getClassName();
        detail.functionName = problem.getFunctionName();
        detail.parameters = problem.getParameters() == null ? null : List.copyOf(problem.getParameters());
        detail.returnType = problem.getReturnType();
        detail.testCases = copyTests(problem.getTestCases());
        detail.bundleId = problem.getBundleId();
        detail.difficulty = problem.getDifficulty();
        detail.premium = problem.isPremium();
        detail.points = problem.getPoints();
        detail.estimatedTime = problem.getEstimatedTime();
        detail.category = problem.getCategory();
        detail.availability = "PUBLIC";
        return detail;
    }

    private static List<TestCase> copyTests(List<TestCase> source) {
        if (source == null) {
            return null;
        }
        List<TestCase> copies = new ArrayList<>();
        for (TestCase testCase : source) {
            if (testCase == null) {
                continue;
            }
            copies.add(new TestCase(testCase.getInput(), testCase.getOutput(), testCase.getExplanation()));
        }
        return copies;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Integer getNumericId() { return numericId; }
    public void setNumericId(Integer numericId) { this.numericId = numericId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    public List<TestCase> getPublicTestCases() { return publicTestCases; }
    public void setPublicTestCases(List<TestCase> publicTestCases) { this.publicTestCases = publicTestCases; }
    public String getTestcaseVersion() { return testcaseVersion; }
    public void setTestcaseVersion(String testcaseVersion) { this.testcaseVersion = testcaseVersion; }
    public String getInputFormat() { return inputFormat; }
    public void setInputFormat(String inputFormat) { this.inputFormat = inputFormat; }
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
    public String getConstraints() { return constraints; }
    public void setConstraints(String constraints) { this.constraints = constraints; }
    public List<String> getHints() { return hints; }
    public void setHints(List<String> hints) { this.hints = hints; }
    public String getExampleInput() { return exampleInput; }
    public void setExampleInput(String exampleInput) { this.exampleInput = exampleInput; }
    public String getExampleOutput() { return exampleOutput; }
    public void setExampleOutput(String exampleOutput) { this.exampleOutput = exampleOutput; }
    public String getExampleExplanation() { return exampleExplanation; }
    public void setExampleExplanation(String exampleExplanation) { this.exampleExplanation = exampleExplanation; }
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getFunctionName() { return functionName; }
    public void setFunctionName(String functionName) { this.functionName = functionName; }
    public List<Parameter> getParameters() { return parameters; }
    public void setParameters(List<Parameter> parameters) { this.parameters = parameters; }
    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }
    public List<TestCase> getTestCases() { return testCases; }
    public void setTestCases(List<TestCase> testCases) { this.testCases = testCases; }
    public String getBundleId() { return bundleId; }
    public void setBundleId(String bundleId) { this.bundleId = bundleId; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public boolean isPremium() { return premium; }
    public void setPremium(boolean premium) { this.premium = premium; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public int getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(int estimatedTime) { this.estimatedTime = estimatedTime; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }
}
