package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "batch_members")
@CompoundIndexes({
    @CompoundIndex(name = "batchId_userId_unique_idx", def = "{'batchId': 1, 'userId': 1}", unique = true),
    @CompoundIndex(name = "userId_batchId_idx", def = "{'userId': 1, 'batchId': 1}")
})
public class BatchMember {
    @Id
    private String id;
    private String batchId;
    private String userId;
    private Date addedAt = new Date();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Date getAddedAt() { return addedAt; }
    public void setAddedAt(Date addedAt) { this.addedAt = addedAt; }
}
