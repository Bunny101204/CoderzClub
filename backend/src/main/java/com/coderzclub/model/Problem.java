package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;
import java.util.List;

/**
 * Represents a coding problem with support for both function-based and stdin/stdout execution modes.
 */
@Document(collection = "problems")
public class Problem {
    @Id
    private String id;
    private String title;
    private String statement; // Problem description (also called description)
    private List<String> tags;
    private String createdBy; // user id
    private Date createdAt = new Date();
    
    // Execution mode: "FUNCTION" (old) or "STDIN_STDOUT" (new)
    private String executionMode = "STDIN_STDOUT"; // Default to new mode
    
    // Fields for STDIN_STDOUT mode (NEW - Recommended)
    private List<TestCase> publicTestCases;  // Public test cases (shown to user)
    private List<TestCase> hiddenTestCases;  // Hidden test cases (not shown to user)
    private String inputFormat;    // Description of input format
    private String outputFormat;   // Description of output format
    private String constraints;    // Problem constraints (e.g., "1 <= n <= 10^5")
    private List<String> hints;    // Hints to help solve the problem
    private String exampleInput;   // Example input for display
    private String exampleOutput;  // Example output for display
    private String exampleExplanation; // Explanation of the example
    
    // Fields for FUNCTION mode (OLD - Deprecated but kept for backward compatibility)
    @Deprecated
    private String template;       // Code template (deprecated)
    @Deprecated
    private String language;       // Programming language (deprecated)
    @Deprecated
    private String className;      // Class name (deprecated)
    @Deprecated
    private String functionName;   // Function name (deprecated)
    @Deprecated
    private List<Parameter> parameters; // Function parameters (deprecated)
    @Deprecated
    private String returnType;     // Return type (deprecated)
    @Deprecated
    private List<TestCase> testCases; // Old test cases (deprecated, use publicTestCases)
    
    // Fields for bundle system and monetization
    private String bundleId; // ID of the problem bundle this belongs to
    private String difficulty; // EASY, MEDIUM, HARD (or BASIC, INTERMEDIATE, ADVANCED, SDE, EXPERT for bundles)
    private boolean isPremium; // Whether this problem requires premium subscription
    private int points; // Points awarded for solving this problem
    private int estimatedTime; // Estimated time to solve in minutes
    private String category; // ALGORITHMS, DATA_STRUCTURES, SYSTEM_DESIGN, etc.

    /**
     * Gets the list of parameters for the function.
     */
    public List<Parameter> getParameters() { return parameters; }
    /**
     * Sets the list of parameters for the function.
     */
    public void setParameters(List<Parameter> parameters) { this.parameters = parameters; }
    /**
     * Gets the return type of the function.
     */
    public String getReturnType() { return returnType; }
    /**
     * Sets the return type of the function.
     */
    public void setReturnType(String returnType) { this.returnType = returnType; }

    // Getters and setters for all fields
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getFunctionName() { return functionName; }
    public void setFunctionName(String functionName) { this.functionName = functionName; }

    public List<TestCase> getTestCases() { return testCases; }
    public void setTestCases(List<TestCase> testCases) { this.testCases = testCases; }

    public List<TestCase> getHiddenTestCases() { return hiddenTestCases; }
    public void setHiddenTestCases(List<TestCase> hiddenTestCases) { this.hiddenTestCases = hiddenTestCases; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    
    // New getters and setters for bundle system
    public String getBundleId() { return bundleId; }
    public void setBundleId(String bundleId) { this.bundleId = bundleId; }
    
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    
    public boolean isPremium() { return isPremium; }
    public void setPremium(boolean premium) { isPremium = premium; }
    
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    
    public int getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(int estimatedTime) { this.estimatedTime = estimatedTime; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    // New getters and setters for STDIN_STDOUT mode
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    
    public List<TestCase> getPublicTestCases() { return publicTestCases; }
    public void setPublicTestCases(List<TestCase> publicTestCases) { this.publicTestCases = publicTestCases; }
    
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
} 