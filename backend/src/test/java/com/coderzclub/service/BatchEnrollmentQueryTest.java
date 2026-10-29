package com.coderzclub.service;

import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchAssignment;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Problem;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class BatchEnrollmentQueryTest {

    @Test
    void enrolledListLoadsMembershipsBatchesAssignmentsAndProblemsOnce() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        BatchService service = new BatchService(mongo);
        BatchMember first = membership("b1", "student");
        BatchMember second = membership("b2", "student");
        BatchMember third = membership("b3", "student");
        when(mongo.count(any(Query.class), eq(BatchMember.class))).thenReturn(3L);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(first, second, third));
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of(batch("b1"), batch("b2"), batch("b3")));
        when(mongo.find(any(Query.class), eq(BatchAssignment.class))).thenReturn(List.of(
            assignment("b1", "p1"),
            assignment("b2", "p2"),
            assignment("b3", "p3")
        ));
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(problem("p1"), problem("p2"), problem("p3")));

        Map<String, Object> page = service.listEnrolledForUser("student", 0, 20);

        assertEquals(3, ((List<?>) page.get("items")).size());
        verify(mongo, times(1)).count(any(Query.class), eq(BatchMember.class));
        verify(mongo, times(1)).find(any(Query.class), eq(BatchMember.class));
        verify(mongo, times(1)).find(any(Query.class), eq(Batch.class));
        verify(mongo, times(1)).find(any(Query.class), eq(BatchAssignment.class));
        ArgumentCaptor<Query> problemQuery = ArgumentCaptor.forClass(Query.class);
        verify(mongo, times(1)).find(problemQuery.capture(), eq(Problem.class));
        assertFalse(problemQuery.getValue().getFieldsObject().containsKey("hiddenTestCases"));
        assertEquals(1, problemQuery.getValue().getFieldsObject().get("title"));
    }

    private static BatchMember membership(String batchId, String userId) {
        BatchMember member = new BatchMember();
        member.setBatchId(batchId);
        member.setUserId(userId);
        return member;
    }

    private static Batch batch(String id) {
        Batch batch = new Batch();
        batch.setId(id);
        batch.setName("Batch " + id);
        batch.setActive(true);
        return batch;
    }

    private static BatchAssignment assignment(String batchId, String problemId) {
        BatchAssignment assignment = new BatchAssignment();
        assignment.setBatchId(batchId);
        assignment.setProblemId(problemId);
        return assignment;
    }

    private static Problem problem(String id) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setTitle("Problem " + id);
        problem.setNumericId(1);
        return problem;
    }
}
