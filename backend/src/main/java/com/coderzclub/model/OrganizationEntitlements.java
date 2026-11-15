package com.coderzclub.model;

import java.util.ArrayList;
import java.util.List;

public class OrganizationEntitlements {
    private int maxStudents = 500;
    private int maxAdmins = 20;
    private List<String> features = new ArrayList<>();

    public int getMaxStudents() { return maxStudents; }
    public void setMaxStudents(int maxStudents) { this.maxStudents = maxStudents; }
    public int getMaxAdmins() { return maxAdmins; }
    public void setMaxAdmins(int maxAdmins) { this.maxAdmins = maxAdmins; }
    public List<String> getFeatures() { return features; }
    public void setFeatures(List<String> features) {
        this.features = features == null ? new ArrayList<>() : new ArrayList<>(features);
    }
}
