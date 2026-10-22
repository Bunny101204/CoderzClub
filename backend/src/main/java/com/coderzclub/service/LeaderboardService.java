package com.coderzclub.service;

import com.coderzclub.dto.LeaderboardEntry;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {
    static final String ZSET = "coderzclub:leaderboard";
    static final String VERSION = "coderzclub:leaderboard:version";
    static final String CACHE = "coderzclub:leaderboard:top:";
    public static final int MAX_PAGE_SIZE = 50;
    private static final String ACTIVE_STATUS_EXCLUSION = "DELETED";

    @Autowired private StringRedisTemplate redis;
    @Autowired private UserRepository userRepository;
    @Autowired private ObjectMapper objectMapper;
    @Value("${leaderboard.cache-ttl-seconds:15}") private long cacheTtlSeconds;
    @Value("${leaderboard.rebuild-on-startup:false}") private boolean rebuildOnStartup;

    @PostConstruct
    public void rebuildOnStartupIfEnabled() {
        if (rebuildOnStartup) rebuild();
    }

    public static int boundSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    @SuppressWarnings("unchecked")
    public List<LeaderboardEntry> top(int requested) {
        Object users = page(0, requested).get("users");
        return users instanceof List<?> list ? (List<LeaderboardEntry>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> page(int page, int size) {
        int safeSize = boundSize(size);
        int safePage = Math.max(0, page);
        List<LeaderboardEntry> entries;
        long total = countActive();
        try {
            entries = redisSlice(safePage, safeSize, total);
        } catch (Exception ignored) {
            entries = mongoSlice(safePage, safeSize);
            total = countActive();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("users", entries);
        body.put("page", safePage);
        body.put("size", safeSize);
        body.put("total", total);
        return body;
    }

    public Long rank(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            Long redisRank = redis.opsForZSet().reverseRank(ZSET, userId);
            if (redisRank != null) {
                return redisRank + 1;
            }
        } catch (Exception ignored) {
        }
        return mongoRank(userId);
    }

    public void update(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        if (user.isDeleted()) {
            removeParticipant(user.getId());
            return;
        }
        try {
            redis.execute(new SessionCallback<List<Object>>() {
                @Override
                public List<Object> execute(RedisOperations operations) {
                    operations.multi();
                    operations.opsForZSet().add(ZSET, user.getId(), user.getTotalPoints());
                    operations.opsForValue().increment(VERSION);
                    return operations.exec();
                }
            });
        } catch (Exception ignored) { }
    }

    public void invalidate() {
        try {
            redis.opsForValue().increment(VERSION);
        } catch (Exception ignored) { }
    }

    public void removeParticipant(String userId) {
        if (userId == null) {
            return;
        }
        try {
            redis.execute(new SessionCallback<List<Object>>() {
                @Override
                public List<Object> execute(RedisOperations operations) {
                    operations.multi();
                    operations.opsForZSet().remove(ZSET, userId);
                    operations.opsForValue().increment(VERSION);
                    return operations.exec();
                }
            });
        } catch (Exception ignored) {
            invalidate();
        }
    }

    @Scheduled(fixedDelayString = "${leaderboard.rebuild-fixed-delay-ms:300000}")
    public void scheduledRebuild() {
        rebuild();
    }

    public void rebuild() {
        try {
            List<User> users = userRepository.findByAccountStatusNot(
                ACTIVE_STATUS_EXCLUSION,
                PageRequest.of(0, 10_000, leaderboardSort())).getContent();
            redis.execute(new SessionCallback<List<Object>>() {
                @Override
                public List<Object> execute(RedisOperations operations) {
                    operations.multi();
                    operations.delete(ZSET);
                    for (User user : users) {
                        if (user.isDeleted()) {
                            continue;
                        }
                        operations.opsForZSet().add(ZSET, user.getId(), user.getTotalPoints());
                    }
                    operations.opsForValue().increment(VERSION);
                    return operations.exec();
                }
            });
        } catch (Exception ignored) { }
    }

    private List<LeaderboardEntry> redisSlice(int page, int size, long total) throws Exception {
        int expectedEntries = (int) Math.min(size, Math.max(0, total - (long) page * size));
        String version = currentVersion();
        String key = CACHE + version + ":" + page + ":" + size;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            List<LeaderboardEntry> cachedEntries = objectMapper.readValue(
                cached, new TypeReference<List<LeaderboardEntry>>() {});
            List<LeaderboardEntry> filtered = dropDeletedIdentity(cachedEntries);
            if (filtered.size() >= expectedEntries || filtered.size() == cachedEntries.size()) {
                assignRanks(filtered, page, size);
                return filtered;
            }
        }
        long start = (long) page * size;
        long end = start + size - 1;
        Set<String> ids = redis.opsForZSet().reverseRange(ZSET, start, end);
        List<LeaderboardEntry> entries = ids == null || ids.isEmpty()
            ? mongoSlice(page, size)
            : hydrate(ids, page, size);
        if (entries.size() < expectedEntries) {
            entries = mongoSlice(page, size);
        }
        redis.opsForValue().set(key, objectMapper.writeValueAsString(entries), cacheTtlSeconds, TimeUnit.SECONDS);
        return entries;
    }

    private List<LeaderboardEntry> mongoSlice(int page, int size) {
        Page<User> result = userRepository.findByAccountStatusNot(
            ACTIVE_STATUS_EXCLUSION,
            PageRequest.of(page, size, leaderboardSort()));
        List<LeaderboardEntry> entries = result.getContent().stream()
            .filter(user -> !user.isDeleted())
            .map(this::entry)
            .collect(Collectors.toList());
        assignRanks(entries, page, size);
        return entries;
    }

    private Long mongoRank(String userId) {
        return userRepository.findById(userId)
            .filter(user -> !user.isDeleted())
            .map(user -> {
                long ahead = userRepository.findByAccountStatusNot(
                    ACTIVE_STATUS_EXCLUSION,
                    PageRequest.of(0, MAX_PAGE_SIZE * 20, leaderboardSort()))
                    .getContent().stream()
                    .filter(candidate -> !candidate.isDeleted())
                    .takeWhile(candidate -> !userId.equals(candidate.getId()))
                    .count();
                return ahead + 1;
            })
            .orElse(null);
    }

    private String currentVersion() {
        String version = redis.opsForValue().get(VERSION);
        if (version != null) return version;
        redis.opsForValue().setIfAbsent(VERSION, "0");
        return "0";
    }

    private List<LeaderboardEntry> hydrate(Set<String> ids, int page, int size) {
        Map<String, User> users = userRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (String id : ids) {
            User user = users.get(id);
            if (user == null || user.isDeleted()) {
                continue;
            }
            entries.add(entry(user));
        }
        assignRanks(entries, page, size);
        return entries;
    }

    private List<LeaderboardEntry> dropDeletedIdentity(List<LeaderboardEntry> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream()
            .filter(entry -> entry != null && entry.getUsername() != null && !entry.getUsername().startsWith("deleted-"))
            .toList();
    }

    private void assignRanks(List<LeaderboardEntry> entries, int page, int size) {
        long startRank = (long) page * size + 1;
        for (int index = 0; index < entries.size(); index++) {
            entries.get(index).setRank(startRank + index);
        }
    }

    private long countActive() {
        try {
            return userRepository.countByAccountStatusNot(ACTIVE_STATUS_EXCLUSION);
        } catch (Exception ignored) {
            return userRepository.count();
        }
    }

    private static Sort leaderboardSort() {
        return Sort.by(Sort.Direction.DESC, "totalPoints").and(Sort.by(Sort.Direction.ASC, "_id"));
    }

    private LeaderboardEntry entry(User user) {
        LeaderboardEntry entry = new LeaderboardEntry();
        entry.setId(user.getId());
        entry.setUsername(user.getUsername());
        entry.setProfilePicture(user.getProfilePicture());
        entry.setTotalPoints(user.getTotalPoints());
        entry.setProblemsSolved(user.getProblemsSolved());
        return entry;
    }
}
