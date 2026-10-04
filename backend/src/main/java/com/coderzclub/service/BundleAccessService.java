package com.coderzclub.service;

import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.BundleAccessGrant;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemBundle;
import com.coderzclub.model.User;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BundleAccessService {
    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String VISIBILITY_RESTRICTED = "RESTRICTED";

    private final MongoTemplate mongoTemplate;

    public BundleAccessService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public static String visibilityOf(ProblemBundle bundle) {
        if (bundle == null || bundle.getVisibility() == null || bundle.getVisibility().isBlank()) {
            return VISIBILITY_PUBLIC;
        }
        return bundle.getVisibility().trim().toUpperCase();
    }

    public static boolean isPublic(ProblemBundle bundle) {
        return VISIBILITY_PUBLIC.equals(visibilityOf(bundle));
    }

    public static boolean isAdmin(User user) {
        return user != null && user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    public boolean canAccess(User user, ProblemBundle bundle) {
        if (bundle == null) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        if (!bundle.isActive()) {
            return false;
        }
        if (isPublic(bundle)) {
            return true;
        }
        if (user == null || user.isDeleted() || user.getId() == null) {
            return false;
        }
        return grantedRestrictedBundleIds(user.getId()).contains(bundle.getId());
    }

    public ProblemBundle requireAccessibleBundle(User user, String bundleId) {
        ProblemBundle bundle = mongoTemplate.findById(bundleId, ProblemBundle.class);
        if (bundle == null || !canAccess(user, bundle)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bundle not found");
        }
        return bundle;
    }

    public List<ProblemBundle> listAccessibleActiveBundles(User user) {
        Set<String> grantedIds = Set.of();
        if (user != null && !user.isDeleted() && user.getId() != null) {
            grantedIds = grantedRestrictedBundleIds(user.getId());
        }
        Criteria publicOrMissing = new Criteria().orOperator(
            Criteria.where("visibility").is(null),
            Criteria.where("visibility").exists(false),
            Criteria.where("visibility").is(""),
            Criteria.where("visibility").is(VISIBILITY_PUBLIC)
        );
        Criteria access = grantedIds.isEmpty()
            ? publicOrMissing
            : new Criteria().orOperator(publicOrMissing, Criteria.where("_id").in(grantedIds));
        Query query = new Query(new Criteria().andOperator(
            Criteria.where("isActive").is(true),
            access
        ));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(query, ProblemBundle.class);
    }

    public Set<String> grantedRestrictedBundleIds(String userId) {
        List<BatchMember> memberships = mongoTemplate.find(
            Query.query(Criteria.where("userId").is(userId)),
            BatchMember.class
        );
        List<String> batchIds = memberships.stream().map(BatchMember::getBatchId).filter(id -> id != null && !id.isBlank()).toList();
        List<String> activeBatchIds = List.of();
        if (!batchIds.isEmpty()) {
            List<Batch> batches = mongoTemplate.find(
                Query.query(Criteria.where("_id").in(batchIds).and("active").is(true)),
                Batch.class
            );
            activeBatchIds = batches.stream().map(Batch::getId).toList();
        }
        List<Criteria> grantOr = new ArrayList<>();
        grantOr.add(Criteria.where("subjectType").is(BundleAccessGrant.SUBJECT_USER).and("subjectId").is(userId));
        if (!activeBatchIds.isEmpty()) {
            grantOr.add(Criteria.where("subjectType").is(BundleAccessGrant.SUBJECT_BATCH).and("subjectId").in(activeBatchIds));
        }
        List<BundleAccessGrant> grants = mongoTemplate.find(
            Query.query(new Criteria().orOperator(grantOr.toArray(Criteria[]::new))),
            BundleAccessGrant.class
        );
        Set<String> ids = new HashSet<>();
        for (BundleAccessGrant grant : grants) {
            if (grant.getBundleId() != null) {
                ids.add(grant.getBundleId());
            }
        }
        return ids;
    }

    public ProblemBundle setVisibility(String bundleId, String visibility) {
        ProblemBundle bundle = requireBundle(bundleId);
        String next = normalizeVisibility(visibility);
        bundle.setVisibility(next);
        bundle.setUpdatedAt(new Date());
        return mongoTemplate.save(bundle);
    }

    public BundleAccessGrant addGrant(String bundleId, String subjectType, String subjectId, String createdBy) {
        requireBundle(bundleId);
        String type = normalizeSubjectType(subjectType);
        String sid = requireSubjectId(subjectId);
        if (BundleAccessGrant.SUBJECT_USER.equals(type)) {
            User user = mongoTemplate.findById(sid, User.class);
            if (user == null || user.isDeleted()) {
                throw new IllegalArgumentException("User not found");
            }
        } else {
            Batch batch = mongoTemplate.findById(sid, Batch.class);
            if (batch == null) {
                throw new IllegalArgumentException("Batch not found");
            }
        }
        Query existingQuery = Query.query(Criteria.where("bundleId").is(bundleId)
            .and("subjectType").is(type)
            .and("subjectId").is(sid));
        BundleAccessGrant existing = mongoTemplate.findOne(existingQuery, BundleAccessGrant.class);
        if (existing != null) {
            return existing;
        }
        BundleAccessGrant grant = new BundleAccessGrant();
        grant.setBundleId(bundleId);
        grant.setSubjectType(type);
        grant.setSubjectId(sid);
        grant.setCreatedAt(new Date());
        grant.setCreatedBy(createdBy);
        try {
            return mongoTemplate.save(grant);
        } catch (DuplicateKeyException ignored) {
            BundleAccessGrant raced = mongoTemplate.findOne(existingQuery, BundleAccessGrant.class);
            if (raced != null) {
                return raced;
            }
            throw ignored;
        }
    }

    public void removeGrant(String bundleId, String grantId) {
        requireBundle(bundleId);
        Query query = Query.query(Criteria.where("_id").is(grantId).and("bundleId").is(bundleId));
        mongoTemplate.remove(query, BundleAccessGrant.class);
    }

    public void removeGrantBySubject(String bundleId, String subjectType, String subjectId) {
        requireBundle(bundleId);
        mongoTemplate.remove(
            Query.query(Criteria.where("bundleId").is(bundleId)
                .and("subjectType").is(normalizeSubjectType(subjectType))
                .and("subjectId").is(requireSubjectId(subjectId))),
            BundleAccessGrant.class
        );
    }

    public Map<String, Object> listGrants(String bundleId) {
        ProblemBundle bundle = requireBundle(bundleId);
        List<BundleAccessGrant> grants = mongoTemplate.find(
            Query.query(Criteria.where("bundleId").is(bundleId)).with(Sort.by(Sort.Direction.DESC, "createdAt")),
            BundleAccessGrant.class
        );
        List<String> userIds = grants.stream()
            .filter(g -> BundleAccessGrant.SUBJECT_USER.equals(g.getSubjectType()))
            .map(BundleAccessGrant::getSubjectId)
            .toList();
        List<String> batchIds = grants.stream()
            .filter(g -> BundleAccessGrant.SUBJECT_BATCH.equals(g.getSubjectType()))
            .map(BundleAccessGrant::getSubjectId)
            .toList();
        Map<String, User> users = loadUsers(userIds);
        Map<String, Batch> batches = loadBatches(batchIds);

        List<Map<String, Object>> userRows = new ArrayList<>();
        List<Map<String, Object>> batchRows = new ArrayList<>();
        for (BundleAccessGrant grant : grants) {
            if (BundleAccessGrant.SUBJECT_USER.equals(grant.getSubjectType())) {
                User user = users.get(grant.getSubjectId());
                if (user == null || user.isDeleted()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("grantId", grant.getId());
                row.put("userId", user.getId());
                row.put("username", user.getUsername());
                row.put("email", user.getEmail());
                userRows.add(row);
            } else if (BundleAccessGrant.SUBJECT_BATCH.equals(grant.getSubjectType())) {
                Batch batch = batches.get(grant.getSubjectId());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("grantId", grant.getId());
                row.put("batchId", grant.getSubjectId());
                row.put("name", batch == null ? "Unknown batch" : batch.getName());
                row.put("active", batch != null && batch.isActive());
                batchRows.add(row);
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("bundleId", bundle.getId());
        response.put("visibility", visibilityOf(bundle));
        response.put("users", userRows);
        response.put("batches", batchRows);
        return response;
    }

    public List<Problem> problemsForAccessibleBundle(User user, String bundleId) {
        ProblemBundle bundle = requireAccessibleBundle(user, bundleId);
        List<String> ids = bundle.getProblemIds() == null ? List.of() : bundle.getProblemIds();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Problem> found = mongoTemplate.find(Query.query(Criteria.where("_id").in(ids)), Problem.class);
        Map<String, Problem> byId = found.stream().collect(Collectors.toMap(Problem::getId, p -> p, (a, b) -> a));
        List<Problem> ordered = new ArrayList<>();
        for (String id : ids) {
            Problem problem = byId.get(id);
            if (problem != null) {
                ordered.add(problem);
            }
        }
        return ordered;
    }

    private ProblemBundle requireBundle(String bundleId) {
        if (bundleId == null || bundleId.isBlank()) {
            throw new IllegalArgumentException("Bundle not found");
        }
        ProblemBundle bundle = mongoTemplate.findById(bundleId, ProblemBundle.class);
        if (bundle == null) {
            throw new IllegalArgumentException("Bundle not found");
        }
        return bundle;
    }

    private static String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return VISIBILITY_PUBLIC;
        }
        String next = visibility.trim().toUpperCase();
        if (!VISIBILITY_PUBLIC.equals(next) && !VISIBILITY_RESTRICTED.equals(next)) {
            throw new IllegalArgumentException("visibility must be PUBLIC or RESTRICTED");
        }
        return next;
    }

    private static String normalizeSubjectType(String subjectType) {
        if (subjectType == null) {
            throw new IllegalArgumentException("subjectType is required");
        }
        String type = subjectType.trim().toUpperCase();
        if (!BundleAccessGrant.SUBJECT_USER.equals(type) && !BundleAccessGrant.SUBJECT_BATCH.equals(type)) {
            throw new IllegalArgumentException("subjectType must be USER or BATCH");
        }
        return type;
    }

    private static String requireSubjectId(String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId is required");
        }
        return subjectId.trim();
    }

    private Map<String, User> loadUsers(List<String> ids) {
        Map<String, User> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (User user : mongoTemplate.find(Query.query(Criteria.where("_id").in(ids)), User.class)) {
            map.put(user.getId(), user);
        }
        return map;
    }

    private Map<String, Batch> loadBatches(List<String> ids) {
        Map<String, Batch> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Batch batch : mongoTemplate.find(Query.query(Criteria.where("_id").in(ids)), Batch.class)) {
            map.put(batch.getId(), batch);
        }
        return map;
    }
}
