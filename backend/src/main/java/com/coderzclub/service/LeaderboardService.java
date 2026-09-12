package com.coderzclub.service;

import com.coderzclub.dto.LeaderboardEntry;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {
    private static final String ZSET = "coderzclub:leaderboard";
    private static final String VERSION = "coderzclub:leaderboard:version";
    private static final String CACHE = "coderzclub:leaderboard:top:";

    @Autowired private StringRedisTemplate redis;
    @Autowired private UserRepository userRepository;
    @Autowired private ObjectMapper objectMapper;
    @Value("${leaderboard.cache-ttl-seconds:15}") private long cacheTtlSeconds;
    @Value("${leaderboard.rebuild-on-startup:false}") private boolean rebuildOnStartup;

    @PostConstruct
    public void rebuildOnStartupIfEnabled() {
        if (rebuildOnStartup) rebuild();
    }

    public List<LeaderboardEntry> top(int requested) {
        int limit = Math.max(1, Math.min(100, requested));
        try {
            long participantCount = userRepository.count();
            int expectedEntries = (int) Math.min(limit, participantCount);
            String version = currentVersion();
            String key = CACHE + version + ":" + limit;
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                List<LeaderboardEntry> cachedEntries = objectMapper.readValue(
                    cached, new TypeReference<List<LeaderboardEntry>>() {});
                if (cachedEntries.size() >= expectedEntries) return cachedEntries;
            }
            Set<String> ids = redis.opsForZSet().reverseRange(ZSET, 0, limit - 1);
            List<LeaderboardEntry> entries = ids == null || ids.size() < expectedEntries
                ? mongoTop(limit) : hydrate(ids);
            redis.opsForValue().set(key, objectMapper.writeValueAsString(entries), cacheTtlSeconds, TimeUnit.SECONDS);
            return entries;
        } catch (Exception ignored) {
            return mongoTop(limit);
        }
    }

    public Long rank(String userId) {
        try {
            Long rank = redis.opsForZSet().reverseRank(ZSET, userId);
            return rank == null ? null : rank + 1;
        } catch (Exception ignored) { return null; }
    }

    public void update(User user) {
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

    @Scheduled(fixedDelayString = "${leaderboard.rebuild-fixed-delay-ms:300000}")
    public void scheduledRebuild() {
        rebuild();
    }

    public void rebuild() {
        try {
            List<User> users = userRepository.findAll();
            redis.execute(new SessionCallback<List<Object>>() {
                @Override
                public List<Object> execute(RedisOperations operations) {
                    operations.multi();
                    operations.delete(ZSET);
                    for (User user : users) {
                        operations.opsForZSet().add(ZSET, user.getId(), user.getTotalPoints());
                    }
                    operations.opsForValue().increment(VERSION);
                    return operations.exec();
                }
            });
        } catch (Exception ignored) { }
    }

    private String currentVersion() {
        String version = redis.opsForValue().get(VERSION);
        if (version != null) return version;
        redis.opsForValue().setIfAbsent(VERSION, "0");
        return "0";
    }

    private List<LeaderboardEntry> mongoTop(int limit) {
        List<LeaderboardEntry> entries = userRepository.findAllByOrderByTotalPointsDesc(PageRequest.of(0, limit,
            Sort.by(Sort.Direction.DESC, "totalPoints"))).getContent().stream()
            .map(this::entry).collect(Collectors.toList());
        for (int index = 0; index < entries.size(); index++) entries.get(index).setRank(index + 1L);
        return entries;
    }

    private List<LeaderboardEntry> hydrate(Set<String> ids) {
        Map<String, User> users = userRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(User::getId, user -> user));
        long rank = 1;
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (String id : ids) {
            User user = users.get(id);
            if (user != null) { LeaderboardEntry entry = entry(user); entry.setRank(rank++); entries.add(entry); }
        }
        return entries;
    }

    private LeaderboardEntry entry(User user) {
        LeaderboardEntry entry = new LeaderboardEntry();
        entry.setId(user.getId()); entry.setUsername(user.getUsername());
        entry.setProfilePicture(user.getProfilePicture()); entry.setTotalPoints(user.getTotalPoints());
        entry.setProblemsSolved(user.getProblemsSolved());
        return entry;
    }
}