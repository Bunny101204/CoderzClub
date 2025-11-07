package com.coderzclub.model;

/**
 * Represents a test case with stdin/stdout format.
 * Input is what the user's program will read from stdin.
 * Output is what the user's program should print to stdout.
 */
public class TestCase {
    private String input;      // Raw stdin input (e.g., "4\n2 7 11 15\n9")
    private String output;     // Expected stdout output (e.g., "0 1")
    private String explanation; // Optional explanation of the test case
    
    // For backward compatibility with old function-based format
    private Object inputObject;  // Deprecated: Use input instead
    private Object outputObject; // Deprecated: Use output instead

    public TestCase() {}
    
    public TestCase(String input, String output) {
        this.input = input;
        this.output = output;
    }
    
    public TestCase(String input, String output, String explanation) {
        this.input = input;
        this.output = output;
        this.explanation = explanation;
    }

    // New stdin/stdout getters and setters
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    
    // Deprecated methods for backward compatibility
    @Deprecated
    public Object getInputObject() { return inputObject; }
    @Deprecated
    public void setInputObject(Object inputObject) { this.inputObject = inputObject; }
    
    @Deprecated
    public Object getOutputObject() { return outputObject; }
    @Deprecated
    public void setOutputObject(Object outputObject) { this.outputObject = outputObject; }
} 