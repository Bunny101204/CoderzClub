package com.coderzclub.service;

import com.coderzclub.dto.ProblemProgressEntry;
import com.coderzclub.dto.UserProfileStatsResponse;
import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
import com.coderzclub.model.User;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserProgressService {
    public static final String SUCCESS_RATE_DEFINITION =
        "accepted judged submissions / completed student submissions; infrastructure failures excluded";

    private final MongoTemplate mongoTemplate;

    public UserProgressService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<ProblemProgressEntry> progressForUser(String userId) {
        List<Document> groups = groupedOutcomes(userId);
        Map<String, Set<SubmissionAttemptClassifier.Kind>> byStoredId = new LinkedHashMap<>();
        for (Document group : groups) {
            String storedId = stringId(group.get("_id"));
            if (storedId == null || storedId.isBlank()) {
                continue;
            }
            Set<SubmissionAttemptClassifier.Kind> kinds = byStoredId.computeIfAbsent(storedId, ignored -> new HashSet<>());
            addKinds(kinds, group.get("results"));
            addKinds(kinds, group.get("verdicts"));
        }
        Map<String, Problem> problems = loadProblems(byStoredId.keySet());
        List<ProblemProgressEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Set<SubmissionAttemptClassifier.Kind>> item : byStoredId.entrySet()) {
            String status = SubmissionAttemptClassifier.problemStatus(item.getValue());
            if (status == null) {
                continue;
            }
            Problem problem = resolveProblem(item.getKey(), problems);
            ProblemProgressEntry entry = new ProblemProgressEntry();
            entry.setStatus(status);
            if (problem != null) {
                entry.setProblemId(problem.getId());
                entry.setNumericId(problem.getNumericId());
                entry.setAliases(aliases(problem, item.getKey()));
            } else {
                entry.setProblemId(item.getKey());
                entry.setAliases(List.of(item.getKey()));
            }
            entries.add(entry);
        }
        return entries;
    }

    public UserProfileStatsResponse profileStats(User user) {
        UserProfileStatsResponse response = new UserProfileStatsResponse();
        if (user == null) {
            return response;
        }
        response.setTotalPoints(user.getTotalPoints());
        response.setCurrentStreak(user.getCurrentStreak());
        response.setLongestStreak(user.getLongestStreak());
        response.setSuccessRateDefinition(SUCCESS_RATE_DEFINITION);
        response.setActivityTimezone("UTC");

        List<ProblemProgressEntry> progress = progressForUser(user.getId());
        List<ProblemProgressEntry> solved = progress.stream().filter(item -> "SOLVED".equals(item.getStatus())).toList();
        response.setUniqueProblemsSolved(solved.size());
        response.setDifficultySolved(difficultyCounts(solved));

        SubmissionCounts counts = countSubmissions(user.getId());
        response.setAcceptedJudgedSubmissions(counts.accepted);
        response.setCompletedStudentSubmissions(counts.student);
        response.setSuccessRate(counts.student == 0 ? 0.0 : (double) counts.accepted / (double) counts.student);
        response.setActivity(activityByUtcDate(user.getId()));
        return response;
    }

    static Map<String, Integer> difficultyCounts(List<ProblemProgressEntry> solved, Map<String, String> difficultyById) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("EASY", 0);
        counts.put("MEDIUM", 0);
        counts.put("HARD", 0);
        Set<String> counted = new HashSet<>();
        for (ProblemProgressEntry entry : solved) {
            String id = entry.getProblemId();
            if (id == null || !counted.add(id)) {
                continue;
            }
            String bucket = normalizeDifficulty(difficultyById.get(id));
            if (!"EASY".equals(bucket) && !"MEDIUM".equals(bucket) && !"HARD".equals(bucket)) {
                continue;
            }
            counts.put(bucket, counts.get(bucket) + 1);
        }
        return counts;
    }

    static String normalizeDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return "UNKNOWN";
        }
        String value = difficulty.trim().toUpperCase();
        if ("BASIC".equals(value) || "EASY".equals(value)) {
            return "EASY";
        }
        if ("INTERMEDIATE".equals(value) || "MEDIUM".equals(value)) {
            return "MEDIUM";
        }
        if ("ADVANCED".equals(value) || "HARD".equals(value)) {
            return "HARD";
        }
        return "UNKNOWN";
    }

    static List<Map<String, Object>> activityFromStudentAttempts(List<Submission> submissions) {
        Map<String, Integer> byDay = new LinkedHashMap<>();
        if (submissions == null) {
            return List.of();
        }
        for (Submission submission : submissions) {
            if (submission == null || submission.getCreatedAt() == null) {
                continue;
            }
            if (!SubmissionAttemptClassifier.countsTowardActivity(submission.getResult(), submission.getVerdict())) {
                continue;
            }
            LocalDate day = Instant.ofEpochMilli(submission.getCreatedAt().getTime()).atZone(ZoneOffset.UTC).toLocalDate();
            String key = day.toString();
            byDay.put(key, byDay.getOrDefault(key, 0) + 1);
        }
        List<Map<String, Object>> activity = new ArrayList<>();
        byDay.forEach((date, count) -> activity.add(Map.of("date", date, "count", count)));
        return activity;
    }

    private Map<String, Integer> difficultyCounts(List<ProblemProgressEntry> solved) {
        Set<String> ids = new HashSet<>();
        Set<Integer> numericIds = new HashSet<>();
        for (ProblemProgressEntry entry : solved) {
            if (entry.getProblemId() != null) {
                ids.add(entry.getProblemId());
            }
            if (entry.getNumericId() != null) {
                numericIds.add(entry.getNumericId());
            }
        }
        Map<String, Problem> problems = loadProblems(ids);
        if (!numericIds.isEmpty()) {
            Query numericQuery = Query.query(Criteria.where("numericId").in(numericIds));
            numericQuery.fields().include("numericId").include("difficulty");
            for (Problem problem : mongoTemplate.find(numericQuery, Problem.class)) {
                problems.putIfAbsent(problem.getId(), problem);
            }
        }
        Map<String, String> difficultyById = new HashMap<>();
        for (Problem problem : problems.values()) {
            difficultyById.put(problem.getId(), problem.getDifficulty());
            if (problem.getNumericId() != null) {
                difficultyById.put(String.valueOf(problem.getNumericId()), problem.getDifficulty());
            }
        }
        return difficultyCounts(solved, difficultyById);
    }

    private List<Document> groupedOutcomes(String userId) {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("userId").is(userId)),
            Aggregation.group("problemId")
                .addToSet("result").as("results")
                .addToSet("verdict").as("verdicts")
        );
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, "submissions", Document.class);
        return results.getMappedResults();
    }

    private SubmissionCounts countSubmissions(String userId) {
        Query query = Query.query(Criteria.where("userId").is(userId));
        query.fields().include("result").include("verdict");
        long accepted = 0;
        long student = 0;
        for (Submission submission : mongoTemplate.find(query, Submission.class)) {
            SubmissionAttemptClassifier.Kind kind = SubmissionAttemptClassifier.classify(
                submission.getResult(), submission.getVerdict());
            if (kind == SubmissionAttemptClassifier.Kind.ACCEPTED) {
                accepted++;
                student++;
            } else if (kind == SubmissionAttemptClassifier.Kind.STUDENT_VERDICT) {
                student++;
            }
        }
        return new SubmissionCounts(accepted, student);
    }

    private List<Map<String, Object>> activityByUtcDate(String userId) {
        Query query = Query.query(Criteria.where("userId").is(userId));
        query.fields().include("createdAt").include("result").include("verdict");
        return activityFromStudentAttempts(mongoTemplate.find(query, Submission.class));
    }

    private Map<String, Problem> loadProblems(Set<String> storedIds) {
        Map<String, Problem> byAnyId = new HashMap<>();
        if (storedIds == null || storedIds.isEmpty()) {
            return byAnyId;
        }
        List<String> ids = new ArrayList<>(storedIds);
        List<Integer> numericIds = new ArrayList<>();
        for (String id : storedIds) {
            try {
                numericIds.add(Integer.valueOf(id));
            } catch (NumberFormatException ignored) {
            }
        }
        Query query = new Query();
        if (numericIds.isEmpty()) {
            query.addCriteria(Criteria.where("_id").in(ids));
        } else {
            query.addCriteria(new Criteria().orOperator(
                Criteria.where("_id").in(ids),
                Criteria.where("numericId").in(numericIds)
            ));
        }
        query.fields().include("numericId").include("difficulty");
        for (Problem problem : mongoTemplate.find(query, Problem.class)) {
            byAnyId.put(problem.getId(), problem);
            if (problem.getNumericId() != null) {
                byAnyId.put(String.valueOf(problem.getNumericId()), problem);
            }
        }
        return byAnyId;
    }

    private static Problem resolveProblem(String storedId, Map<String, Problem> problems) {
        Problem direct = problems.get(storedId);
        if (direct != null) {
            return direct;
        }
        return problems.values().stream()
            .filter(problem -> storedId.equals(problem.getId())
                || (problem.getNumericId() != null && storedId.equals(String.valueOf(problem.getNumericId()))))
            .findFirst()
            .orElse(null);
    }

    private static List<String> aliases(Problem problem, String storedId) {
        Set<String> aliases = new HashSet<>();
        aliases.add(storedId);
        if (problem.getId() != null) {
            aliases.add(problem.getId());
        }
        if (problem.getNumericId() != null) {
            aliases.add(String.valueOf(problem.getNumericId()));
        }
        return new ArrayList<>(aliases);
    }

    private static void addKinds(Set<SubmissionAttemptClassifier.Kind> kinds, Object raw) {
        if (!(raw instanceof List<?> values)) {
            return;
        }
        for (Object value : values) {
            kinds.add(SubmissionAttemptClassifier.classifyOne(value == null ? null : String.valueOf(value)));
        }
    }

    private static String stringId(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record SubmissionCounts(long accepted, long student) {}
}
