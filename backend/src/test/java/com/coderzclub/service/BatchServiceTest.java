package com.coderzclub.service;

import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchServiceTest {
    @Test
    void createRequiresName() {
        BatchService service = new BatchService(mock(MongoTemplate.class));
        assertThrows(IllegalArgumentException.class, () -> service.create("  ", null, "admin"));
    }

    @Test
    void createPersistsTrimmedName() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.save(any(Batch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Batch batch = new BatchService(mongo).create("  DSA October  ", "desc", "admin-1");
        assertEquals("DSA October", batch.getName());
        assertEquals("desc", batch.getDescription());
        assertTrue(batch.isActive());
        assertEquals("admin-1", batch.getCreatedBy());
    }

    @Test
    void addMemberRejectsUnknownUser() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Batch batch = new Batch();
        batch.setId("b1");
        when(mongo.findById("b1", Batch.class)).thenReturn(batch);
        when(mongo.findById("missing", User.class)).thenReturn(null);
        BatchService service = new BatchService(mongo);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.addMembers("b1", java.util.List.of("missing")));
        assertTrue(ex.getMessage().contains("User not found"));
        verify(mongo, never()).save(any(BatchMember.class));
    }

    @Test
    void duplicateMemberAddIsIdempotent() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Batch batch = new Batch();
        batch.setId("b1");
        User user = new User();
        user.setId("u1");
        BatchMember existing = new BatchMember();
        existing.setBatchId("b1");
        existing.setUserId("u1");
        when(mongo.findById("b1", Batch.class)).thenReturn(batch);
        when(mongo.findById("u1", User.class)).thenReturn(user);
        when(mongo.save(any(BatchMember.class))).thenThrow(new DuplicateKeyException("dup"));
        when(mongo.findOne(any(Query.class), eq(BatchMember.class))).thenReturn(existing);
        BatchService service = new BatchService(mongo);
        assertEquals("u1", service.addMembers("b1", java.util.List.of("u1")).get(0).getUserId());
    }

    @Test
    void duplicateKeyIsRethrownWhenExistingMemberCannotBeLoaded() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Batch batch = new Batch();
        batch.setId("b1");
        User user = new User();
        user.setId("u1");
        when(mongo.findById("b1", Batch.class)).thenReturn(batch);
        when(mongo.findById("u1", User.class)).thenReturn(user);
        DuplicateKeyException duplicate = new DuplicateKeyException("unrelated unique index");
        when(mongo.save(any(BatchMember.class))).thenThrow(duplicate);
        when(mongo.findOne(any(Query.class), eq(BatchMember.class))).thenReturn(null);
        assertThrows(DuplicateKeyException.class,
            () -> new BatchService(mongo).addMembers("b1", java.util.List.of("u1")));
    }

    @Test
    void unknownBatchIsNotFound() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById("missing", Batch.class)).thenReturn(null);
        assertThrows(ResponseStatusException.class, () -> new BatchService(mongo).requireBatch("missing"));
    }

    @Test
    void pageSizeIsCapped() {
        assertEquals(50, BatchService.boundSize(1000));
        assertEquals(20, BatchService.boundSize(0));
    }

    @Test
    void invalidProblemIsRejectedAndDuplicateAssignmentIsIdempotent() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Batch batch = new Batch();
        batch.setId("b1");
        when(mongo.findById("b1", Batch.class)).thenReturn(batch);
        when(mongo.findById("missing", com.coderzclub.model.Problem.class)).thenReturn(null);
        when(mongo.findOne(any(Query.class), eq(com.coderzclub.model.Problem.class))).thenReturn(null);
        BatchService service = new BatchService(mongo);
        assertThrows(IllegalArgumentException.class, () -> service.addAssignments("b1", java.util.List.of("missing")));

        com.coderzclub.model.Problem problem = new com.coderzclub.model.Problem();
        problem.setId("mongo-26");
        problem.setNumericId(26);
        when(mongo.findById("mongo-26", com.coderzclub.model.Problem.class)).thenReturn(problem);
        when(mongo.count(any(Query.class), eq(com.coderzclub.model.BatchAssignment.class))).thenReturn(0L);
        when(mongo.exists(any(Query.class), eq(com.coderzclub.model.BatchAssignment.class))).thenReturn(false);
        com.coderzclub.model.BatchAssignment existing = new com.coderzclub.model.BatchAssignment();
        existing.setProblemId("mongo-26");
        when(mongo.save(any(com.coderzclub.model.BatchAssignment.class))).thenThrow(new DuplicateKeyException("dup"));
        when(mongo.findOne(any(Query.class), eq(com.coderzclub.model.BatchAssignment.class))).thenReturn(existing);
        assertEquals("mongo-26", service.addAssignments("b1", java.util.List.of("mongo-26")).get(0).getProblemId());
    }

    @Test
    void archivedBatchBlocksMembershipAndAssignmentChanges() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Batch batch = new Batch();
        batch.setId("b1");
        batch.setActive(false);
        when(mongo.findById("b1", Batch.class)).thenReturn(batch);
        BatchService service = new BatchService(mongo);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.addMembers("b1", java.util.List.of("u1")));
        assertTrue(ex.getMessage().contains("Archived"));
        assertThrows(IllegalArgumentException.class, () -> service.removeMember("b1", "u1"));
        assertThrows(IllegalArgumentException.class, () -> service.addAssignments("b1", java.util.List.of("p1")));
        assertThrows(IllegalArgumentException.class, () -> service.removeAssignment("b1", "p1"));
        service.requireBatch("b1");
    }

    @Test
    void removingMemberDoesNotDeleteSubmissions() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Batch batch = new Batch();
        batch.setId("b1");
        batch.setActive(true);
        when(mongo.findById("b1", Batch.class)).thenReturn(batch);
        new BatchService(mongo).removeMember("b1", "u1");
        verify(mongo).remove(any(Query.class), eq(BatchMember.class));
        verify(mongo, never()).remove(any(Query.class), eq(com.coderzclub.model.Submission.class));
        verify(mongo, never()).remove(any(), eq(User.class));
    }
}
