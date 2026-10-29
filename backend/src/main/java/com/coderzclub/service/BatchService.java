package com.coderzclub.service;

import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchAssignment;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
        long memberCount = mongoTemplate.count(Query.query(Criteria.where("batchId").is(id)), BatchMember.class);
        response.put("memberCount", memberCount);
        response.put("assignmentCount", mongoTemplate.count(Query.query(Criteria.where("batchId").is(id)), BatchAssignment.class));
        response.put("activeMemberCount", activeMemberCount(id, memberCount));
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
            if (user.isDeleted()) {
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
        removeMembers(batchId, List.of(userId));
    }

    public int removeMembers(String batchId, List<String> userIds) {
        requireWritable(batchId);
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("userIds is required");
        }
        int removed = 0;
        for (String rawId : userIds) {
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            var result = mongoTemplate.remove(
                Query.query(Criteria.where("batchId").is(batchId).and("userId").is(rawId.trim())),
                BatchMember.class);
            if (result != null) {
                removed += (int) result.getDeletedCount();
            }
        }
        touch(batchId);
        return removed;
    }

    public int removeAssignments(String batchId, List<String> problemIds) {
        requireWritable(batchId);
        if (problemIds == null || problemIds.isEmpty()) {
            throw new IllegalArgumentException("problemIds is required");
        }
        int removed = 0;
        for (String rawId : problemIds) {
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            var result = mongoTemplate.remove(
                Query.query(Criteria.where("batchId").is(batchId).and("problemId").is(rawId.trim())),
                BatchAssignment.class);
            if (result != null) {
                removed += (int) result.getDeletedCount();
            }
        }
        touch(batchId);
        return removed;
    }

    public Map<String, Object> listMembers(String batchId, int page, int size, String search) {
        requireBatch(batchId);
        int safeSize = boundSize(size);
        int safePage = Math.max(0, page);
        List<BatchMember> memberships;
        long total;
        if (search == null || search.trim().isEmpty()) {
            Query membershipQuery = Query.query(Criteria.where("batchId").is(batchId));
            total = mongoTemplate.count(membershipQuery, BatchMember.class);
            membershipQuery.with(Sort.by("addedAt")).skip((long) safePage * safeSize).limit(safeSize);
            memberships = mongoTemplate.find(membershipQuery, BatchMember.class);
        } else {
            String escaped = Pattern.quote(search.trim());
            long memberCount = mongoTemplate.count(Query.query(Criteria.where("batchId").is(batchId)), BatchMember.class);
            if (memberCount > 2000) {
                Map<String, Object> limited = searchMembersAmongCandidates(batchId, escaped, safePage, safeSize);
                limited.put("searchLimited", true);
                return limited;
            }
            List<String> memberIds = mongoTemplate.find(Query.query(Criteria.where("batchId").is(batchId)), BatchMember.class)
                .stream().map(BatchMember::getUserId).toList();
            if (memberIds.isEmpty()) {
                return pageResponse("members", List.of(), safePage, safeSize, 0);
            }
            Query usersQuery = Query.query(new Criteria().andOperator(
                Criteria.where("_id").in(memberIds),
                new Criteria().orOperator(
                    Criteria.where("username").regex("^" + escaped, "i"),
                    Criteria.where("email").regex("^" + escaped, "i")
                )
            ));
            total = mongoTemplate.count(usersQuery, User.class);
            usersQuery.with(Sort.by("username")).skip((long) safePage * safeSize).limit(safeSize);
            List<String> pageIds = mongoTemplate.find(usersQuery, User.class).stream().map(User::getId).toList();
            if (pageIds.isEmpty()) {
                return pageResponse("members", List.of(), safePage, safeSize, total);
            }
            Map<String, BatchMember> byUser = new HashMap<>();
            for (BatchMember membership : mongoTemplate.find(
                Query.query(Criteria.where("batchId").is(batchId).and("userId").in(pageIds)),
                BatchMember.class)) {
                byUser.put(membership.getUserId(), membership);
            }
            memberships = new ArrayList<>();
            for (String userId : pageIds) {
                BatchMember membership = byUser.get(userId);
                if (membership != null) {
                    memberships.add(membership);
                }
            }
            return pageResponse("members", memberRows(batchId, memberships), safePage, safeSize, total);
        }
        return pageResponse("members", memberRows(batchId, memberships), safePage, safeSize, total);
    }

    private Map<String, Object> searchMembersAmongCandidates(String batchId, String escaped, int safePage, int safeSize) {
        Query usersQuery = new Query();
        usersQuery.addCriteria(new Criteria().orOperator(
            Criteria.where("username").regex("^" + escaped, "i"),
            Criteria.where("email").regex("^" + escaped, "i")
        ));
        usersQuery.limit(100);
        List<String> candidateIds = mongoTemplate.find(usersQuery, User.class).stream().map(User::getId).toList();
        if (candidateIds.isEmpty()) {
            return pageResponse("members", List.of(), safePage, safeSize, 0);
        }
        List<BatchMember> memberships = mongoTemplate.find(
            Query.query(Criteria.where("batchId").is(batchId).and("userId").in(candidateIds)).with(Sort.by("addedAt")),
            BatchMember.class);
        long total = memberships.size();
        int from = Math.min(safePage * safeSize, memberships.size());
        int to = Math.min(from + safeSize, memberships.size());
        return pageResponse("members", memberRows(batchId, new ArrayList<>(memberships.subList(from, to))), safePage, safeSize, total);
    }

    public Map<String, Object> addMembersResult(String batchId, List<String> userIds) {
        requireWritable(batchId);
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("userIds is required");
        }
        List<String> alreadyMemberIds = new ArrayList<>();
        List<String> fresh = new ArrayList<>();
        for (String rawId : userIds) {
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            String userId = rawId.trim();
            boolean exists = mongoTemplate.exists(
                Query.query(Criteria.where("batchId").is(batchId).and("userId").is(userId)),
                BatchMember.class);
            if (exists) {
                alreadyMemberIds.add(userId);
            } else {
                fresh.add(userId);
            }
        }
        List<BatchMember> members = fresh.isEmpty() ? List.of() : addMembers(batchId, fresh);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("members", members);
        response.put("alreadyMemberIds", alreadyMemberIds);
        return response;
    }

    public Map<String, Object> importIdentifiers(String batchId, List<String> identifiers) {
        requireWritable(batchId);
        if (identifiers == null || identifiers.isEmpty()) {
            throw new IllegalArgumentException("usernames is required");
        }
        if (identifiers.size() > 100) {
            throw new IllegalArgumentException("Import at most 100 students at a time");
        }
        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> alreadyMembers = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : identifiers) {
            if (raw == null) {
                continue;
            }
            String token = raw.trim();
            if (token.isEmpty() || !seen.add(token.toLowerCase(Locale.ROOT))) {
                continue;
            }
            User user = findImportUser(token);
            if (user == null || user.isDeleted()) {
                notFound.add(token);
                continue;
            }
            boolean exists = mongoTemplate.exists(
                Query.query(Criteria.where("batchId").is(batchId).and("userId").is(user.getId())),
                BatchMember.class);
            if (exists) {
                alreadyMembers.add(safeUser(user));
                continue;
            }
            addMembers(batchId, List.of(user.getId()));
            added.add(safeUser(user));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("added", added);
        response.put("alreadyMembers", alreadyMembers);
        response.put("notFound", notFound);
        return response;
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
        long memberCount = mongoTemplate.count(Query.query(Criteria.where("batchId").is(batchId)), BatchMember.class);
        boolean completionLimited = memberCount > 2000;
        Map<String, Integer> solvedCounts = completionLimited
            ? Map.of()
            : solvedByProblem(batchId, problemIds, problems, memberCount);
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
            row.put("memberCount", memberCount);
            row.put("solvedCount", completionLimited ? null : solvedCounts.getOrDefault(assignment.getProblemId(), 0));
            rows.add(row);
        }
        Map<String, Object> response = pageResponse("assignments", rows, safePage, safeSize, total);
        response.put("memberCount", memberCount);
        response.put("completionLimited", completionLimited);
        return response;
    }

    public Map<String, Object> searchUsers(String search, int page, int size) {
        return searchUsers(search, page, size, null);
    }

    public Map<String, Object> searchUsers(String search, int page, int size, String batchId) {
        int safeSize = boundSize(size);
        int safePage = Math.max(0, page);
        List<Criteria> filters = new ArrayList<>();
        filters.add(notDeleted());
        if (search != null && !search.trim().isEmpty()) {
            String escaped = Pattern.quote(search.trim());
            filters.add(new Criteria().orOperator(
                Criteria.where("username").regex("^" + escaped, "i"),
                Criteria.where("email").regex("^" + escaped, "i")
            ));
        }
        Query query = Query.query(new Criteria().andOperator(filters));
        long total = mongoTemplate.count(query, User.class);
        query.with(Sort.by("username")).skip((long) safePage * safeSize).limit(safeSize);
        query.fields().include("username").include("email").include("accountStatus").include("emailVerified");
        List<User> found = mongoTemplate.find(query, User.class);
        Set<String> memberIds = memberIds(batchId, found.stream().map(User::getId).toList());
        List<Map<String, Object>> users = new ArrayList<>();
        for (User user : found) {
            Map<String, Object> row = safeUser(user);
            if (batchId != null && !batchId.isBlank()) {
                row.put("alreadyMember", memberIds.contains(user.getId()));
            }
            users.add(row);
        }
        return pageResponse("users", users, safePage, safeSize, total);
    }

    public static final int ENROLLED_PROBLEM_PREVIEW = 5;

    /**
     * Batches the user belongs to as a student. One membership page, then one batch
     * query, one assignment query, and one problem query. Coordinator reports and
     * other students are not included.
     */
    public Map<String, Object> listEnrolledForUser(String userId, int page, int size) {
        int safeSize = boundSize(size);
        int safePage = Math.max(0, page);
        if (userId == null || userId.isBlank()) {
            return pageResponse("items", List.of(), safePage, safeSize, 0);
        }
        long total = mongoTemplate.count(Query.query(Criteria.where("userId").is(userId)), BatchMember.class);
        Query membershipQuery = Query.query(Criteria.where("userId").is(userId))
            .with(Sort.by(Sort.Direction.DESC, "addedAt"))
            .skip((long) safePage * safeSize)
            .limit(safeSize);
        List<BatchMember> memberships = mongoTemplate.find(membershipQuery, BatchMember.class);
        if (memberships.isEmpty()) {
            return pageResponse("items", List.of(), safePage, safeSize, total);
        }
        List<String> batchIds = memberships.stream().map(BatchMember::getBatchId).distinct().toList();
        Map<String, Batch> batches = new HashMap<>();
        for (Batch batch : mongoTemplate.find(Query.query(Criteria.where("_id").in(batchIds)), Batch.class)) {
            batches.put(batch.getId(), batch);
        }
        Query assignmentQuery = Query.query(Criteria.where("batchId").in(batchIds))
            .with(Sort.by(Sort.Direction.ASC, "assignedAt"));
        assignmentQuery.fields().include("batchId").include("problemId").include("assignedAt");
        Map<String, List<BatchAssignment>> assignmentsByBatch = new HashMap<>();
        for (BatchAssignment assignment : mongoTemplate.find(assignmentQuery, BatchAssignment.class)) {
            assignmentsByBatch.computeIfAbsent(assignment.getBatchId(), ignored -> new ArrayList<>()).add(assignment);
        }
        List<String> previewIds = new ArrayList<>();
        for (BatchMember membership : memberships) {
            List<BatchAssignment> assigned = assignmentsByBatch.getOrDefault(membership.getBatchId(), List.of());
            int preview = Math.min(ENROLLED_PROBLEM_PREVIEW, assigned.size());
            for (int i = 0; i < preview; i++) {
                previewIds.add(assigned.get(i).getProblemId());
            }
        }
        Map<String, Problem> problems = loadPracticeProblems(previewIds);
        List<Map<String, Object>> items = new ArrayList<>();
        for (BatchMember membership : memberships) {
            Batch batch = batches.get(membership.getBatchId());
            if (batch == null) {
                continue;
            }
            List<BatchAssignment> assigned = assignmentsByBatch.getOrDefault(batch.getId(), List.of());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("batchId", batch.getId());
            item.put("name", batch.getName());
            item.put("description", batch.getDescription());
            item.put("active", batch.isActive());
            item.put("memberSince", membership.getAddedAt());
            item.put("assignmentCount", assigned.size());
            item.put("problems", practiceRows(assigned, problems, ENROLLED_PROBLEM_PREVIEW));
            items.add(item);
        }
        return pageResponse("items", items, safePage, safeSize, total);
    }

    /**
     * Practice problems for one batch the caller belongs to. Missing membership is
     * a 404 so unrelated batch ids are not distinguishable from unknown ids.
     */
    public Map<String, Object> enrolledPractice(String userId, String batchId, int page, int size) {
        if (userId == null || userId.isBlank() || batchId == null || batchId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found");
        }
        BatchMember membership = mongoTemplate.findOne(
            Query.query(Criteria.where("batchId").is(batchId).and("userId").is(userId)),
            BatchMember.class);
        if (membership == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found");
        }
        Batch batch = mongoTemplate.findById(batchId, Batch.class);
        if (batch == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found");
        }
        int safeSize = boundSize(size);
        int safePage = Math.max(0, page);
        long total = mongoTemplate.count(Query.query(Criteria.where("batchId").is(batchId)), BatchAssignment.class);
        Query assignmentQuery = Query.query(Criteria.where("batchId").is(batchId))
            .with(Sort.by(Sort.Direction.ASC, "assignedAt"))
            .skip((long) safePage * safeSize)
            .limit(safeSize);
        assignmentQuery.fields().include("problemId").include("assignedAt");
        List<BatchAssignment> assignments = mongoTemplate.find(assignmentQuery, BatchAssignment.class);
        List<String> problemIds = assignments.stream().map(BatchAssignment::getProblemId).toList();
        Map<String, Problem> problems = loadPracticeProblems(problemIds);
        Map<String, Object> response = pageResponse("problems", practiceRows(assignments, problems, assignments.size()), safePage, safeSize, total);
        response.put("batchId", batch.getId());
        response.put("name", batch.getName());
        response.put("description", batch.getDescription());
        response.put("active", batch.isActive());
        response.put("memberSince", membership.getAddedAt());
        return response;
    }

    public static int boundSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Long activeMemberCount(String batchId, long memberCount) {
        if (memberCount == 0) {
            return 0L;
        }
        if (memberCount > 2000) {
            return null;
        }
        List<String> ids = mongoTemplate.find(Query.query(Criteria.where("batchId").is(batchId)), BatchMember.class)
            .stream().map(BatchMember::getUserId).toList();
        if (ids.isEmpty()) {
            return 0L;
        }
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where("_id").in(ids),
            new Criteria().orOperator(
                Criteria.where("accountStatus").exists(false),
                Criteria.where("accountStatus").is(null),
                Criteria.where("accountStatus").is("ACTIVE")
            )
        ));
        return mongoTemplate.count(query, User.class);
    }

    private List<Map<String, Object>> memberRows(String batchId, List<BatchMember> memberships) {
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<String> userIds = memberships.stream().map(BatchMember::getUserId).toList();
        Map<String, User> users = new HashMap<>();
        Query userQuery = Query.query(Criteria.where("_id").in(userIds));
        userQuery.fields().include("username").include("email").include("accountStatus").include("emailVerified");
        for (User user : mongoTemplate.find(userQuery, User.class)) {
            users.put(user.getId(), user);
        }
        List<BatchAssignment> assignments = mongoTemplate.find(
            Query.query(Criteria.where("batchId").is(batchId)), BatchAssignment.class);
        List<String> assignedIds = assignments.stream().map(BatchAssignment::getProblemId).toList();
        Map<String, Problem> problems = loadProblems(assignedIds);
        Map<String, Integer> solved = solvedByUser(userIds, assignedIds, problems);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BatchMember membership : memberships) {
            User user = users.get(membership.getUserId());
            Map<String, Object> row = user == null ? new LinkedHashMap<>() : safeUser(user);
            if (user == null) {
                row.put("id", membership.getUserId());
                row.put("username", null);
                row.put("email", null);
                row.put("accountStatus", null);
                row.put("emailVerified", false);
            }
            row.put("addedAt", membership.getAddedAt());
            row.put("assignedProblemCount", assignedIds.size());
            row.put("solvedCount", solved.getOrDefault(membership.getUserId(), 0));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Integer> solvedByUser(List<String> userIds, List<String> assignedProblemIds, Map<String, Problem> problems) {
        Map<String, Integer> solved = new HashMap<>();
        if (userIds.isEmpty() || assignedProblemIds.isEmpty()) {
            return solved;
        }
        Map<String, String> canonical = canonicalProblemIds(assignedProblemIds, problems);
        Query query = Query.query(Criteria.where("userId").in(userIds).and("problemId").in(canonical.keySet()));
        query.fields().include("userId").include("problemId").include("result").include("verdict");
        Map<String, Set<String>> solvedProblems = new HashMap<>();
        for (Submission submission : mongoTemplate.find(query, Submission.class)) {
            if (SubmissionAttemptClassifier.classify(submission.getResult(), submission.getVerdict())
                != SubmissionAttemptClassifier.Kind.ACCEPTED) {
                continue;
            }
            String problemId = canonical.get(submission.getProblemId());
            if (problemId == null) {
                continue;
            }
            solvedProblems.computeIfAbsent(submission.getUserId(), ignored -> new HashSet<>()).add(problemId);
        }
        for (Map.Entry<String, Set<String>> entry : solvedProblems.entrySet()) {
            solved.put(entry.getKey(), entry.getValue().size());
        }
        return solved;
    }

    private Map<String, Integer> solvedByProblem(
        String batchId, List<String> problemIds, Map<String, Problem> problems, long memberCount
    ) {
        Map<String, Integer> solved = new HashMap<>();
        if (memberCount == 0 || problemIds.isEmpty()) {
            return solved;
        }
        List<String> memberIds = mongoTemplate.find(Query.query(Criteria.where("batchId").is(batchId)), BatchMember.class)
            .stream().map(BatchMember::getUserId).toList();
        Map<String, String> canonical = canonicalProblemIds(problemIds, problems);
        Query query = Query.query(Criteria.where("userId").in(memberIds).and("problemId").in(canonical.keySet()));
        query.fields().include("userId").include("problemId").include("result").include("verdict");
        Map<String, Set<String>> usersByProblem = new HashMap<>();
        for (Submission submission : mongoTemplate.find(query, Submission.class)) {
            if (SubmissionAttemptClassifier.classify(submission.getResult(), submission.getVerdict())
                != SubmissionAttemptClassifier.Kind.ACCEPTED) {
                continue;
            }
            String problemId = canonical.get(submission.getProblemId());
            if (problemId == null) {
                continue;
            }
            usersByProblem.computeIfAbsent(problemId, ignored -> new HashSet<>()).add(submission.getUserId());
        }
        for (Map.Entry<String, Set<String>> entry : usersByProblem.entrySet()) {
            solved.put(entry.getKey(), entry.getValue().size());
        }
        return solved;
    }

    private static Map<String, String> canonicalProblemIds(List<String> assignedProblemIds, Map<String, Problem> problems) {
        Map<String, String> canonical = new HashMap<>();
        for (String problemId : assignedProblemIds) {
            canonical.put(problemId, problemId);
            Problem problem = problems.get(problemId);
            if (problem != null && problem.getNumericId() != null) {
                canonical.put(String.valueOf(problem.getNumericId()), problemId);
            }
        }
        return canonical;
    }

    private Set<String> memberIds(String batchId, List<String> userIds) {
        if (batchId == null || batchId.isBlank() || userIds.isEmpty()) {
            return Set.of();
        }
        return mongoTemplate.find(
                Query.query(Criteria.where("batchId").is(batchId).and("userId").in(userIds)),
                BatchMember.class)
            .stream().map(BatchMember::getUserId).collect(java.util.stream.Collectors.toSet());
    }

    private static Criteria notDeleted() {
        return new Criteria().orOperator(
            Criteria.where("accountStatus").exists(false),
            Criteria.where("accountStatus").ne("DELETED")
        );
    }

    private User findImportUser(String token) {
        String escaped = Pattern.quote(token);
        Query query = Query.query(new Criteria().andOperator(
            new Criteria().orOperator(
                Criteria.where("username").regex("^" + escaped + "$", "i"),
                Criteria.where("email").regex("^" + escaped + "$", "i")
            ),
            notDeleted()
        ));
        return mongoTemplate.findOne(query, User.class);
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

    private Map<String, Problem> loadPracticeProblems(List<String> ids) {
        Map<String, Problem> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        Query query = Query.query(Criteria.where("_id").in(ids));
        query.fields().include("title").include("numericId").include("difficulty");
        for (Problem problem : mongoTemplate.find(query, Problem.class)) {
            map.put(problem.getId(), problem);
        }
        return map;
    }

    private static List<Map<String, Object>> practiceRows(List<BatchAssignment> assignments, Map<String, Problem> problems, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int count = Math.min(limit, assignments.size());
        for (int i = 0; i < count; i++) {
            BatchAssignment assignment = assignments.get(i);
            Problem problem = problems.get(assignment.getProblemId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("problemId", assignment.getProblemId());
            row.put("numericId", problem == null ? null : problem.getNumericId());
            row.put("title", problem == null ? null : problem.getTitle());
            row.put("difficulty", problem == null ? null : problem.getDifficulty());
            rows.add(row);
        }
        return rows;
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
        map.put("accountStatus", user.getAccountStatus() == null ? "ACTIVE" : user.getAccountStatus());
        map.put("emailVerified", user.isEmailVerified());
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
