package com.coderzclub.service;

import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemBundle;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Resolves problem ids and numeric aliases with one _id query and, only when
 * some aliases remain, one numericId query. Request order is preserved.
 */
@Service
public class ProblemIdentityResolver {
    private final MongoTemplate mongoTemplate;

    public ProblemIdentityResolver(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void applyCanonicalProblemIds(List<ProblemBundle> bundles) {
        if (bundles == null || bundles.isEmpty()) {
            return;
        }
        List<String> rawIds = new ArrayList<>();
        for (ProblemBundle bundle : bundles) {
            if (bundle != null && bundle.getProblemIds() != null) {
                rawIds.addAll(bundle.getProblemIds());
            }
        }
        Map<String, String> canonicalByRaw = canonicalByRawId(rawIds);
        for (ProblemBundle bundle : bundles) {
            if (bundle == null) {
                continue;
            }
            List<String> canonical = new ArrayList<>();
            if (bundle.getProblemIds() != null) {
                for (String rawId : bundle.getProblemIds()) {
                    String canonicalId = canonicalByRaw.get(rawId);
                    if (canonicalId != null && !canonical.contains(canonicalId)) {
                        canonical.add(canonicalId);
                    }
                }
            }
            bundle.setProblemIds(canonical);
            bundle.setTotalProblems(canonical.size());
        }
    }

    public List<Problem> resolveForList(List<String> rawIds) {
        return resolve(rawIds, false);
    }

    private Map<String, String> canonicalByRawId(List<String> rawIds) {
        Map<String, String> byRequestedRaw = new LinkedHashMap<>();
        Map<String, Problem> byId = new LinkedHashMap<>();
        Map<Integer, Problem> byNumeric = new LinkedHashMap<>();
        for (Problem problem : resolve(rawIds, true)) {
            if (problem.getId() != null) {
                byId.put(problem.getId(), problem);
            }
            if (problem.getNumericId() != null) {
                byNumeric.putIfAbsent(problem.getNumericId(), problem);
            }
        }
        if (rawIds == null) {
            return byRequestedRaw;
        }
        for (String rawId : rawIds) {
            if (rawId == null || rawId.isBlank() || byRequestedRaw.containsKey(rawId)) {
                continue;
            }
            Problem problem = byId.get(rawId);
            if (problem == null) {
                Integer numericId = numericId(rawId);
                if (numericId != null) {
                    problem = byNumeric.get(numericId);
                }
            }
            if (problem != null && problem.getId() != null) {
                byRequestedRaw.put(rawId, problem.getId());
            }
        }
        return byRequestedRaw;
    }

    private List<Problem> resolve(List<String> rawIds, boolean identityOnly) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            if (rawId != null && !rawId.isBlank()) {
                distinct.add(rawId);
            }
        }
        if (distinct.isEmpty()) {
            return List.of();
        }
        Map<String, Problem> byId = new LinkedHashMap<>();
        Query byObjectId = Query.query(Criteria.where("_id").in(distinct));
        project(byObjectId, identityOnly);
        for (Problem problem : mongoTemplate.find(byObjectId, Problem.class)) {
            if (problem.getId() != null) {
                byId.put(problem.getId(), problem);
            }
        }
        List<Integer> unresolvedNumeric = new ArrayList<>();
        for (String rawId : distinct) {
            if (byId.containsKey(rawId)) {
                continue;
            }
            Integer numericId = numericId(rawId);
            if (numericId != null) {
                unresolvedNumeric.add(numericId);
            }
        }
        Map<Integer, Problem> byNumeric = new LinkedHashMap<>();
        if (!unresolvedNumeric.isEmpty()) {
            Query byNumericId = Query.query(Criteria.where("numericId").in(unresolvedNumeric));
            project(byNumericId, identityOnly);
            for (Problem problem : mongoTemplate.find(byNumericId, Problem.class)) {
                if (problem.getNumericId() != null) {
                    byNumeric.putIfAbsent(problem.getNumericId(), problem);
                }
            }
        }
        List<Problem> ordered = new ArrayList<>();
        for (String rawId : rawIds) {
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            Problem problem = byId.get(rawId);
            if (problem == null) {
                Integer numericId = numericId(rawId);
                if (numericId != null) {
                    problem = byNumeric.get(numericId);
                }
            }
            if (problem != null) {
                ordered.add(problem);
            }
        }
        return ordered;
    }

    private static void project(Query query, boolean identityOnly) {
        if (identityOnly) {
            query.fields().include("numericId");
            return;
        }
        query.fields().exclude("hiddenTestCases");
    }

    private static Integer numericId(String rawId) {
        try {
            return Integer.valueOf(rawId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
