package com.coderzclub.dto;

import java.util.List;

public class BatchIdsRequest {
    private List<String> userIds;
    private List<String> problemIds;
    private List<String> usernames;

    public List<String> getUserIds() { return userIds; }
    public void setUserIds(List<String> userIds) { this.userIds = userIds; }
    public List<String> getUsernames() { return usernames; }
    public void setUsernames(List<String> usernames) { this.usernames = usernames; }
    public List<String> getProblemIds() { return problemIds; }
    public void setProblemIds(List<String> problemIds) { this.problemIds = problemIds; }
}
