package com.coderzclub.service;

import com.coderzclub.dto.PublicProblemDetail;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Shared read cache for public practice problems.
 *
 * Detail key: {@code coderzclub:problem:public:v1:&lt;canonicalProblemId&gt;}
 * List version: {@code coderzclub:problem:list:version}
 * List key: {@code coderzclub:problem:list:v&lt;version&gt;:&lt;practice|admin&gt;:&lt;page&gt;:&lt;size&gt;:&lt;difficulty|all&gt;}
 *
 * Redis is an optimization. Get, set, and version failures fall open to Mongo.
 * Contest snapshots and code drafts are never stored here.
 * Detail misses on one JVM are coalesced with a local lock; there is no
 * distributed lock. List misses are not coalesced.
 */
@Service
public class ProblemReadCache {
    private static final Logger logger = LoggerFactory.getLogger(ProblemReadCache.class);
    public static final String DETAIL_PREFIX = "coderzclub:problem:public:v1:";
    public static final String LIST_VERSION_KEY = "coderzclub:problem:list:version";
    public static final String LIST_KEY_PREFIX = "coderzclub:problem:list:v";
    static final int MAX_CACHED_PAGE = 4;
    private static final Set<Integer> CACHED_PAGE_SIZES = Set.of(10, 20);
    private static final Set<String> CACHED_DIFFICULTIES = Set.of(
        "EASY", "MEDIUM", "HARD", "BASIC", "INTERMEDIATE", "ADVANCED", "SDE", "EXPERT");

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MongoTemplate mongoTemplate;
    private final OperationalMetrics metrics;
    private final long detailTtlSeconds;
    private final long listTtlSeconds;

    public ProblemReadCache(
        StringRedisTemplate redis,
        ObjectMapper objectMapper,
        MongoTemplate mongoTemplate,
        OperationalMetrics metrics,
        @Value("${problem.cache.detail-ttl-seconds:600}") long detailTtlSeconds,
        @Value("${problem.cache.list-ttl-seconds:600}") long listTtlSeconds
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.mongoTemplate = mongoTemplate;
        this.metrics = metrics;
        this.detailTtlSeconds = Math.max(1, detailTtlSeconds);
        this.listTtlSeconds = Math.max(1, listTtlSeconds);
    }

    public DetailLookup lookupDetail(String canonicalId) {
        if (canonicalId == null || canonicalId.isBlank() || redis == null) {
            return DetailLookup.miss();
        }
        try {
            String json = redis.opsForValue().get(detailKey(canonicalId));
            if (json == null || json.isBlank()) {
                return DetailLookup.miss();
            }
            PublicProblemDetail detail = objectMapper.readValue(json, PublicProblemDetail.class);
            if (detail == null || !"PUBLIC".equals(detail.getAvailability()) || detail.getId() == null) {
                return DetailLookup.miss();
            }
            return DetailLookup.hit(detail);
        } catch (Exception redisFailure) {
            logger.warn("problem_detail_cache_read_failed");
            return DetailLookup.fallback();
        }
    }

    public void noteDetail(String outcome) {
        if (metrics != null) {
            metrics.problemDetailCache(outcome);
        }
    }

    public void writeDetail(PublicProblemDetail detail) {
        if (detail == null || detail.getId() == null || !"PUBLIC".equals(detail.getAvailability()) || redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(
                detailKey(detail.getId()),
                objectMapper.writeValueAsString(detail),
                detailTtlSeconds,
                TimeUnit.SECONDS);
        } catch (Exception redisFailure) {
            logger.warn("problem_detail_cache_write_failed");
        }
    }

    public void evictDetail(String canonicalId) {
        if (canonicalId == null || canonicalId.isBlank() || redis == null) {
            return;
        }
        try {
            redis.delete(detailKey(canonicalId));
        } catch (Exception redisFailure) {
            logger.warn("problem_detail_cache_evict_failed");
        }
    }

    public void bumpListVersion() {
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().increment(LIST_VERSION_KEY);
        } catch (Exception redisFailure) {
            logger.warn("problem_list_version_bump_failed");
        }
    }

    public boolean listCacheable(String difficulty, String category, List<String> tags, String search,
                                 boolean cursor, int page, int size) {
        if (cursor || page < 0 || page > MAX_CACHED_PAGE || !CACHED_PAGE_SIZES.contains(size)) {
            return false;
        }
        if (category != null && !category.isBlank()) {
            return false;
        }
        if (search != null && !search.isBlank()) {
            return false;
        }
        if (tags != null && !tags.isEmpty()) {
            return false;
        }
        return difficulty == null || difficulty.isBlank() || CACHED_DIFFICULTIES.contains(difficulty);
    }

    public Map<String, Object> readList(boolean admin, int page, int size, String difficulty) {
        if (!listCacheable(difficulty, null, null, null, false, page, size) || redis == null) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(listKey(currentVersion(), admin, page, size, difficulty));
            if (json == null || json.isBlank()) {
                metrics.problemListCache("miss");
                return null;
            }
            Map<String, Object> body = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
            if (!stillVisible(body, admin)) {
                metrics.problemListCache("miss");
                return null;
            }
            metrics.problemListCache("hit");
            return body;
        } catch (Exception redisFailure) {
            metrics.problemListCache("fallback");
            logger.warn("problem_list_cache_read_failed");
            return null;
        }
    }

    public void writeList(boolean admin, int page, int size, String difficulty, Map<String, Object> body) {
        if (body == null || !listCacheable(difficulty, null, null, null, false, page, size) || redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(
                listKey(currentVersion(), admin, page, size, difficulty),
                objectMapper.writeValueAsString(body),
                listTtlSeconds,
                TimeUnit.SECONDS);
        } catch (Exception redisFailure) {
            logger.warn("problem_list_cache_write_failed");
        }
    }

    public static final class DetailLookup {
        private final String outcome;
        private final PublicProblemDetail detail;

        private DetailLookup(String outcome, PublicProblemDetail detail) {
            this.outcome = outcome;
            this.detail = detail;
        }

        public static DetailLookup hit(PublicProblemDetail detail) { return new DetailLookup("hit", detail); }
        public static DetailLookup miss() { return new DetailLookup("miss", null); }
        public static DetailLookup fallback() { return new DetailLookup("fallback", null); }

        public String outcome() { return outcome; }
        public PublicProblemDetail detail() { return detail; }
    }

    static String detailKey(String canonicalId) {
        return DETAIL_PREFIX + canonicalId;
    }

    static String listKey(String version, boolean admin, int page, int size, String difficulty) {
        String audience = admin ? "admin" : "practice";
        String difficultyKey = difficulty == null || difficulty.isBlank() ? "all" : difficulty;
        return LIST_KEY_PREFIX + version + ":" + audience + ":" + page + ":" + size + ":" + difficultyKey;
    }

    private String currentVersion() {
        String version = redis.opsForValue().get(LIST_VERSION_KEY);
        if (version != null && !version.isBlank()) {
            return version;
        }
        redis.opsForValue().setIfAbsent(LIST_VERSION_KEY, "0");
        String created = redis.opsForValue().get(LIST_VERSION_KEY);
        return created == null || created.isBlank() ? "0" : created;
    }

    /**
     * Practice pages are returned only when every cached id is still a practice
     * problem. A failed list-version bump therefore cannot keep a CONTEST_ONLY
     * problem on a student page. Admin pages only check that the ids still exist.
     */
    private boolean stillVisible(Map<String, Object> body, boolean admin) {
        Object raw = body.get("problems");
        if (!(raw instanceof List<?> problems)) {
            return false;
        }
        if (problems.isEmpty()) {
            return true;
        }
        List<String> ids = new ArrayList<>();
        for (Object item : problems) {
            if (!(item instanceof Map<?, ?> map) || map.get("id") == null) {
                return false;
            }
            ids.add(String.valueOf(map.get("id")));
        }
        Criteria idMatch = Criteria.where("_id").in(ids);
        Query query = Query.query(admin
            ? idMatch
            : new Criteria().andOperator(idMatch, ProblemAvailability.practiceListingCriteria()));
        query.fields().include("availability");
        List<Problem> found = mongoTemplate.find(query, Problem.class);
        return found.size() == ids.size();
    }
}
