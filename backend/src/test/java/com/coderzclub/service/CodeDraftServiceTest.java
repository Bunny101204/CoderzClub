package com.coderzclub.service;

import com.coderzclub.config.SubmissionLimitsConfig;
import com.coderzclub.dto.CodeDraftResponse;
import com.coderzclub.model.CodeDraft;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.User;
import com.coderzclub.repository.ProblemRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeDraftServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");

    @Mock private MongoTemplate mongoTemplate;
    @Mock private ProblemRepository problemRepository;
    @Mock private ProblemAccessService problemAccessService;
    @Mock private ContestService contestService;
    @Mock private DraftMetrics metrics;

    private CodeDraftService service;

    @BeforeEach
    void setUp() {
        service = new CodeDraftService(
            mongoTemplate,
            problemRepository,
            problemAccessService,
            contestService,
            new SubmissionValidator(),
            new SubmissionLimitsConfig(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            metrics
        );
    }

    @Test
    void newPracticeDraftStartsAtRevisionOne() {
        allowPractice("p1", problem("p1"));
        when(mongoTemplate.insert(any(CodeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CodeDraftResponse created = service.savePractice(user("user-a"), "p1", 62, "class A {}", null);

        assertEquals("class A {}", created.getCode());
        assertEquals(1, created.getRevision());
        assertEquals(NOW, created.getUpdatedAt());
        ArgumentCaptor<CodeDraft> captor = ArgumentCaptor.forClass(CodeDraft.class);
        verify(mongoTemplate).insert(captor.capture());
        assertEquals("user-a", captor.getValue().getUserId());
        assertEquals(CodeDraft.ScopeType.PRACTICE, captor.getValue().getScopeType());
        assertEquals("practice", captor.getValue().getScopeKey());
        assertNull(captor.getValue().getContestId());
        assertEquals("p1", captor.getValue().getProblemId());
        assertEquals(1, captor.getValue().getRevision());
        verify(metrics).sync();
        verify(mongoTemplate, never()).findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(CodeDraft.class)
        );
    }

    @Test
    void zeroExpectedRevisionCreatesAndEmptyCodeIsAllowed() {
        allowPractice("p1", problem("p1"));
        when(mongoTemplate.insert(any(CodeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CodeDraftResponse created = service.savePractice(user("user-a"), "p1", 71, "", 0L);

        assertEquals("", created.getCode());
        assertEquals(1, created.getRevision());
    }

    @Test
    void numericProblemIdIsStoredAsCanonicalId() {
        Problem problem = problem("mongo-problem");
        when(problemRepository.findById("12")).thenReturn(Optional.empty());
        when(problemRepository.findByNumericId(12)).thenReturn(Optional.of(problem));
        when(mongoTemplate.insert(any(CodeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.savePractice(user("user-a"), "12", 62, "code", null);

        ArgumentCaptor<CodeDraft> captor = ArgumentCaptor.forClass(CodeDraft.class);
        verify(mongoTemplate).insert(captor.capture());
        assertEquals("mongo-problem", captor.getValue().getProblemId());
    }

    @Test
    void matchingRevisionUpdatesAtomicallyAndIncrements() {
        allowPractice("p1", problem("p1"));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(CodeDraft.class)))
            .thenAnswer(invocation -> {
                Update update = invocation.getArgument(1);
                Document set = (Document) update.getUpdateObject().get("$set");
                CodeDraft saved = new CodeDraft();
                saved.setCode(set.getString("code"));
                saved.setRevision(set.getLong("revision"));
                saved.setUpdatedAt(set.get("updatedAt", Instant.class));
                return saved;
            });

        CodeDraftResponse updated = service.savePractice(user("user-a"), "p1", 62, "next", 4L);

        assertEquals("next", updated.getCode());
        assertEquals(5, updated.getRevision());
        assertEquals(NOW, updated.getUpdatedAt());
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(query.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(CodeDraft.class));
        Document criteria = query.getValue().getQueryObject();
        assertEquals("user-a", criteria.getString("userId"));
        assertEquals("practice", criteria.getString("scopeKey"));
        assertEquals("p1", criteria.getString("problemId"));
        assertEquals(62, criteria.getInteger("languageId"));
        assertEquals(4L, criteria.getLong("revision"));
        verify(mongoTemplate, never()).insert(any(CodeDraft.class));
        verify(metrics).sync();
    }

    @Test
    void staleRevisionReturnsConflictAndLeavesCloudContentUnchanged() {
        allowPractice("p1", problem("p1"));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(CodeDraft.class)))
            .thenReturn(null);
        CodeDraft current = stored("cloud", 7);
        when(mongoTemplate.findOne(any(Query.class), eq(CodeDraft.class))).thenReturn(current);

        DraftConflictException conflict = assertThrows(DraftConflictException.class,
            () -> service.savePractice(user("user-a"), "p1", 62, "stale", 4L));

        assertEquals("cloud", conflict.getCode());
        assertEquals(7, conflict.getRevision());
        assertEquals(NOW, conflict.getUpdatedAt());
        verify(mongoTemplate, never()).insert(any(CodeDraft.class));
        verify(metrics).conflict();
        verify(metrics, never()).sync();
    }

    @Test
    void duplicateCreateIsAConflict() {
        allowPractice("p1", problem("p1"));
        when(mongoTemplate.insert(any(CodeDraft.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(mongoTemplate.findOne(any(Query.class), eq(CodeDraft.class))).thenReturn(stored("winner", 1));

        DraftConflictException conflict = assertThrows(DraftConflictException.class,
            () -> service.savePractice(user("user-a"), "p1", 62, "loser", 0L));

        assertEquals("winner", conflict.getCode());
        assertEquals(1, conflict.getRevision());
        verify(metrics).conflict();
    }

    @Test
    void onlyOneWriterCanCommitTheSameRevision() throws Exception {
        allowPractice("p1", problem("p1"));
        AtomicInteger commits = new AtomicInteger();
        AtomicReference<String> winner = new AtomicReference<>();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(CodeDraft.class)))
            .thenAnswer(invocation -> {
                Query query = invocation.getArgument(0);
                assertEquals(4L, query.getQueryObject().getLong("revision"));
                Update update = invocation.getArgument(1);
                Document set = (Document) update.getUpdateObject().get("$set");
                assertEquals(5L, set.getLong("revision"));
                if (commits.incrementAndGet() == 1) {
                    winner.set(set.getString("code"));
                    CodeDraft saved = new CodeDraft();
                    saved.setCode(winner.get());
                    saved.setRevision(5);
                    saved.setUpdatedAt(NOW);
                    return saved;
                }
                return null;
            });
        when(mongoTemplate.findOne(any(Query.class), eq(CodeDraft.class))).thenAnswer(invocation -> {
            CodeDraft current = stored(winner.get(), 5);
            return current;
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(() -> callSave("alpha"));
            Future<Object> second = pool.submit(() -> callSave("beta"));
            List<Object> results = List.of(first.get(), second.get());
            long successes = results.stream().filter(CodeDraftResponse.class::isInstance).count();
            long conflicts = results.stream().filter(DraftConflictException.class::isInstance).count();
            assertEquals(1, successes);
            assertEquals(1, conflicts);
            assertEquals(2, commits.get());
            DraftConflictException conflict = (DraftConflictException) results.stream()
                .filter(DraftConflictException.class::isInstance)
                .findFirst()
                .orElseThrow();
            assertEquals(winner.get(), conflict.getCode());
            assertEquals(5, conflict.getRevision());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void retryWithRevisionFromConflictSucceeds() {
        allowPractice("p1", problem("p1"));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(CodeDraft.class)))
            .thenReturn(null)
            .thenAnswer(invocation -> {
                Update update = invocation.getArgument(1);
                Document set = (Document) update.getUpdateObject().get("$set");
                CodeDraft saved = new CodeDraft();
                saved.setCode(set.getString("code"));
                saved.setRevision(set.getLong("revision"));
                saved.setUpdatedAt(NOW);
                return saved;
            });
        when(mongoTemplate.findOne(any(Query.class), eq(CodeDraft.class))).thenReturn(stored("cloud", 8));

        DraftConflictException conflict = assertThrows(DraftConflictException.class,
            () -> service.savePractice(user("user-a"), "p1", 62, "mine", 3L));
        CodeDraftResponse retried = service.savePractice(user("user-a"), "p1", 62, "mine", conflict.getRevision());

        assertEquals(8, conflict.getRevision());
        assertEquals("mine", retried.getCode());
        assertEquals(9, retried.getRevision());
    }

    @Test
    void practiceAndContestScopesStayIsolated() {
        allowPractice("p1", problem("p1"));
        when(contestService.requireContestDraftTarget(user("user-a"), "contest-x", "p1"))
            .thenReturn(new ContestService.ContestDraftTarget("contest-x", "p1"));
        when(contestService.requireContestDraftTarget(user("user-a"), "contest-y", "p1"))
            .thenReturn(new ContestService.ContestDraftTarget("contest-y", "p1"));
        when(mongoTemplate.insert(any(CodeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.savePractice(user("user-a"), "p1", 62, "practice solution", null);
        service.saveContest(user("user-a"), "contest-x", "p1", 62, "contest solution", null);
        service.saveContest(user("user-a"), "contest-y", "p1", 62, "different contest solution", null);

        ArgumentCaptor<CodeDraft> captor = ArgumentCaptor.forClass(CodeDraft.class);
        verify(mongoTemplate, org.mockito.Mockito.times(3)).insert(captor.capture());
        List<CodeDraft> saved = captor.getAllValues();
        assertEquals("practice", saved.get(0).getScopeKey());
        assertEquals("practice solution", saved.get(0).getCode());
        assertEquals("contest:contest-x", saved.get(1).getScopeKey());
        assertEquals("contest-x", saved.get(1).getContestId());
        assertEquals("contest solution", saved.get(1).getCode());
        assertEquals("contest:contest-y", saved.get(2).getScopeKey());
        assertEquals("different contest solution", saved.get(2).getCode());
    }

    @Test
    void loadUsesTheAuthenticatedUserIdentity() {
        allowPractice("p1", problem("p1"));
        when(mongoTemplate.findOne(any(Query.class), eq(CodeDraft.class))).thenReturn(null);

        CodeDraftResponse missing = service.loadPractice(user("user-a"), "p1", 62);

        assertNull(missing.getCode());
        assertEquals(0, missing.getRevision());
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(query.capture(), eq(CodeDraft.class));
        assertEquals("user-a", query.getValue().getQueryObject().getString("userId"));
        verify(metrics).load();
    }

    @Test
    void contestOnlyProblemIsNotReadableThroughPracticeDrafts() {
        Problem hidden = problem("secret");
        hidden.setAvailability(ProblemAvailability.CONTEST_ONLY);
        allowPractice("secret", hidden);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found"))
            .when(problemAccessService).requirePracticeExecute(user("user-a"), hidden);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.loadPractice(user("user-a"), "secret", 62));

        assertEquals(404, ex.getStatusCode().value());
        verify(mongoTemplate, never()).findOne(any(Query.class), eq(CodeDraft.class));
        verify(mongoTemplate, never()).insert(any(CodeDraft.class));
    }

    @Test
    void contestDraftRejectsWhenContestAccessRejects() {
        when(contestService.requireContestDraftTarget(user("user-a"), "upcoming", "p1"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.saveContest(user("user-a"), "upcoming", "p1", 62, "code", 0L));

        assertEquals(404, ex.getStatusCode().value());
        verify(mongoTemplate, never()).insert(any(CodeDraft.class));
    }

    @Test
    void languageIdUsesTheSubmissionValidator() {
        allowPractice("p1", problem("p1"));

        assertThrows(IllegalArgumentException.class,
            () -> service.savePractice(user("user-a"), "p1", 0, "code", 0L));
        assertThrows(IllegalArgumentException.class,
            () -> service.savePractice(user("user-a"), "p1", 92, "code", 0L));
        assertThrows(IllegalArgumentException.class,
            () -> service.loadPractice(user("user-a"), "p1", 99));

        verify(mongoTemplate, never()).insert(any(CodeDraft.class));
        verify(mongoTemplate, never()).findOne(any(Query.class), eq(CodeDraft.class));
    }

    @Test
    void nullCodeAndOversizedCodeAreRejected() {
        allowPractice("p1", problem("p1"));

        assertThrows(IllegalArgumentException.class,
            () -> service.savePractice(user("user-a"), "p1", 62, null, 0L));
        assertThrows(IllegalArgumentException.class,
            () -> service.savePractice(user("user-a"), "p1", 62, "x".repeat(100001), 0L));
        assertThrows(IllegalArgumentException.class,
            () -> service.savePractice(user("user-a"), "p1", 99, "code", 0L));

        verify(mongoTemplate, never()).insert(any(CodeDraft.class));
    }

    @Test
    void mongoWriteFailureDoesNotExposeSource() {
        allowPractice("p1", problem("p1"));
        when(mongoTemplate.insert(any(CodeDraft.class)))
            .thenThrow(new DataAccessResourceFailureException("write failed for secret source"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.savePractice(user("user-a"), "p1", 62, "secret source", 0L));

        assertEquals(503, ex.getStatusCode().value());
        assertEquals("Draft sync is unavailable", ex.getReason());
        assertTrue(ex.getReason() == null || !ex.getReason().contains("secret source"));
        verify(metrics).syncFailure();
    }

    private Object callSave(String code) {
        try {
            return service.savePractice(user("user-a"), "p1", 62, code, 4L);
        } catch (DraftConflictException ex) {
            return ex;
        }
    }

    private void allowPractice(String rawId, Problem problem) {
        when(problemRepository.findById(rawId)).thenReturn(Optional.of(problem));
    }

    private static CodeDraft stored(String code, long revision) {
        CodeDraft draft = new CodeDraft();
        draft.setCode(code);
        draft.setRevision(revision);
        draft.setUpdatedAt(NOW);
        return draft;
    }

    private static Problem problem(String id) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setAvailability(ProblemAvailability.PUBLIC);
        return problem;
    }

    private static final Map<String, User> USERS = new HashMap<>();

    private static User user(String id) {
        return USERS.computeIfAbsent(id, key -> {
            User user = new User();
            user.setId(key);
            user.setUsername(key);
            user.setRole("USER");
            user.setAccountStatus("ACTIVE");
            return user;
        });
    }
}
