package com.coderzclub.model;

/**
 * Represents a function parameter for a coding problem.
 * Example: name = "nums", type = "int[]" or "ListNode".
 */
public class Parameter {
    private String name;
    private String type;

    public Parameter() {}

    public Parameter(String name, String type) {
        this.name = name;
        this.type = type;
    }

    /**
     * Gets the parameter name.
     */
    public String getName() { return name; }
    /**
     * Sets the parameter name.
     */
    public void setName(String name) { this.name = name; }
    /**
     * Gets the parameter type (e.g., int, int[], ListNode).
     */
    public String getType() { return type; }
    /**
     * Sets the parameter type.
     */
    public void setType(String type) { this.type = type; }
} 