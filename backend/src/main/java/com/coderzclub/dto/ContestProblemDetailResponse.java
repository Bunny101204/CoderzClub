package com.coderzclub.dto;

import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.Parameter;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.TestCase;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Safe student view of a published contest problem. Hidden tests are never copied here.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContestProblemDetailResponse {
    private String id;
    private Integer numericId;
    private String title;
    private String statement;
    private List<String> tags;
    private String difficulty;
    private String category;
    private boolean premium;
    private int points;
    private int estimatedTime;
    private String executionMode;
    private String testcaseVersion;
    private String inputFormat;
    private String outputFormat;
    private String constraints;
    private List<String> hints;
    private String exampleInput;
    private String exampleOutput;
    private String exampleExplanation;
    private List<TestCase> publicTestCases;
    private String template;
    private String functionName;
    private List<Parameter> parameters;
    private String className;
    private String returnType;
    private String availability;

    public static ContestProblemDetailResponse from(ContestProblemSnapshot snapshot) {
        ContestProblemDetailResponse response = new ContestProblemDetailResponse();
        if (snapshot == null) {
            return response;
        }
        response.id = snapshot.getProblemId();
        response.numericId = snapshot.getNumericId();
        response.title = snapshot.getTitle();
        response.statement = snapshot.getStatement();
        response.tags = snapshot.getTags() == null ? null : new ArrayList<>(snapshot.getTags());
        response.difficulty = snapshot.getDifficulty();
        response.category = snapshot.getCategory();
        response.premium = snapshot.isPremium();
        response.points = snapshot.getPoints();
        response.estimatedTime = snapshot.getEstimatedTime();
        response.executionMode = snapshot.getExecutionMode() == null ? null : snapshot.getExecutionMode().name();
        response.testcaseVersion = snapshot.getTestcaseVersion();
        response.inputFormat = snapshot.getInputFormat();
        response.outputFormat = snapshot.getOutputFormat();
        response.constraints = snapshot.getConstraints();
        response.hints = snapshot.getHints() == null ? null : new ArrayList<>(snapshot.getHints());
        response.exampleInput = snapshot.getExampleInput();
        response.exampleOutput = snapshot.getExampleOutput();
        response.exampleExplanation = snapshot.getExampleExplanation();
        response.publicTestCases = ContestProblemSnapshot.copyTests(snapshot.getPublicTestCases());
        response.template = snapshot.getTemplate();
        response.functionName = snapshot.getFunctionName();
        response.parameters = snapshot.getParameters() == null ? null : new ArrayList<>(snapshot.getParameters());
        response.className = snapshot.getClassName();
        response.returnType = snapshot.getReturnType();
        response.availability = snapshot.getAvailability() == null
            ? ProblemAvailability.PUBLIC.name()
            : snapshot.getAvailability().name();
        return response;
    }

    public String getId() { return id; }
    public Integer getNumericId() { return numericId; }
    public String getTitle() { return title; }
    public String getStatement() { return statement; }
    public List<String> getTags() { return tags; }
    public String getDifficulty() { return difficulty; }
    public String getCategory() { return category; }
    public boolean isPremium() { return premium; }
    public int getPoints() { return points; }
    public int getEstimatedTime() { return estimatedTime; }
    public String getExecutionMode() { return executionMode; }
    public String getTestcaseVersion() { return testcaseVersion; }
    public String getInputFormat() { return inputFormat; }
    public String getOutputFormat() { return outputFormat; }
    public String getConstraints() { return constraints; }
    public List<String> getHints() { return hints; }
    public String getExampleInput() { return exampleInput; }
    public String getExampleOutput() { return exampleOutput; }
    public String getExampleExplanation() { return exampleExplanation; }
    public List<TestCase> getPublicTestCases() { return publicTestCases; }
    public String getTemplate() { return template; }
    public String getFunctionName() { return functionName; }
    public List<Parameter> getParameters() { return parameters; }
    public String getClassName() { return className; }
    public String getReturnType() { return returnType; }
    public String getAvailability() { return availability; }
}
