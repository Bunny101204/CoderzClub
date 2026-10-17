package com.coderzclub.service;

import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchCoordinator;
import com.coderzclub.model.User;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BatchCoordinatorService {
    public static final int MAX_INSTITUTION = 120;

    private final MongoTemplate mongoTemplate;
    private final BatchService batchService;

    public BatchCoordinatorService(MongoTemplate mongoTemplate, BatchService batchService) {
        this.mongoTemplate = mongoTemplate;
        this.batchService = batchService;
    }

    public List<Map<String, Object>> list(String batchId) {
        batchService.requireBatch(batchId);
        List<BatchCoordinator> rows = mongoTemplate.find(
            Query.query(Criteria.where("batchId").is(batchId)).with(Sort.by("addedAt")),
            BatchCoordinator.class);
        Map<String, User> users = loadUsers(rows.stream().map(BatchCoordinator::getUserId).toList());
        List<Map<String, Object>> response = new ArrayList<>();
        for (BatchCoordinator row : rows) {
            response.add(toMap(row, users.get(row.getUserId())));
        }
        return response;
    }

    public Map<String, Object> grant(String batchId, String userId, String institution, String addedBy) {
        batchService.requireBatch(batchId);
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        User user = mongoTemplate.findById(userId.trim(), User.class);
        if (user == null || user.isDeleted()) {
            throw new IllegalArgumentException("User not found");
        }
        BatchCoordinator existing = mongoTemplate.findOne(
            Query.query(Criteria.where("batchId").is(batchId).and("userId").is(user.getId())),
            BatchCoordinator.class);
        if (existing != null) {
            return toMap(existing, user);
        }
        BatchCoordinator row = new BatchCoordinator();
        row.setBatchId(batchId);
        row.setUserId(user.getId());
        row.setInstitution(normalizeInstitution(institution));
        row.setAddedAt(new Date());
        row.setAddedBy(addedBy);
        try {
            row = mongoTemplate.save(row);
        } catch (DuplicateKeyException duplicate) {
            row = mongoTemplate.findOne(
                Query.query(Criteria.where("batchId").is(batchId).and("userId").is(user.getId())),
                BatchCoordinator.class);
            if (row == null) {
                throw duplicate;
            }
        }
        return toMap(row, user);
    }

    public void revoke(String batchId, String userId) {
        batchService.requireBatch(batchId);
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        mongoTemplate.remove(
            Query.query(Criteria.where("batchId").is(batchId).and("userId").is(userId.trim())),
            BatchCoordinator.class);
    }

    public List<Map<String, Object>> listForUser(String userId) {
        List<BatchCoordinator> rows = mongoTemplate.find(
            Query.query(Criteria.where("userId").is(userId)).with(Sort.by(Sort.Direction.DESC, "addedAt")),
            BatchCoordinator.class);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> batchIds = rows.stream().map(BatchCoordinator::getBatchId).toList();
        Map<String, Batch> batches = new HashMap<>();
        for (Batch batch : mongoTemplate.find(Query.query(Criteria.where("_id").in(batchIds)), Batch.class)) {
            batches.put(batch.getId(), batch);
        }
        List<Map<String, Object>> response = new ArrayList<>();
        for (BatchCoordinator row : rows) {
            Batch batch = batches.get(row.getBatchId());
            if (batch == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("batchId", batch.getId());
            item.put("name", batch.getName());
            item.put("description", batch.getDescription());
            item.put("active", batch.isActive());
            item.put("institution", row.getInstitution());
            item.put("addedAt", row.getAddedAt());
            response.add(item);
        }
        return response;
    }

    private Map<String, User> loadUsers(List<String> ids) {
        Map<String, User> users = new HashMap<>();
        if (ids.isEmpty()) {
            return users;
        }
        Query query = Query.query(Criteria.where("_id").in(ids));
        query.fields().include("username").include("email").include("accountStatus");
        for (User user : mongoTemplate.find(query, User.class)) {
            users.put(user.getId(), user);
        }
        return users;
    }

    private static String normalizeInstitution(String institution) {
        if (institution == null) {
            return null;
        }
        String trimmed = institution.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_INSTITUTION) {
            throw new IllegalArgumentException("institution must be 120 characters or fewer");
        }
        return trimmed;
    }

    private static Map<String, Object> toMap(BatchCoordinator row, User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", row.getId());
        map.put("batchId", row.getBatchId());
        map.put("userId", row.getUserId());
        map.put("username", user == null ? null : user.getUsername());
        map.put("email", user == null ? null : user.getEmail());
        map.put("institution", row.getInstitution());
        map.put("addedAt", row.getAddedAt());
        return map;
    }
}
