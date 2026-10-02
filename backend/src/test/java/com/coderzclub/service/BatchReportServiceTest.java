package com.coderzclub.service;

import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchAssignment;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
import com.coderzclub.model.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchReportServiceTest {
    @Test
    void classifierSemanticsMatchPhase3() {
        assertEquals("SOLVED", BatchReportService.cellStatus(Set.of(SubmissionAttemptClassifier.Kind.ACCEPTED)));
        assertEquals("ATTEMPTED", BatchReportService.cellStatus(Set.of(SubmissionAttemptClassifier.Kind.STUDENT_VERDICT)));
        assertEquals("UNSOLVED", BatchReportService.cellStatus(Set.of(SubmissionAttemptClassifier.Kind.INFRASTRUCTURE)));
        assertEquals("UNSOLVED", BatchReportService.cellStatus(Set.of(SubmissionAttemptClassifier.Kind.IGNORED)));
        assertEquals("UNSOLVED", BatchReportService.cellStatus(Set.of()));
        assertEquals("SOLVED", BatchReportService.cellStatus(Set.of(
            SubmissionAttemptClassifier.Kind.STUDENT_VERDICT,
            SubmissionAttemptClassifier.Kind.ACCEPTED)));
        assertEquals("ATTEMPTED", BatchReportService.cellStatus(Set.of(
            SubmissionAttemptClassifier.classify("TIME_LIMIT_EXCEEDED", null))));
        assertEquals("ATTEMPTED", BatchReportService.cellStatus(Set.of(
            SubmissionAttemptClassifier.classify("COMPILATION_ERROR", null))));
        assertEquals("UNSOLVED", BatchReportService.cellStatus(Set.of(
            SubmissionAttemptClassifier.classify("INTERNAL_ERROR", null))));
        assertEquals("UNSOLVED", BatchReportService.cellStatus(Set.of(
            SubmissionAttemptClassifier.classify("QUEUED", null))));
        assertEquals("ATTEMPTED", BatchReportService.cellStatus(Set.of(
            SubmissionAttemptClassifier.classify("INTERNAL_ERROR", null),
            SubmissionAttemptClassifier.classify("WRONG_ANSWER", null))));
        assertEquals(SubmissionAttemptClassifier.problemStatus(Set.of(SubmissionAttemptClassifier.Kind.ACCEPTED)),
            BatchReportService.cellStatus(Set.of(SubmissionAttemptClassifier.Kind.ACCEPTED)));
    }

    @Test
    void reportUsesBulkSubmissionQueryAndCorrectDenominators() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        BatchService batchService = mock(BatchService.class);
        Batch batch = new Batch();
        batch.setId("b1");
        batch.setName("DSA October Batch");
        batch.setActive(true);
        when(batchService.requireBatch("b1")).thenReturn(batch);

        BatchMember alice = member("u-alice");
        BatchMember bob = member("u-bob");
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(alice, bob));
        when(mongo.find(any(Query.class), eq(BatchAssignment.class))).thenReturn(List.of(
            assignment("mongo-26"), assignment("mongo-27"), assignment("mongo-28")));

        Problem p26 = problem("mongo-26", 26, "Two Sum");
        Problem p27 = problem("mongo-27", 27, "Arrays");
        Problem p28 = problem("mongo-28", 28, "Graph");
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(p26, p27, p28));
        when(mongo.find(any(Query.class), eq(User.class))).thenReturn(List.of(
            user("u-alice", "alice"), user("u-bob", "bob")));
        when(mongo.find(any(Query.class), eq(Submission.class))).thenReturn(List.of(
            submission("u-alice", "mongo-26", "WRONG_ANSWER"),
            submission("u-alice", "mongo-26", "ACCEPTED"),
            submission("u-alice", "26", "INTERNAL_ERROR"),
            submission("u-alice", "mongo-27", "WRONG_ANSWER"),
            submission("u-alice", "mongo-28", "QUEUED"),
            submission("u-bob", "mongo-26", "COMPLETED"),
            submission("u-bob", "mongo-27", "ACCEPTED")
        ));

        BatchReportService service = new BatchReportService(mongo, batchService);
        Map<String, Object> report = service.report("b1", 0, 20, null);

        verify(mongo, times(1)).find(any(Query.class), eq(Submission.class));
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(queryCaptor.capture(), eq(Submission.class));
        assertTrue(queryCaptor.getValue().getQueryObject().toJson().contains("$in"));

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        assertEquals(2, summary.get("studentCount"));
        assertEquals(3, summary.get("assignedProblemCount"));
        assertEquals(6, summary.get("totalCells"));
        assertEquals(2, summary.get("solvedCells"));
        assertEquals(1, summary.get("attemptedCells"));
        assertEquals(3, summary.get("unsolvedCells"));
        assertEquals("33.33", summary.get("completionPercent"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> students = (List<Map<String, Object>>) report.get("students");
        Map<String, Object> aliceRow = students.stream().filter(row -> "alice".equals(row.get("username"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> aliceCells = (List<Map<String, Object>>) aliceRow.get("cells");
        assertEquals("SOLVED", aliceCells.get(0).get("status"));
        assertEquals("ATTEMPTED", aliceCells.get(1).get("status"));
        assertEquals("UNSOLVED", aliceCells.get(2).get("status"));
        assertEquals(26, aliceCells.get(0).get("numericId"));
        org.bson.Document query = queryCaptor.getValue().getQueryObject();
        List<?> userIn = inValues(query, "userId");
        List<?> problemIn = inValues(query, "problemId");
        assertEquals(2, userIn.size());
        assertTrue(problemIn.size() >= 3 && problemIn.size() <= 6);
    }

    @Test
    void summaryUsesAllMembersNotTheCurrentPage() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        BatchService batchService = mock(BatchService.class);
        Batch batch = new Batch();
        batch.setId("b1");
        when(batchService.requireBatch("b1")).thenReturn(batch);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(
            member("u1"), member("u2"), member("u3")));
        when(mongo.find(any(Query.class), eq(BatchAssignment.class))).thenReturn(List.of(
            assignment("p1"), assignment("p2")));
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(
            problem("p1", 1, "A"), problem("p2", 2, "B")));
        when(mongo.find(any(Query.class), eq(User.class))).thenReturn(List.of(
            user("u1", "a"), user("u2", "b"), user("u3", "c")));
        when(mongo.find(any(Query.class), eq(Submission.class))).thenReturn(List.of());
        Map<String, Object> report = new BatchReportService(mongo, batchService).report("b1", 0, 1, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        assertEquals(3, summary.get("studentCount"));
        assertEquals(2, summary.get("assignedProblemCount"));
        assertEquals(6, summary.get("totalCells"));
        assertEquals(0, summary.get("solvedCells"));
        assertEquals(6, summary.get("unsolvedCells"));
        assertEquals("0.00", summary.get("completionPercent"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> students = (List<Map<String, Object>>) report.get("students");
        assertEquals(1, students.size());
        assertEquals(3L, ((Number) report.get("totalPages")).longValue());
        assertEquals(3L, ((Number) report.get("totalItems")).longValue());
    }

    @Test
    void oldStringIdAndObjectIdProblemsMatchPhase3Aliases() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        BatchService batchService = mock(BatchService.class);
        Batch batch = new Batch();
        batch.setId("b1");
        when(batchService.requireBatch("b1")).thenReturn(batch);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("u1")));
        when(mongo.find(any(Query.class), eq(BatchAssignment.class))).thenReturn(List.of(
            assignment("23"), assignment("6aa407db2f4d12188ea742e2")));
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(
            problem("23", 23, "Legacy"),
            problem("6aa407db2f4d12188ea742e2", 26, "Newer")));
        when(mongo.find(any(Query.class), eq(User.class))).thenReturn(List.of(user("u1", "alice")));
        when(mongo.find(any(Query.class), eq(Submission.class))).thenReturn(List.of(
            submission("u1", "23", "ACCEPTED"),
            submission("u1", "26", "WRONG_ANSWER")));
        Map<String, Object> report = new BatchReportService(mongo, batchService).report("b1", 0, 20, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> students = (List<Map<String, Object>>) report.get("students");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cells = (List<Map<String, Object>>) students.get(0).get("cells");
        assertEquals("SOLVED", cells.get(0).get("status"));
        assertEquals("ATTEMPTED", cells.get(1).get("status"));
        assertEquals(23, cells.get(0).get("numericId"));
        assertEquals(26, cells.get(1).get("numericId"));
    }

    @Test
    void csvRejectsBatchesLargerThanExportCap() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        BatchService batchService = mock(BatchService.class);
        Batch batch = new Batch();
        batch.setId("b1");
        when(batchService.requireBatch("b1")).thenReturn(batch);
        List<BatchMember> members = new java.util.ArrayList<>();
        for (int i = 0; i < 501; i++) {
            members.add(member("u" + i));
        }
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(members);
        when(mongo.find(any(Query.class), eq(BatchAssignment.class))).thenReturn(List.of(assignment("p1")));
        org.springframework.web.server.ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> new BatchReportService(mongo, batchService).csv("b1", null));
        assertEquals(400, ex.getStatusCode().value());
        verify(mongo, times(0)).find(any(Query.class), eq(Submission.class));
    }

    @Test
    void csvEscapesFormulasAndUsesSameStatuses() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        BatchService batchService = mock(BatchService.class);
        Batch batch = new Batch();
        batch.setId("b1");
        batch.setName("Batch");
        when(batchService.requireBatch("b1")).thenReturn(batch);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("u1")));
        when(mongo.find(any(Query.class), eq(BatchAssignment.class))).thenReturn(List.of(assignment("p1")));
        Problem problem = problem("p1", 101, "Two Sum");
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(problem));
        User user = user("u1", "=cmd");
        user.setEmail("a@b.c");
        when(mongo.find(any(Query.class), eq(User.class))).thenReturn(List.of(user));
        when(mongo.find(any(Query.class), eq(Submission.class))).thenReturn(List.of(
            submission("u1", "p1", "ACCEPTED")));

        String csv = new BatchReportService(mongo, batchService).csv("b1", null);
        assertTrue(csv.startsWith("student,101,solved,attempted,unsolved,completion"));
        assertTrue(csv.contains("'=cmd,SOLVED,1,0,0,100.00"));
    }

    private static List<?> inValues(org.bson.Document query, String field) {
        Object direct = query.get(field);
        if (direct instanceof org.bson.Document document && document.get("$in") instanceof List<?> list) {
            return list;
        }
        Object and = query.get("$and");
        if (and instanceof List<?> parts) {
            for (Object part : parts) {
                if (part instanceof org.bson.Document document
                    && document.get(field) instanceof org.bson.Document fieldQuery
                    && fieldQuery.get("$in") instanceof List<?> list) {
                    return list;
                }
            }
        }
        return List.of();
    }

    private static BatchMember member(String userId) {
        BatchMember member = new BatchMember();
        member.setUserId(userId);
        return member;
    }

    private static BatchAssignment assignment(String problemId) {
        BatchAssignment assignment = new BatchAssignment();
        assignment.setProblemId(problemId);
        return assignment;
    }

    private static Problem problem(String id, int numericId, String title) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setNumericId(numericId);
        problem.setTitle(title);
        return problem;
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static Submission submission(String userId, String problemId, String result) {
        Submission submission = new Submission();
        submission.setUserId(userId);
        submission.setProblemId(problemId);
        submission.setResult(result);
        submission.setCreatedAt(new Date());
        return submission;
    }
}
