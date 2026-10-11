package com.coderzclub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable copy of a problem as of contest publish. Hidden tests are stored here
 * for judging only and must never be serialized to student APIs.
 */
@Document(collection = "contest_problem_snapshots")
@CompoundIndex(
    name = "contestId_problemId_unique_idx",
    def = "{'contestId': 1, 'problemId': 1}",
    unique = true
)
public class ContestProblemSnapshot {
    @Id
    private String id;
    private String contestId;
    private String problemId;
    private Integer numericId;
    private Instant capturedAt;

    private String title;
    private String statement;
    private String difficulty;
    private String category;
    private List<String> tags;
    private int points;
    private int estimatedTime;
    private boolean premium;
    private String constraints;
    private String inputFormat;
    private String outputFormat;
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

    private ExecutionMode executionMode;
    private String testcaseVersion;
    private List<TestCase> publicTestCases;
    @JsonIgnore
    private List<TestCase> hiddenTestCases;
    private ProblemAvailability availability;

    public static ContestProblemSnapshot fromProblem(String contestId, Problem problem, Instant capturedAt) {
        ContestProblemSnapshot snapshot = new ContestProblemSnapshot();
        snapshot.setContestId(contestId);
        snapshot.setProblemId(problem.getId());
        snapshot.setNumericId(problem.getNumericId());
        snapshot.setCapturedAt(capturedAt);
        snapshot.setTitle(problem.getTitle());
        snapshot.setStatement(problem.getStatement());
        snapshot.setDifficulty(problem.getDifficulty());
        snapshot.setCategory(problem.getCategory());
        snapshot.setTags(copyStrings(problem.getTags()));
        snapshot.setPoints(problem.getPoints());
        snapshot.setEstimatedTime(problem.getEstimatedTime());
        snapshot.setPremium(problem.isPremium());
        snapshot.setConstraints(problem.getConstraints());
        snapshot.setInputFormat(problem.getInputFormat());
        snapshot.setOutputFormat(problem.getOutputFormat());
        snapshot.setHints(copyStrings(problem.getHints()));
        snapshot.setExampleInput(problem.getExampleInput());
        snapshot.setExampleOutput(problem.getExampleOutput());
        snapshot.setExampleExplanation(problem.getExampleExplanation());
        snapshot.setTemplate(problem.getTemplate());
        snapshot.setLanguage(problem.getLanguage());
        snapshot.setClassName(problem.getClassName());
        snapshot.setFunctionName(problem.getFunctionName());
        snapshot.setParameters(copyParameters(problem.getParameters()));
        snapshot.setReturnType(problem.getReturnType());
        snapshot.setExecutionMode(problem.getExecutionMode());
        snapshot.setTestcaseVersion(problem.getTestcaseVersion() == null ? "v1" : problem.getTestcaseVersion());
        snapshot.setPublicTestCases(copyTests(problem.getPublicTestCases()));
        snapshot.setHiddenTestCases(copyTests(problem.getHiddenTestCases()));
        snapshot.setAvailability(ProblemAvailability.of(problem));
        return snapshot;
    }

    public Problem toValidationProblem() {
        Problem problem = new Problem();
        problem.setId(problemId);
        problem.setPublicTestCases(copyTests(publicTestCases));
        problem.setHiddenTestCases(copyTests(hiddenTestCases));
        problem.setExecutionMode(executionMode);
        problem.setTestcaseVersion(testcaseVersion);
        return problem;
    }

    public static List<TestCase> copyTests(List<TestCase> source) {
        if (source == null) {
            return new ArrayList<>();
        }
        List<TestCase> copy = new ArrayList<>(source.size());
        for (TestCase test : source) {
            if (test == null) {
                continue;
            }
            copy.add(new TestCase(test.getInput(), test.getOutput(), test.getExplanation()));
        }
        return copy;
    }

    private static List<Parameter> copyParameters(List<Parameter> source) {
        if (source == null) {
            return new ArrayList<>();
        }
        List<Parameter> copy = new ArrayList<>(source.size());
        for (Parameter parameter : source) {
            if (parameter == null) {
                continue;
            }
            copy.add(new Parameter(parameter.getName(), parameter.getType()));
        }
        return copy;
    }

    private static List<String> copyStrings(List<String> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getContestId() { return contestId; }
    public void setContestId(String contestId) { this.contestId = contestId; }
    public String getProblemId() { return problemId; }
    public void setProblemId(String problemId) { this.problemId = problemId; }
    public Integer getNumericId() { return numericId; }
    public void setNumericId(Integer numericId) { this.numericId = numericId; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
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
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public int getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(int estimatedTime) { this.estimatedTime = estimatedTime; }
    public boolean isPremium() { return premium; }
    public void setPremium(boolean premium) { this.premium = premium; }
    public String getConstraints() { return constraints; }
    public void setConstraints(String constraints) { this.constraints = constraints; }
    public String getInputFormat() { return inputFormat; }
    public void setInputFormat(String inputFormat) { this.inputFormat = inputFormat; }
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
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
    public ExecutionMode getExecutionMode() { return executionMode; }
    public void setExecutionMode(ExecutionMode executionMode) { this.executionMode = executionMode; }
    public String getTestcaseVersion() { return testcaseVersion; }
    public void setTestcaseVersion(String testcaseVersion) { this.testcaseVersion = testcaseVersion; }
    public List<TestCase> getPublicTestCases() { return publicTestCases; }
    public void setPublicTestCases(List<TestCase> publicTestCases) { this.publicTestCases = publicTestCases; }
    public List<TestCase> getHiddenTestCases() { return hiddenTestCases; }
    public void setHiddenTestCases(List<TestCase> hiddenTestCases) { this.hiddenTestCases = hiddenTestCases; }
    public ProblemAvailability getAvailability() { return availability; }
    public void setAvailability(ProblemAvailability availability) { this.availability = availability; }
}
