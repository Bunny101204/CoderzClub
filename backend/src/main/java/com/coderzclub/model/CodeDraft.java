package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One current code draft for a user, scope, problem, and language.
 * Not a submission and not an append-only history.
 */
@Document(collection = "code_drafts")
@CompoundIndex(
    name = "userId_scopeKey_problemId_languageId_unique_idx",
    def = "{'userId': 1, 'scopeKey': 1, 'problemId': 1, 'languageId': 1}",
    unique = true
)
public class CodeDraft {
    @Id
    private String id;
    private String userId;
    private ScopeType scopeType;
    private String scopeKey;
    private String contestId;
    private String problemId;
    private Integer languageId;
    private String code;
    private long revision;
    private Instant createdAt;
    private Instant updatedAt;

    public enum ScopeType {
        PRACTICE,
        CONTEST
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public ScopeType getScopeType() { return scopeType; }
    public void setScopeType(ScopeType scopeType) { this.scopeType = scopeType; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public String getContestId() { return contestId; }
    public void setContestId(String contestId) { this.contestId = contestId; }
    public String getProblemId() { return problemId; }
    public void setProblemId(String problemId) { this.problemId = problemId; }
    public Integer getLanguageId() { return languageId; }
    public void setLanguageId(Integer languageId) { this.languageId = languageId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
