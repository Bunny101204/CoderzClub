package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Document(collection = "users")
public class User {
    @Id
    private String id;
    private String username;
    private String email;
    private String passwordHash;
    private String role; // "admin" or "user"
    private Date createdAt = new Date();
    
    // New fields for subscription and progress tracking
    private String subscriptionId; // Reference to active subscription
    private String subscriptionPlan; // FREE, BASIC, PREMIUM, ENTERPRISE
    private Date subscriptionExpiry;
    private boolean isPremium; // Whether user has premium access
    private int totalPoints; // Total points earned from solving problems
    private int problemsSolved; // Total number of problems solved
    private int currentStreak; // Current daily streak
    private int longestStreak; // Longest daily streak achieved
    private Date lastActiveDate; // Last date user was active
    private List<String> solvedProblemIds = new java.util.ArrayList<>(); // List of solved problem IDs
    private Map<String, Integer> categoryProgress; // Progress by category
    private Map<String, Integer> difficultyProgress; // Progress by difficulty
    private String profilePicture; // URL to profile picture
    private String bio; // User bio/description
    private String location; // User location
    private String website; // User website
    private List<String> socialLinks; // Social media links
    private boolean emailVerified; // Whether email is verified
    private boolean profileComplete; // Whether profile is complete
    
    // Default constructor
    public User() {}
    
    // Constructor with all fields
    public User(String id, String username, String email, String passwordHash, String role, Date createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }
    
    // Getters
    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public Date getCreatedAt() { return createdAt; }
    
    // New getters for subscription and progress
    public String getSubscriptionId() { return subscriptionId; }
    public String getSubscriptionPlan() { return subscriptionPlan; }
    public Date getSubscriptionExpiry() { return subscriptionExpiry; }
    public boolean isPremium() { return isPremium; }
    public int getTotalPoints() { return totalPoints; }
    public int getProblemsSolved() { return problemsSolved; }
    public int getCurrentStreak() { return currentStreak; }
    public int getLongestStreak() { return longestStreak; }
    public Date getLastActiveDate() { return lastActiveDate; }
    public List<String> getSolvedProblemIds() { return solvedProblemIds; }
    public Map<String, Integer> getCategoryProgress() { return categoryProgress; }
    public Map<String, Integer> getDifficultyProgress() { return difficultyProgress; }
    public String getProfilePicture() { return profilePicture; }
    public String getBio() { return bio; }
    public String getLocation() { return location; }
    public String getWebsite() { return website; }
    public List<String> getSocialLinks() { return socialLinks; }
    public boolean isEmailVerified() { return emailVerified; }
    public boolean isProfileComplete() { return profileComplete; }
    
    // Setters
    public void setId(String id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setRole(String role) { this.role = role; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    
    // New setters for subscription and progress
    public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
    public void setSubscriptionPlan(String subscriptionPlan) { this.subscriptionPlan = subscriptionPlan; }
    public void setSubscriptionExpiry(Date subscriptionExpiry) { this.subscriptionExpiry = subscriptionExpiry; }
    public void setPremium(boolean premium) { isPremium = premium; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }
    public void setProblemsSolved(int problemsSolved) { this.problemsSolved = problemsSolved; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }
    public void setLongestStreak(int longestStreak) { this.longestStreak = longestStreak; }
    public void setLastActiveDate(Date lastActiveDate) { this.lastActiveDate = lastActiveDate; }
    public void setSolvedProblemIds(List<String> solvedProblemIds) { this.solvedProblemIds = solvedProblemIds; }
    public void setCategoryProgress(Map<String, Integer> categoryProgress) { this.categoryProgress = categoryProgress; }
    public void setDifficultyProgress(Map<String, Integer> difficultyProgress) { this.difficultyProgress = difficultyProgress; }
    public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }
    public void setBio(String bio) { this.bio = bio; }
    public void setLocation(String location) { this.location = location; }
    public void setWebsite(String website) { this.website = website; }
    public void setSocialLinks(List<String> socialLinks) { this.socialLinks = socialLinks; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public void setProfileComplete(boolean profileComplete) { this.profileComplete = profileComplete; }
} 