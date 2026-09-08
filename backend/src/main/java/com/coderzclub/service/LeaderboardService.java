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
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {
    private static final String ZSET = "coderzclub:leaderboard";
    private static final String CACHE = "coderzclub:leaderboard:top:";

    @Autowired private StringRedisTemplate redis;
    @Autowired private UserRepository userRepository;
    @Autowired private ObjectMapper objectMapper;
    @Value("${leaderboard.cache-ttl-seconds:15}") private long cacheTtlSeconds;

    public List<LeaderboardEntry> top(int requested) {
        int limit = Math.max(1, Math.min(100, requested));
        String key = CACHE + limit;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) return objectMapper.readValue(cached, new TypeReference<List<LeaderboardEntry>>() {});
            Set<String> ids = redis.opsForZSet().reverseRange(ZSET, 0, limit - 1);
            List<LeaderboardEntry> entries = ids == null || ids.isEmpty()
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
            redis.opsForZSet().add(ZSET, user.getId(), user.getTotalPoints());
            invalidate();
        } catch (Exception ignored) { }
    }

    public void invalidate() {
        try {
            Set<String> keys = redis.keys(CACHE + "*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        } catch (Exception ignored) { }
    }

    private List<LeaderboardEntry> mongoTop(int limit) {
        return userRepository.findAllByOrderByTotalPointsDesc(PageRequest.of(0, limit,
            Sort.by(Sort.Direction.DESC, "totalPoints"))).getContent().stream()
            .map(this::entry).collect(Collectors.toList());
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