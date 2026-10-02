package com.coderzclub.dto;

import java.util.ArrayList;
import java.util.List;

public class ProblemProgressEntry {
    private String problemId;
    private Integer numericId;
    private String status;
    private List<String> aliases = new ArrayList<>();

    public String getProblemId() {
        return problemId;
    }

    public void setProblemId(String problemId) {
        this.problemId = problemId;
    }

    public Integer getNumericId() {
        return numericId;
    }

    public void setNumericId(Integer numericId) {
        this.numericId = numericId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases == null ? new ArrayList<>() : aliases;
    }
}
