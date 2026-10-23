package com.coderzclub.service;

import com.coderzclub.dto.PublicProblemDetail;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.User;
import com.coderzclub.repository.ProblemRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Public practice detail with a Redis read-through cache.
 * A cached body is returned only after Mongo says the problem is currently PUBLIC.
 * CONTEST_ONLY problems are never written to the public cache.
 */
@Service
public class ProblemPublicDetailService {
    private final MongoTemplate mongoTemplate;
    private final ProblemRepository problemRepository;
    private final ProblemAccessService problemAccessService;
    private final ProblemReadCache cache;
    private final ConcurrentHashMap<String, Object> detailLoads = new ConcurrentHashMap<>();

    public ProblemPublicDetailService(
        MongoTemplate mongoTemplate,
        ProblemRepository problemRepository,
        ProblemAccessService problemAccessService,
        ProblemReadCache cache
    ) {
        this.mongoTemplate = mongoTemplate;
        this.problemRepository = problemRepository;
        this.problemAccessService = problemAccessService;
        this.cache = cache;
    }

    public Object read(User user, String rawId) {
        Problem located = findProbe(rawId);
        if (located == null || located.getId() == null) {
            return null;
        }
        String canonicalId = located.getId();
        if (!ProblemAvailability.isPublic(located)) {
            cache.evictDetail(canonicalId);
            return restrictedView(user, canonicalId);
        }
        ProblemReadCache.DetailLookup lookedUp = cache.lookupDetail(canonicalId);
        if ("fallback".equals(lookedUp.outcome())) {
            cache.noteDetail("fallback");
            return loadPublic(user, canonicalId, false);
        }
        if (lookedUp.detail() != null && stillPublic(canonicalId)) {
            cache.noteDetail("hit");
            return lookedUp.detail();
        }
        if (lookedUp.detail() != null) {
            cache.evictDetail(canonicalId);
            Problem confirm = findProbeById(canonicalId);
            if (confirm == null || !ProblemAvailability.isPublic(confirm)) {
                cache.noteDetail("miss");
                if (confirm == null) {
                    return null;
                }
                return restrictedView(user, canonicalId);
            }
        }
        return loadPublic(user, canonicalId, true);
    }

    private boolean stillPublic(String canonicalId) {
        Problem confirm = findProbeById(canonicalId);
        return confirm != null && ProblemAvailability.isPublic(confirm);
    }

    private Object loadPublic(User user, String canonicalId, boolean recordMiss) {
        Object lock = detailLoads.computeIfAbsent(canonicalId, ignored -> new Object());
        synchronized (lock) {
            try {
                Problem confirm = findProbeById(canonicalId);
                if (confirm == null) {
                    return null;
                }
                if (!ProblemAvailability.isPublic(confirm)) {
                    cache.evictDetail(canonicalId);
                    return restrictedView(user, canonicalId);
                }
                ProblemReadCache.DetailLookup again = cache.lookupDetail(canonicalId);
                if (again.detail() != null) {
                    cache.noteDetail("hit");
                    return again.detail();
                }
                if (recordMiss) {
                    cache.noteDetail("miss");
                }
                Problem full = problemRepository.findById(canonicalId).orElse(null);
                if (full == null) {
                    return null;
                }
                if (!ProblemAvailability.isPublic(full)) {
                    cache.evictDetail(canonicalId);
                    return visibleEntity(user, full);
                }
                PublicProblemDetail detail = PublicProblemDetail.fromPublicProblem(full);
                cache.writeDetail(detail);
                return detail;
            } finally {
                detailLoads.remove(canonicalId, lock);
            }
        }
    }

    private Object restrictedView(User user, String canonicalId) {
        Problem full = problemRepository.findById(canonicalId).orElse(null);
        return visibleEntity(user, full);
    }

    private Object visibleEntity(User user, Problem problem) {
        if (problem == null || !problemAccessService.canView(user, problem)) {
            return null;
        }
        problem.setHiddenTestCases(null);
        return problem;
    }

    private Problem findProbe(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        Problem byId = findProbeById(rawId);
        if (byId != null) {
            return byId;
        }
        Integer numericId = numericId(rawId);
        if (numericId == null) {
            return null;
        }
        Query query = Query.query(Criteria.where("numericId").is(numericId));
        query.fields().include("availability").include("numericId");
        return mongoTemplate.findOne(query, Problem.class);
    }

    private Problem findProbeById(String id) {
        Query query = Query.query(Criteria.where("_id").is(id));
        query.fields().include("availability").include("numericId");
        return mongoTemplate.findOne(query, Problem.class);
    }

    private static Integer numericId(String rawId) {
        try {
            return Integer.valueOf(rawId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
