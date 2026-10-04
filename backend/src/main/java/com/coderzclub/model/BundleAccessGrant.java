package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "bundle_access_grants")
@CompoundIndexes({
    @CompoundIndex(name = "bundle_subject_unique_idx", def = "{'bundleId': 1, 'subjectType': 1, 'subjectId': 1}", unique = true),
    @CompoundIndex(name = "subject_bundle_idx", def = "{'subjectType': 1, 'subjectId': 1, 'bundleId': 1}")
})
public class BundleAccessGrant {
    public static final String SUBJECT_USER = "USER";
    public static final String SUBJECT_BATCH = "BATCH";

    @Id
    private String id;
    private String bundleId;
    private String subjectType;
    private String subjectId;
    private Date createdAt = new Date();
    private String createdBy;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBundleId() { return bundleId; }
    public void setBundleId(String bundleId) { this.bundleId = bundleId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
