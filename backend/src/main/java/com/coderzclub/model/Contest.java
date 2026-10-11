package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "contests")
@CompoundIndexes({
    @CompoundIndex(name = "createdAt_desc_idx", def = "{'createdAt': -1}"),
    @CompoundIndex(name = "published_audienceType_batchIds_startAt_idx", def = "{'published': 1, 'audienceType': 1, 'batchIds': 1, 'startAt': 1}"),
    // Equality on published + problemIds (one array). Do not add batchIds here:
    // MongoDB cannot compound two multikey array fields.
    @CompoundIndex(name = "published_problemIds_idx", def = "{'published': 1, 'problemIds': 1}")
})
public class Contest {
    @Id
    private String id;
    private String title;
    private String description;
    private Instant startAt;
    private Instant endAt;
    private boolean published;
    private ContestAudienceType audienceType;
    private List<String> batchIds = new ArrayList<>();
    private List<String> problemIds = new ArrayList<>();
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public ContestAudienceType getAudienceType() { return audienceType; }
    public void setAudienceType(ContestAudienceType audienceType) { this.audienceType = audienceType; }
    public List<String> getBatchIds() { return batchIds; }
    public void setBatchIds(List<String> batchIds) { this.batchIds = batchIds; }
    public List<String> getProblemIds() { return problemIds; }
    public void setProblemIds(List<String> problemIds) { this.problemIds = problemIds; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
