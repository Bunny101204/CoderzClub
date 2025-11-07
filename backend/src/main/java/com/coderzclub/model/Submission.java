package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;

@Document(collection = "submissions")
public class Submission {
    @Id
    private String id;
    private String userId;
    private String problemId;
    private String code;
    private String language;
    private String result;
    private String output;
    private Date createdAt = new Date();

    // Default constructor
    public Submission() {}

    // Constructor with all fields
    public Submission(String id, String userId, String problemId, String code, String language, String result, String output, Date createdAt) {
        this.id = id;
        this.userId = userId;
        this.problemId = problemId;
        this.code = code;
        this.language = language;
        this.result = result;
        this.output = output;
        this.createdAt = createdAt;
    }

    // Builder pattern implementation
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String userId;
        private String problemId;
        private String code;
        private String language;
        private String result;
        private String output;
        private Date createdAt = new Date();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder problemId(String problemId) {
            this.problemId = problemId;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder output(String output) {
            this.output = output;
            return this;
        }

        public Builder createdAt(Date createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Submission build() {
            return new Submission(id, userId, problemId, code, language, result, output, createdAt);
        }
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getProblemId() { return problemId; }
    public void setProblemId(String problemId) { this.problemId = problemId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
} 