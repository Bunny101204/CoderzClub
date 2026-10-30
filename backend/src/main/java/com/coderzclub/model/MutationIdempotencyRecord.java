package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * One client attempt. A completed key is replayed until the record expires.
 * An open record without a saved job only blocks a second accept for a short
 * lease. A saved job is linked back onto the record so a failed update cannot
 * hold the subject until the retention TTL.
 */
@Document(collection = "mutation_idempotency")
@CompoundIndexes({
    @CompoundIndex(name = "idempotency_operation_unique", def = "{'userId': 1, 'scope': 1, 'operationKey': 1}", unique = true),
    @CompoundIndex(
        name = "idempotency_open_subject_unique",
        def = "{'userId': 1, 'scope': 1, 'subjectId': 1}",
        unique = true,
        partialFilter = "{'open': true}"
    )
})
public class MutationIdempotencyRecord {
    @Id
    private String id;
    private String userId;
    private String scope;
    private String operationKey;
    private String subjectId;
    private String resourceId;
    private boolean open;
    private Date createdAt = new Date();
    /** TTL is created by MutationIdempotencyService.ensureIndexes as mutation_idempotency_ttl. */
    private Date expiresAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getOperationKey() { return operationKey; }
    public void setOperationKey(String operationKey) { this.operationKey = operationKey; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }
}
