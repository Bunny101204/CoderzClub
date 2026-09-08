package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;
import java.util.List;

/**
 * Represents a bundle of coding problems organized by difficulty level.
 */
@Document(collection = "problem_bundles")
public class ProblemBundle {
    @Id
    private String id;
    private String name;
    private String description;
    private String difficulty; // BASIC, INTERMEDIATE, ADVANCED, SDE, EXPERT
    private String category; // ALGORITHMS, DATA_STRUCTURES, SYSTEM_DESIGN, etc.
    private List<String> problemIds; // List of problem IDs in this bundle
    private int totalProblems;
    private int totalPoints;
    private int estimatedTotalTime; // Total estimated time in minutes
    private boolean isPremium; // Whether this bundle requires premium subscription
    private double price; // Price in USD for premium bundles
    private String currency; // Currency code (USD, EUR, etc.)
    private List<String> tags;
    private String createdBy; // Admin user ID who created this bundle
    private Date createdAt = new Date();
    private Date updatedAt = new Date();
    private boolean isActive; // Whether this bundle is available for users
    private String sharedTemplate; // Shared template code for all problems in this bundle

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getProblemIds() { return problemIds; }
    public void setProblemIds(List<String> problemIds) { this.problemIds = problemIds; }

    public int getTotalProblems() { return totalProblems; }
    public void setTotalProblems(int totalProblems) { this.totalProblems = totalProblems; }

    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }

    public int getEstimatedTotalTime() { return estimatedTotalTime; }
    public void setEstimatedTotalTime(int estimatedTotalTime) { this.estimatedTotalTime = estimatedTotalTime; }

    public boolean isPremium() { return isPremium; }
    public void setPremium(boolean premium) { isPremium = premium; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    public String getSharedTemplate() { return sharedTemplate; }
    public void setSharedTemplate(String sharedTemplate) { this.sharedTemplate = sharedTemplate; }
}
