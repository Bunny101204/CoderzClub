package com.coderzclub.service;

import com.coderzclub.config.WorkerProperties;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.repository.SubmissionJobRepository;
import com.mongodb.ClientSessionOptions;
import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubmissionJobTransactionTest {
    private SubmissionJobService service;
    private SubmissionJobService target;
    private SubmissionJobRepository jobs;
    private SubmissionOutboxService outbox;
    private SubmissionJobEventService events;
    private MongoTemplate mongo;
    private MongoTransactionManager manager;
    private ClientSession session;

    @BeforeEach
    void setup() {
        MongoDatabaseFactory factory = mock(MongoDatabaseFactory.class);
        session = mock(ClientSession.class);
        AtomicBoolean active = new AtomicBoolean();
        when(factory.getSession(any(ClientSessionOptions.class))).thenReturn(session);
        when(session.hasActiveTransaction()).thenAnswer(call -> active.get());
        doAnswer(call -> { active.set(true); return null; }).when(session).startTransaction();
        doAnswer(call -> { active.set(false); return null; }).when(session).commitTransaction();
        doAnswer(call -> { active.set(false); return null; }).when(session).abortTransaction();
        manager = new MongoTransactionManager(factory);
        mongo = mock(MongoTemplate.class);
        when(mongo.executeCommand(anyString())).thenAnswer(call -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new MongoException(263, "OperationNotSupportedInTransaction: hello cannot start a transaction");
            }
            return new Document("setName", "rs0");
        });
        jobs = mock(SubmissionJobRepository.class);
        when(jobs.save(any())).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            SubmissionJob job = call.getArgument(0);
            job.setId("saved-job");
            return job;
        });
        outbox = mock(SubmissionOutboxService.class);
        events = mock(SubmissionJobEventService.class);
        target = new SubmissionJobService();
        ReflectionTestUtils.setField(target, "jobRepository", jobs);
        ReflectionTestUtils.setField(target, "outboxService", outbox);
        ReflectionTestUtils.setField(target, "eventService", events);
        ReflectionTestUtils.setField(target, "workerProperties", new WorkerProperties());
        ReflectionTestUtils.setField(target, "mongoTemplate", mongo);
        ReflectionTestUtils.setField(target, "transactionManager", manager);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        service = (SubmissionJobService) proxy.getProxy();
    }

    @Test
    void coldStartEveryUnkeyedOverloadProbesOutsideTransactionAndCommitsBeforePublishing() {
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(events).publish(any(), any());
        service.createJob("u", "p", "code", "Java", 62, "v1", 1, ExecutionMode.STANDARD_PER_CASE);
        ReflectionTestUtils.setField(target, "replicaSet", null);
        service.createJob("u", "p", "code", "Java", 62, "v1", 1, ExecutionMode.STANDARD_PER_CASE, 10);
        ReflectionTestUtils.setField(target, "replicaSet", null);
        service.createJob("u", "p", "code", "Java", 62, "v1", 1, ExecutionMode.STANDARD_PER_CASE, 10, "c", null);
        ReflectionTestUtils.setField(target, "replicaSet", null);
        service.createJob("u", "p", "code", "Java", 62, "v1", 1, ExecutionMode.STANDARD_PER_CASE, 10, "c", null, "snapshot");
        verify(mongo, times(4)).executeCommand(anyString());
        verify(session, times(4)).commitTransaction();
        verify(session, never()).abortTransaction();
    }

    @Test
    void outboxFailureAbortsJobAndDoesNotPublish() {
        when(outbox.createJobEvent(anyString())).thenThrow(new MongoException(263, "unsupported operation"));
        assertThrows(MongoException.class, this::keyed);
        verify(session).abortTransaction();
        verify(session, never()).commitTransaction();
        verifyNoInteractions(events);
        verify(jobs).save(any());
    }

    @Test
    void transientOutboxFailureRetriesInFreshTransaction() {
        MongoException conflict = new MongoException(112, "conflict");
        conflict.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        when(outbox.createJobEvent(anyString())).thenThrow(conflict).thenReturn(null);
        assertEquals("saved-job", keyed().getId());
        var order = inOrder(session);
        order.verify(session).startTransaction();
        order.verify(session).abortTransaction();
        order.verify(session).startTransaction();
        order.verify(session).commitTransaction();
        verify(events).publish(any(), eq(SubmissionJob.JobStatus.QUEUED));
    }

    @Test
    void exhaustedRetriesAbortEveryAttempt() {
        MongoException conflict = new MongoException(112, "conflict");
        conflict.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        doThrow(conflict).when(jobs).save(any());
        assertThrows(MongoException.class, this::keyed);
        verify(session, times(3)).abortTransaction();
        verify(session, never()).commitTransaction();
        verifyNoInteractions(events, outbox);
    }

    @Test
    void duplicateJobRaceReplaysOnlyAfterRollback() {
        SubmissionJob winner = new SubmissionJob();
        winner.setId("winner");
        when(jobs.findFirstByUserIdAndIdempotencyScopeAndIdempotencyKey("u", "submission-job", "attempt001"))
            .thenReturn(Optional.empty()).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                return Optional.of(winner);
            });
        doThrow(new org.springframework.dao.DuplicateKeyException("race")).when(jobs).save(any());
        assertSame(winner, keyed());
        verify(session).abortTransaction();
        verify(outbox).ensureJobEvent("winner");
        verify(outbox, never()).createJobEvent(anyString());
    }

    @Test
    void unknownCommitResultDoesNotBlindlyCreateAnotherJobOrPublishSuccess() {
        MongoException uncertain = new MongoException(91, "commit response lost");
        uncertain.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        doThrow(uncertain).when(session).commitTransaction();
        assertThrows(RuntimeException.class, this::keyed);
        verify(jobs).save(any());
        verify(session).commitTransaction();
        verifyNoInteractions(events);
    }

    @Test
    void probeSuspendsAnExistingCallerTransaction() {
        new TransactionTemplate(manager).execute(status -> {
            assertEquals("saved-job", keyed().getId());
            return null;
        });
        verify(mongo).executeCommand(anyString());
        verify(session, times(2)).commitTransaction();
    }

    private SubmissionJob keyed() {
        return service.createJob("u", "p", "code", "Java", 62, "v1", 1,
            ExecutionMode.STANDARD_PER_CASE, null, null, null, null, "submission-job", "attempt001");
    }
}
