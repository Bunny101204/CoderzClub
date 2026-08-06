package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "user_solved_problems")
@CompoundIndex(def = "{ 'userId': 1, 'problemId': 1 }", unique = true)
public class UserSolvedProblem {
    @Id
    private String id;
    private String userId;
    private String problemId;
    private String submissionId;
    private Date solvedAt;
    private int pointsAwarded;
    private String difficulty;
    private String language;

    public UserSolvedProblem() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getProblemId() { return problemId; }
    public void setProblemId(String problemId) { this.problemId = problemId; }

    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }

    public Date getSolvedAt() { return solvedAt; }
    public void setSolvedAt(Date solvedAt) { this.solvedAt = solvedAt; }

    public int getPointsAwarded() { return pointsAwarded; }
    public void setPointsAwarded(int pointsAwarded) { this.pointsAwarded = pointsAwarded; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
