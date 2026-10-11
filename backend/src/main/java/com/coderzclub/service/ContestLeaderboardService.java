package com.coderzclub.service;

import com.coderzclub.dto.ContestLeaderboardEntry;
import com.coderzclub.model.Contest;
import com.coderzclub.model.ContestScore;
import com.coderzclub.model.ContestScoringMode;
import com.coderzclub.model.ContestStatus;
import com.coderzclub.model.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Indexed ContestScore pagination. Redis caches rendered pages only;
 * Mongo remains authoritative. Redis failure never blocks reads or scoring.
 */
@Service
public class ContestLeaderboardService {
    private static final Logger logger = LoggerFactory.getLogger(ContestLeaderboardService.class);
    public static final int MAX_PAGE_SIZE = 50;
    public static final int DEFAULT_PAGE_SIZE = 20;
    static final String CACHE_PREFIX = "coderzclub:contest-leaderboard:";

    private final MongoTemplate mongoTemplate;
    private final ContestAccessService contestAccess;
    private final Clock clock;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final OperationalMetrics metrics;
    private final long cacheTtlSeconds;

    public ContestLeaderboardService(
        MongoTemplate mongoTemplate,
        ContestAccessService contestAccess,
        Clock clock,
        StringRedisTemplate redis,
        ObjectMapper objectMapper,
        OperationalMetrics metrics,
        @Value("${contest.leaderboard.cache-ttl-seconds:5}") long cacheTtlSeconds
    ) {
        this.mongoTemplate = mongoTemplate;
        this.contestAccess = contestAccess;
        this.clock = clock;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.cacheTtlSeconds = Math.max(1, cacheTtlSeconds);
    }

    public Map<String, Object> page(User user, String contestId, int page, int size) {
        requireVisibleLiveOrEnded(user, contestId);
        int safeSize = boundSize(size);
        int safePage = Math.max(0, page);
        String cacheKey = CACHE_PREFIX + contestId + ":" + safePage + ":" + safeSize;
        Map<String, Object> cached = readCache(cacheKey);
        if (cached != null) {
            return decorateForViewer(cached, user);
        }
        Map<String, Object> body = loadFromMongo(contestId, safePage, safeSize);
        writeCache(cacheKey, viewerNeutral(body));
        return decorateForViewer(body, user);
    }

    void requireVisibleLiveOrEnded(User user, String contestId) {
        if (user == null || user.isDeleted() || user.getId() == null) {
            throw ContestAdmissionException.notFound();
        }
        Contest contest = mongoTemplate.findById(contestId, Contest.class);
        if (contest == null || !contest.isPublished()) {
            throw ContestAdmissionException.notFound();
        }
        boolean admin = "ADMIN".equalsIgnoreCase(user.getRole());
        ContestAccessService.UserBatchScope scope = contestAccess.scopeForUser(user.getId());
        if (!admin && !contestAccess.isEligible(user, contest, scope)) {
            throw ContestAdmissionException.notFound();
        }
        ContestStatus status = contestAccess.status(contest, clock.instant());
        if (status == ContestStatus.UPCOMING) {
            throw ContestAdmissionException.notLive();
        }
        if (status != ContestStatus.LIVE && status != ContestStatus.ENDED) {
            throw ContestAdmissionException.notFound();
        }
    }

    Map<String, Object> loadFromMongo(String contestId, int page, int size) {
        Query base = Query.query(Criteria.where("contestId").is(contestId));
        long total = mongoTemplate.count(base, ContestScore.class);
        Query pageQuery = Query.query(Criteria.where("contestId").is(contestId))
            .with(leaderboardSort())
            .skip((long) page * size)
            .limit(size);
        List<ContestScore> scores = mongoTemplate.find(pageQuery, ContestScore.class);
        List<String> userIds = new ArrayList<>();
        for (ContestScore score : scores) {
            if (score.getUserId() != null) {
                userIds.add(score.getUserId());
            }
        }
        Map<String, User> users = loadUsers(userIds);
        List<ContestLeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            ContestScore score = scores.get(i);
            User participant = users.get(score.getUserId());
            ContestLeaderboardEntry entry = new ContestLeaderboardEntry();
            entry.setRank(page * size + i + 1);
            entry.setUsername(safeUsername(participant));
            entry.setTotalPoints(score.getTotalPoints());
            entry.setSolvedCount(score.getSolvedCount());
            entries.add(entry);
        }
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entries", entries);
        body.put("currentPage", page);
        body.put("totalPages", totalPages);
        body.put("totalItems", total);
        body.put("scoringMode", ContestScoringMode.POINTS_FIRST_ACCEPTED.name());
        return body;
    }

    private Map<String, User> loadUsers(List<String> userIds) {
        Map<String, User> byId = new HashMap<>();
        if (userIds.isEmpty()) {
            return byId;
        }
        Query query = Query.query(Criteria.where("_id").in(userIds));
        query.fields().include("username");
        for (User user : mongoTemplate.find(query, User.class)) {
            if (user.getId() != null) {
                byId.put(user.getId(), user);
            }
        }
        return byId;
    }

    private Map<String, Object> readCache(String key) {
        if (redis == null) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                if (metrics != null) {
                    metrics.contestLeaderboardCache("miss");
                }
                return null;
            }
            Map<String, Object> body = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
            if (metrics != null) {
                metrics.contestLeaderboardCache("hit");
            }
            return body;
        } catch (Exception redisFailure) {
            if (metrics != null) {
                metrics.contestLeaderboardCache("fallback");
            }
            logger.warn("contest_leaderboard_cache_read_failed");
            return null;
        }
    }

    private void writeCache(String key, Map<String, Object> body) {
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(body), cacheTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Scoring must not depend on cache writes.
        }
    }

    static Map<String, Object> viewerNeutral(Map<String, Object> body) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.put("entries", stripCurrentUser(body.get("entries")));
        return copy;
    }

    private static List<Map<String, Object>> stripCurrentUser(Object raw) {
        List<Map<String, Object>> entries = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return entries;
        }
        for (Object item : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            if (item instanceof ContestLeaderboardEntry entry) {
                row.put("rank", entry.getRank());
                row.put("username", entry.getUsername());
                row.put("totalPoints", entry.getTotalPoints());
                row.put("solvedCount", entry.getSolvedCount());
            } else if (item instanceof Map<?, ?> map) {
                row.put("rank", map.get("rank"));
                row.put("username", map.get("username"));
                row.put("totalPoints", map.get("totalPoints"));
                row.put("solvedCount", map.get("solvedCount"));
            }
            entries.add(row);
        }
        return entries;
    }

    static Map<String, Object> decorateForViewer(Map<String, Object> body, User viewer) {
        Map<String, Object> copy = viewerNeutral(body);
        Object raw = copy.get("entries");
        if (!(raw instanceof List<?> list) || viewer == null || viewer.getUsername() == null) {
            return copy;
        }
        String username = viewer.getUsername();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) map;
                Object entryName = row.get("username");
                row.put("currentUser", entryName instanceof String name && username.equalsIgnoreCase(name));
            }
        }
        return copy;
    }

    private static String safeUsername(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank() || user.isDeleted()) {
            return "Unknown user";
        }
        return user.getUsername();
    }

    public static int boundSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    public static Sort leaderboardSort() {
        return Sort.by(
            Sort.Order.desc("totalPoints"),
            Sort.Order.desc("solvedCount"),
            Sort.Order.asc("userId")
        );
    }
}
