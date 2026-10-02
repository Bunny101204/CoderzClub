package com.coderzclub.service;

import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchAssignment;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Problem;
import com.coderzclub.model.User;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class BatchService {
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_ASSIGNMENTS = 80;

    private final MongoTemplate mongoTemplate;

    public BatchService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Batch create(String name, String description, String createdBy) {
        String trimmed = requireName(name);
        Batch batch = new Batch();
        batch.setName(trimmed);
        batch.setDescription(normalizeDescription(description));
        batch.setActive(true);
        Date now = new Date();
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        batch.setCreatedBy(createdBy);
        return mongoTemplate.save(batch);
    }

    public Batch update(String id, String name, String description, Boolean active) {
        Batch batch = requireBatch(id);
        if (name != null) {
            batch.setName(requireName(name));
        }
        if (description != null) {
            batch.setDescription(normalizeDescription(description));
        }
        if (active != null) {
            batch.setActive(active);
        }
        batch.setUpdatedAt(new Date());
        return mongoTemplate.save(batch);
    }

    public Batch deactivate(String id) {
        return update(id, null, null, false);
    }

    public Batch requireBatch(String id) {
        Batch batch = mongoTemplate.findById(id, Batch.class);
        if (batch == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found");
        }
        return batch;
    }

    public Batch requireWritable(String id) {
        Batch batch = requireBatch(id);
        if (!batch.isActive()) {
            throw new IllegalArgumentException("Archived batches cannot change membership or assignments");
        }
        return batch;
    }

    public Map<String, Object> list(int page, int size, String search, Boolean active) {
        int safeSize = boundSize(size);
        int safePage = Math.max(0, page);
        Query query = new Query();
        if (search != null && !search.trim().isEmpty()) {
            query.addCriteria(Criteria.where("name").regex("^" + Pattern.quote(search.trim()), "i"));
        }
        if (active != null) {
            query.addCriteria(Criteria.where("active").is(active));
        }
        long total = mongoTemplate.count(query, Batch.class);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"))
            .skip((long) safePage * safeSize)
            .limit(safeSize);
        List<Batch> batches = mongoTemplate.find(query, Batch.class);
        List<String> ids = batches.stream().map(Batch::getId).toList();
        Map<String, Long> members = counts("batch_members", ids);
        Map<String, Long> assignments = counts("batch_assignments", ids);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Batch batch : batches) {
            Map<String, Object> row = toBatchMap(batch);
            row.put("memberCount", members.getOrDefault(batch.getId(), 0L));
            row.put("assignmentCount", assignments.getOrDefault(batch.getId(), 0L));
            items.add(row);
        }
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("batches", items);
        response.put("currentPage", safePage);
        response.put("totalPages", totalPages);
        response.put("totalItems", total);
        return response;
    }

    public Map<String, Object> detail(String id) {
        Batch batch = requireBatch(id);
        Map<String, Object> response = toBatchMap(batch);
        response.put("memberCount", mongoTemplate.count(Query.query(Criteria.where("batchId").is(id)), BatchMember.class));
        response.put("assignmentCount", mongoTemplate.count(Query.query(Criteria.where("batchId").is(id)), BatchAssignment.class));
        return response;
    }

    public List<BatchMember> addMembers(String batchId, List<String> userIds) {
        requireWritable(batchId);
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("userIds is required");
        }
        List<BatchMember> added = new ArrayList<>();
        for (String rawId : userIds) {
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            String userId = rawId.trim();
            User user = mongoTemplate.findById(userId, User.class);
            if (user == null) {
                throw new IllegalArgumentException("User not found: " + userId);
            }
            BatchMember member = new BatchMember();
            member.setBatchId(batchId);
            member.setUserId(user.getId());
            member.setAddedAt(new Date());
            try {
                added.add(mongoTemplate.save(member));
            } catch (DuplicateKeyException duplicate) {
                BatchMember existing = mongoTemplate.findOne(
                    Query.query(Criteria.where("batchId").is(batchId).and("userId").is(user.getId())),
                    BatchMember.class);
                if (existing == null) {
                    throw duplicate;
                }
                added.add(existing);
            }
        }
        touch(batchId);
        return added;
    }

    public void removeMember(String batchId, String userId) {
        requireWritable(batchId);
        mongoTemplate.remove(
            Query.query(Criteria.where("batchId").is(batchId).and("userId").is(userId)),
            BatchMember.class);
        touch(batchId);
    }

    public Map<String, Object> listMembers(String batchId, int page, int size, String search) {
        requireBatch(batchId);
        int safeSize = boundSize(size);
        int safePage = Math.max(0, page);
        List<String> memberIds = mongoTemplate.find(
                Query.query(Criteria.where("batchId").is(batchId)).with(Sort.by("addedAt")),
                BatchMember.class)
            .stream().map(BatchMember::getUserId).toList();
        if (memberIds.isEmpty()) {
            return pageResponse("members", List.of(), safePage, safeSize, 0);
        }
        Query usersQuery = Query.query(Criteria.where("_id").in(memberIds));
        if (search != null && !search.trim().isEmpty()) {
            String escaped = Pattern.quote(search.trim());
            usersQuery.addCriteria(new Criteria().orOperator(
                Criteria.where("username").regex("^" + escaped, "i"),
                Criteria.where("email").regex("^" + escaped, "i")
            ));
        }
        long total = mongoTemplate.count(usersQuery, User.class);
        usersQuery.with(Sort.by("username")).skip((long) safePage * safeSize).limit(safeSize);
        usersQuery.fields().include("username").include("email");
        List<Map<String, Object>> members = new ArrayList<>();
        for (User user : mongoTemplate.find(usersQuery, User.class)) {
            members.add(safeUser(user));
        }
        return pageResponse("members", members, safePage, safeSize, total);
    }

    public List<BatchAssignment> addAssignments(String batchId, List<String> problemIds) {
        requireWritable(batchId);
        if (problemIds == null || problemIds.isEmpty()) {
            throw new IllegalArgumentException("problemIds is required");
        }
        long current = mongoTemplate.count(Query.query(Criteria.where("batchId").is(batchId)), BatchAssignment.class);
        List<BatchAssignment> added = new ArrayList<>();
        for (String rawId : problemIds) {
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            Problem problem = resolveProblem(rawId.trim());
            if (problem == null) {
                throw new IllegalArgumentException("Problem not found: " + rawId);
            }
            boolean exists = mongoTemplate.exists(
                Query.query(Criteria.where("batchId").is(batchId).and("problemId").is(problem.getId())),
                BatchAssignment.class);
            if (!exists && current + added.size() >= MAX_ASSIGNMENTS) {
                throw new IllegalArgumentException("A batch can have at most " + MAX_ASSIGNMENTS + " assigned problems");
            }
            BatchAssignment assignment = new BatchAssignment();
            assignment.setBatchId(batchId);
            assignment.setProblemId(problem.getId());
            assignment.setAssignedAt(new Date());
            try {
                added.add(mongoTemplate.save(assignment));
                if (!exists) {
                    current++;
                }
            } catch (DuplicateKeyException duplicate) {
                BatchAssignment existing = mongoTemplate.findOne(
                    Query.query(Criteria.where("batchId").is(batchId).and("problemId").is(problem.getId())),
                    BatchAssignment.class);
                if (existing == null) {
                    throw duplicate;
                }
                added.add(existing);
            }
        }
        touch(batchId);
        return added;
    }

    public void removeAssignment(String batchId, String problemId) {
        requireWritable(batchId);
        Problem problem = resolveProblem(problemId);
        String canonical = problem == null ? problemId : problem.getId();
        mongoTemplate.remove(
            Query.query(Criteria.where("batchId").is(batchId).and("problemId").is(canonical)),
            BatchAssignment.class);
        touch(batchId);
    }

    public Map<String, Object> listAssignments(String batchId, int page, int size) {
        requireBatch(batchId);
        int safeSize = boundSize(size);
        int safePage = Math.max(0, page);
        Query query = Query.query(Criteria.where("batchId").is(batchId));
        long total = mongoTemplate.count(query, BatchAssignment.class);
        query.with(Sort.by("assignedAt")).skip((long) safePage * safeSize).limit(safeSize);
        List<BatchAssignment> assignments = mongoTemplate.find(query, BatchAssignment.class);
        List<String> problemIds = assignments.stream().map(BatchAssignment::getProblemId).toList();
        Map<String, Problem> problems = loadProblems(problemIds);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BatchAssignment assignment : assignments) {
            Problem problem = problems.get(assignment.getProblemId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("problemId", assignment.getProblemId());
            row.put("numericId", problem == null ? null : problem.getNumericId());
            row.put("title", problem == null ? null : problem.getTitle());
            row.put("difficulty", problem == null ? null : problem.getDifficulty());
            row.put("tags", problem == null ? List.of() : problem.getTags());
            row.put("assignedAt", assignment.getAssignedAt());
            rows.add(row);
        }
        return pageResponse("assignments", rows, safePage, safeSize, total);
    }

    public Map<String, Object> searchUsers(String search, int page, int size) {
        int safeSize = boundSize(size);
        int safePage = Math.max(0, page);
        Query query = new Query();
        if (search != null && !search.trim().isEmpty()) {
            String escaped = Pattern.quote(search.trim());
            query.addCriteria(new Criteria().orOperator(
                Criteria.where("username").regex("^" + escaped, "i"),
                Criteria.where("email").regex("^" + escaped, "i")
            ));
        }
        long total = mongoTemplate.count(query, User.class);
        query.with(Sort.by("username")).skip((long) safePage * safeSize).limit(safeSize);
        query.fields().include("username").include("email");
        List<Map<String, Object>> users = new ArrayList<>();
        for (User user : mongoTemplate.find(query, User.class)) {
            users.add(safeUser(user));
        }
        return pageResponse("users", users, safePage, safeSize, total);
    }

    public static int boundSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Problem resolveProblem(String id) {
        Problem problem = mongoTemplate.findById(id, Problem.class);
        if (problem != null) {
            return problem;
        }
        try {
            return mongoTemplate.findOne(Query.query(Criteria.where("numericId").is(Integer.valueOf(id))), Problem.class);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, Problem> loadProblems(List<String> ids) {
        Map<String, Problem> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Problem problem : mongoTemplate.find(Query.query(Criteria.where("_id").in(ids)), Problem.class)) {
            map.put(problem.getId(), problem);
        }
        return map;
    }

    private Map<String, Long> counts(String collection, List<String> batchIds) {
        Map<String, Long> counts = new HashMap<>();
        if (batchIds.isEmpty()) {
            return counts;
        }
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("batchId").in(batchIds)),
            Aggregation.group("batchId").count().as("n")
        );
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, collection, Document.class);
        for (Document document : results.getMappedResults()) {
            counts.put(String.valueOf(document.get("_id")), ((Number) document.get("n")).longValue());
        }
        return counts;
    }

    private void touch(String batchId) {
        Batch batch = mongoTemplate.findById(batchId, Batch.class);
        if (batch != null) {
            batch.setUpdatedAt(new Date());
            mongoTemplate.save(batch);
        }
    }

    private static String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 120) {
            throw new IllegalArgumentException("name must be 120 characters or fewer");
        }
        return trimmed;
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > 2000) {
            throw new IllegalArgumentException("description must be 2000 characters or fewer");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Map<String, Object> toBatchMap(Batch batch) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", batch.getId());
        map.put("name", batch.getName());
        map.put("description", batch.getDescription());
        map.put("active", batch.isActive());
        map.put("createdAt", batch.getCreatedAt());
        map.put("updatedAt", batch.getUpdatedAt());
        map.put("createdBy", batch.getCreatedBy());
        return map;
    }

    private static Map<String, Object> safeUser(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        return map;
    }

    private static Map<String, Object> pageResponse(String key, List<?> items, int page, int size, long total) {
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(key, items);
        response.put("currentPage", page);
        response.put("totalPages", totalPages);
        response.put("totalItems", total);
        return response;
    }
}
