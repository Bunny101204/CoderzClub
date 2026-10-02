package com.coderzclub.service;

import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchAssignment;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
import com.coderzclub.model.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BatchReportService {
    public static final int MAX_EXPORT_STUDENTS = 500;

    private final MongoTemplate mongoTemplate;
    private final BatchService batchService;

    public BatchReportService(MongoTemplate mongoTemplate, BatchService batchService) {
        this.mongoTemplate = mongoTemplate;
        this.batchService = batchService;
    }

    public Map<String, Object> report(String batchId, int page, int size, String student) {
        return build(batchId, page, size, student, false);
    }

    public String csv(String batchId, String student) {
        Map<String, Object> report = build(batchId, 0, MAX_EXPORT_STUDENTS, student, true);
        return toCsv(report);
    }

    static String cellStatus(Collection<SubmissionAttemptClassifier.Kind> kinds) {
        String status = SubmissionAttemptClassifier.problemStatus(kinds);
        return status == null ? "UNSOLVED" : status;
    }

    Map<String, Object> build(String batchId, int page, int size, String student, boolean export) {
        Batch batch = batchService.requireBatch(batchId);
        int safeSize = export ? MAX_EXPORT_STUDENTS : BatchService.boundSize(size);
        int safePage = Math.max(0, page);

        List<BatchMember> allMembers = mongoTemplate.find(
            Query.query(Criteria.where("batchId").is(batchId)).with(Sort.by("addedAt")),
            BatchMember.class);
        if (export && allMembers.size() > MAX_EXPORT_STUDENTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "CSV export supports at most " + MAX_EXPORT_STUDENTS
                    + " students. Filter the report or split the batch.");
        }
        List<BatchAssignment> assignments = mongoTemplate.find(
            Query.query(Criteria.where("batchId").is(batchId)).with(Sort.by("assignedAt")),
            BatchAssignment.class);
        if (assignments.size() > BatchService.MAX_ASSIGNMENTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A batch can have at most " + BatchService.MAX_ASSIGNMENTS + " assigned problems");
        }

        List<String> assignedProblemIds = assignments.stream().map(BatchAssignment::getProblemId).toList();
        Map<String, Problem> problems = loadProblems(assignedProblemIds);
        List<String> lookupProblemIds = lookupIds(assignedProblemIds, problems);

        List<String> allUserIds = allMembers.stream().map(BatchMember::getUserId).toList();
        Map<String, User> usersById = loadUsers(allUserIds);
        List<String> filteredUserIds = filterStudents(allUserIds, usersById, student);
        long totalStudents = filteredUserIds.size();
        int from = Math.min(safePage * safeSize, filteredUserIds.size());
        int to = Math.min(from + safeSize, filteredUserIds.size());
        List<String> pageUserIds = filteredUserIds.subList(from, to);

        List<Submission> submissions = loadSubmissions(allUserIds, lookupProblemIds);
        Map<String, Map<String, Set<SubmissionAttemptClassifier.Kind>>> kinds =
            kindsByUserAndProblem(submissions, assignedProblemIds, problems);
        Map<String, Date> latestActivity = latestActivity(submissions, assignedProblemIds, problems);

        int totalCells = allUserIds.size() * assignedProblemIds.size();
        int solvedCells = 0;
        int attemptedCells = 0;
        Map<String, Integer> solvedByProblem = new LinkedHashMap<>();
        for (String problemId : assignedProblemIds) {
            solvedByProblem.put(problemId, 0);
        }
        for (String userId : allUserIds) {
            Map<String, Set<SubmissionAttemptClassifier.Kind>> byProblem = kinds.getOrDefault(userId, Map.of());
            for (String problemId : assignedProblemIds) {
                String status = cellStatus(byProblem.get(problemId));
                if ("SOLVED".equals(status)) {
                    solvedCells++;
                    solvedByProblem.put(problemId, solvedByProblem.get(problemId) + 1);
                } else if ("ATTEMPTED".equals(status)) {
                    attemptedCells++;
                }
            }
        }
        int unsolvedCells = totalCells - solvedCells - attemptedCells;

        List<Map<String, Object>> problemColumns = new ArrayList<>();
        for (BatchAssignment assignment : assignments) {
            Problem problem = problems.get(assignment.getProblemId());
            Map<String, Object> column = new LinkedHashMap<>();
            column.put("problemId", assignment.getProblemId());
            column.put("numericId", problem == null ? null : problem.getNumericId());
            column.put("title", problem == null ? null : problem.getTitle());
            column.put("difficulty", problem == null ? null : problem.getDifficulty());
            column.put("tags", problem == null ? List.of() : problem.getTags());
            column.put("solvedCount", solvedByProblem.getOrDefault(assignment.getProblemId(), 0));
            problemColumns.add(column);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (String userId : pageUserIds) {
            User user = usersById.get(userId);
            Map<String, Set<SubmissionAttemptClassifier.Kind>> byProblem = kinds.getOrDefault(userId, Map.of());
            int solved = 0;
            int attempted = 0;
            List<Map<String, Object>> cells = new ArrayList<>();
            for (String problemId : assignedProblemIds) {
                String status = cellStatus(byProblem.get(problemId));
                if ("SOLVED".equals(status)) {
                    solved++;
                } else if ("ATTEMPTED".equals(status)) {
                    attempted++;
                }
                Map<String, Object> cell = new LinkedHashMap<>();
                Problem problem = problems.get(problemId);
                cell.put("problemId", problemId);
                cell.put("numericId", problem == null ? null : problem.getNumericId());
                cell.put("status", status);
                cells.add(cell);
            }
            int unsolved = assignedProblemIds.size() - solved - attempted;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", userId);
            row.put("username", user == null ? null : user.getUsername());
            row.put("email", user == null ? null : user.getEmail());
            row.put("cells", cells);
            row.put("solvedCount", solved);
            row.put("attemptedCount", attempted);
            row.put("unsolvedCount", unsolved);
            row.put("completionPercent", BatchCsv.percentage(solved, assignedProblemIds.size()));
            row.put("latestActivityAt", latestActivity.get(userId));
            rows.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("studentCount", allUserIds.size());
        summary.put("assignedProblemCount", assignedProblemIds.size());
        summary.put("totalCells", totalCells);
        summary.put("solvedCells", solvedCells);
        summary.put("attemptedCells", attemptedCells);
        summary.put("unsolvedCells", unsolvedCells);
        summary.put("completionPercent", BatchCsv.percentage(solvedCells, totalCells));
        summary.put("denominator", "studentCount * assignedProblemCount");

        Map<String, Object> batchView = new LinkedHashMap<>();
        batchView.put("id", batch.getId());
        batchView.put("name", batch.getName());
        batchView.put("description", batch.getDescription());
        batchView.put("active", batch.isActive());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("batch", batchView);
        response.put("summary", summary);
        response.put("problems", problemColumns);
        response.put("students", rows);
        response.put("currentPage", safePage);
        response.put("totalPages", totalStudents == 0 ? 0 : (int) Math.ceil((double) totalStudents / safeSize));
        response.put("totalItems", totalStudents);
        return response;
    }

    static String toCsv(Map<String, Object> report) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> problems = (List<Map<String, Object>>) report.get("problems");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> students = (List<Map<String, Object>>) report.get("students");
        StringBuilder csv = new StringBuilder();
        csv.append("student");
        for (Map<String, Object> problem : problems) {
            csv.append(',').append(BatchCsv.escape(displayProblemHeader(problem)));
        }
        csv.append(",solved,attempted,unsolved,completion\n");
        for (Map<String, Object> student : students) {
            csv.append(BatchCsv.escape(String.valueOf(student.get("username"))));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cells = (List<Map<String, Object>>) student.get("cells");
            for (Map<String, Object> cell : cells) {
                csv.append(',').append(BatchCsv.escape(String.valueOf(cell.get("status"))));
            }
            csv.append(',').append(student.get("solvedCount"))
                .append(',').append(student.get("attemptedCount"))
                .append(',').append(student.get("unsolvedCount"))
                .append(',').append(student.get("completionPercent"))
                .append('\n');
        }
        return csv.toString();
    }

    private List<Submission> loadSubmissions(List<String> userIds, List<String> problemIds) {
        if (userIds.isEmpty() || problemIds.isEmpty()) {
            return List.of();
        }
        Query query = Query.query(Criteria.where("userId").in(userIds).and("problemId").in(problemIds));
        query.fields().include("userId").include("problemId").include("result").include("verdict").include("createdAt");
        return mongoTemplate.find(query, Submission.class);
    }

    private Map<String, Map<String, Set<SubmissionAttemptClassifier.Kind>>> kindsByUserAndProblem(
        List<Submission> submissions, List<String> assignedProblemIds, Map<String, Problem> problems) {
        Map<String, String> canonical = canonicalLookup(assignedProblemIds, problems);
        Map<String, Map<String, Set<SubmissionAttemptClassifier.Kind>>> kinds = new HashMap<>();
        for (Submission submission : submissions) {
            String problemId = canonical.get(submission.getProblemId());
            if (problemId == null) {
                continue;
            }
            kinds.computeIfAbsent(submission.getUserId(), ignored -> new HashMap<>())
                .computeIfAbsent(problemId, ignored -> new HashSet<>())
                .add(SubmissionAttemptClassifier.classify(submission.getResult(), submission.getVerdict()));
        }
        return kinds;
    }

    private Map<String, Date> latestActivity(
        List<Submission> submissions, List<String> assignedProblemIds, Map<String, Problem> problems) {
        Map<String, String> canonical = canonicalLookup(assignedProblemIds, problems);
        Map<String, Date> latest = new HashMap<>();
        for (Submission submission : submissions) {
            if (!canonical.containsKey(submission.getProblemId())) {
                continue;
            }
            if (!SubmissionAttemptClassifier.countsTowardActivity(submission.getResult(), submission.getVerdict())) {
                continue;
            }
            Date created = submission.getCreatedAt();
            if (created == null) {
                continue;
            }
            latest.merge(submission.getUserId(), created, (left, right) -> left.after(right) ? left : right);
        }
        return latest;
    }

    private Map<String, String> canonicalLookup(List<String> assignedProblemIds, Map<String, Problem> problems) {
        Map<String, String> lookup = new HashMap<>();
        for (String problemId : assignedProblemIds) {
            lookup.put(problemId, problemId);
            Problem problem = problems.get(problemId);
            if (problem != null && problem.getNumericId() != null) {
                lookup.put(String.valueOf(problem.getNumericId()), problemId);
            }
        }
        return lookup;
    }

    private List<String> lookupIds(List<String> assignedProblemIds, Map<String, Problem> problems) {
        Set<String> ids = new HashSet<>(assignedProblemIds);
        for (String problemId : assignedProblemIds) {
            Problem problem = problems.get(problemId);
            if (problem != null && problem.getNumericId() != null) {
                ids.add(String.valueOf(problem.getNumericId()));
            }
        }
        return new ArrayList<>(ids);
    }

    private Map<String, Problem> loadProblems(List<String> ids) {
        Map<String, Problem> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        Query query = Query.query(Criteria.where("_id").in(ids));
        query.fields().include("numericId").include("title").include("difficulty").include("tags");
        for (Problem problem : mongoTemplate.find(query, Problem.class)) {
            map.put(problem.getId(), problem);
        }
        return map;
    }

    private Map<String, User> loadUsers(List<String> ids) {
        Map<String, User> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        Query query = Query.query(Criteria.where("_id").in(ids));
        query.fields().include("username").include("email");
        for (User user : mongoTemplate.find(query, User.class)) {
            map.put(user.getId(), user);
        }
        return map;
    }

    private List<String> filterStudents(List<String> userIds, Map<String, User> users, String student) {
        if (student == null || student.trim().isEmpty()) {
            return userIds;
        }
        String needle = student.trim().toLowerCase();
        List<String> filtered = new ArrayList<>();
        for (String userId : userIds) {
            User user = users.get(userId);
            if (user == null) {
                continue;
            }
            String username = user.getUsername() == null ? "" : user.getUsername().toLowerCase();
            String email = user.getEmail() == null ? "" : user.getEmail().toLowerCase();
            if (username.startsWith(needle) || email.startsWith(needle) || username.contains(needle)) {
                filtered.add(userId);
            }
        }
        return filtered;
    }

    private static String displayProblemHeader(Map<String, Object> problem) {
        Object numericId = problem.get("numericId");
        return numericId == null ? String.valueOf(problem.get("problemId")) : String.valueOf(numericId);
    }
}
