package com.coderzclub.service;

import com.coderzclub.config.ContestProperties;
import com.coderzclub.dto.ContestAdminResponse;
import com.coderzclub.dto.ContestProblemSummary;
import com.coderzclub.dto.ContestUserResponse;
import com.coderzclub.dto.ContestWriteRequest;
import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Contest;
import com.coderzclub.model.ContestAudienceType;
import com.coderzclub.model.ContestStatus;
import com.coderzclub.dto.ContestProblemDetailResponse;
import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.TestCase;
import com.coderzclub.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final Instant START = Instant.parse("2026-06-01T13:00:00Z");
    private static final Instant END = Instant.parse("2026-06-01T15:00:00Z");

    private MongoTemplate mongo;
    private ContestProperties properties;
    private ContestService service;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        properties = new ContestProperties();
        service = new ContestService(mongo, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        when(mongo.save(any(Contest.class))).thenAnswer(invocation -> {
            Contest contest = invocation.getArgument(0);
            if (contest.getId() == null) {
                contest.setId("c1");
            }
            return contest;
        });
    }

    @Test
    void clockDrivesStatusBoundaries() {
        Contest contest = publishedContest("c1", START, END);
        assertEquals(ContestStatus.UPCOMING, service.status(contest));
        contest.setPublished(false);
        assertEquals(ContestStatus.DRAFT, service.status(contest));

        ContestService atStart = new ContestService(mongo, properties, Clock.fixed(START, ZoneOffset.UTC));
        contest.setPublished(true);
        assertEquals(ContestStatus.LIVE, atStart.status(contest));
        assertEquals(ContestStatus.LIVE,
            new ContestService(mongo, properties, Clock.fixed(END.minusNanos(1), ZoneOffset.UTC)).status(publishedContest("c1", START, END)));
        assertEquals(ContestStatus.ENDED,
            new ContestService(mongo, properties, Clock.fixed(END, ZoneOffset.UTC)).status(publishedContest("c1", START, END)));
    }

    @Test
    void createRejectsEndBeforeOrEqualStart() {
        stubProblemAndBatch();
        ContestWriteRequest equal = write("Weekly", START, START, List.of("b1"), List.of("p1"));
        IllegalArgumentException equalEx = assertThrows(IllegalArgumentException.class, () -> service.create(equal, "admin-1"));
        assertTrue(equalEx.getMessage().contains("endAt must be after startAt"));

        ContestWriteRequest before = write("Weekly", START, START.minusSeconds(1), List.of("b1"), List.of("p1"));
        IllegalArgumentException beforeEx = assertThrows(IllegalArgumentException.class, () -> service.create(before, "admin-1"));
        assertTrue(beforeEx.getMessage().contains("endAt must be after startAt"));
        verify(mongo, never()).save(any(Contest.class));
    }

    @Test
    void publishRejectsEmptyProblems() {
        stubBatch();
        ContestWriteRequest body = write("Weekly", START, END, List.of("b1"), List.of());
        ContestAdminResponse draft = service.create(body, "admin-1");
        assertEquals(ContestStatus.DRAFT, draft.getStatus());
        when(mongo.findById("c1", Contest.class)).thenReturn(storedDraft(body));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.publish("c1"));
        assertTrue(ex.getMessage().contains("at least one problem"));
    }

    @Test
    void createAcceptsPublicAndContestOnlyProblems() {
        Problem published = problem("p1");
        published.setAvailability(com.coderzclub.model.ProblemAvailability.PUBLIC);
        Problem secret = problem("p2");
        secret.setAvailability(com.coderzclub.model.ProblemAvailability.CONTEST_ONLY);
        when(mongo.findById("p1", Problem.class)).thenReturn(published);
        when(mongo.findById("p2", Problem.class)).thenReturn(secret);
        stubBatch();
        ContestAdminResponse created = service.create(
            write("Weekly", START, END, List.of("b1"), List.of("p1", "p2")), "admin-1");
        assertEquals(List.of("p1", "p2"), created.getProblemIds());
    }

    @Test
    void createRejectsNonexistentProblem() {
        stubBatch();
        when(mongo.findById("missing", Problem.class)).thenReturn(null);
        when(mongo.findOne(any(Query.class), eq(Problem.class))).thenReturn(null);
        ContestWriteRequest body = write("Weekly", START, END, List.of("b1"), List.of("missing"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(body, "admin-1"));
        assertTrue(ex.getMessage().contains("Problem not found"));
    }

    @Test
    void createRejectsDuplicateProblems() {
        stubProblemAndBatch();
        ContestWriteRequest body = write("Weekly", START, END, List.of("b1"), List.of("p1", "p1"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(body, "admin-1"));
        assertTrue(ex.getMessage().contains("duplicate problem"));
    }

    @Test
    void batchesRequiresAtLeastOneBatch() {
        stubProblem();
        ContestWriteRequest body = write("Weekly", START, END, List.of(), List.of("p1"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(body, "admin-1"));
        assertTrue(ex.getMessage().contains("at least one batch"));
    }

    @Test
    void createRejectsNonexistentBatch() {
        stubProblem();
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of());
        ContestWriteRequest body = write("Weekly", START, END, List.of("missing"), List.of("p1"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(body, "admin-1"));
        assertTrue(ex.getMessage().contains("Batch not found"));
    }

    @Test
    void allUsersWithBatchIdsRejected() {
        stubProblem();
        ContestWriteRequest body = write("Global", START, END, List.of("b1"), List.of("p1"));
        body.setAudienceType(ContestAudienceType.ALL_USERS);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(body, "admin-1"));
        assertTrue(ex.getMessage().contains("cannot include batch IDs"));
    }

    @Test
    void allUsersDraftAllowedButPublishRejectedWhenDisabled() {
        stubProblem();
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(problem("p1")));
        ContestWriteRequest body = write("Global", START, END, List.of(), List.of("p1"));
        body.setAudienceType(ContestAudienceType.ALL_USERS);
        ContestAdminResponse draft = service.create(body, "admin-1");
        assertEquals(ContestStatus.DRAFT, draft.getStatus());
        assertEquals(ContestAudienceType.ALL_USERS, draft.getAudienceType());

        Contest stored = storedFrom(draft, false);
        stored.setProblemIds(List.of("p1"));
        stored.setAudienceType(ContestAudienceType.ALL_USERS);
        stored.setBatchIds(List.of());
        stored.setPublished(false);
        when(mongo.findById("c1", Contest.class)).thenReturn(stored);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.publish("c1"));
        assertTrue(ex.getMessage().contains("Global contests are disabled"));
    }

    @Test
    void allUsersPublishSucceedsWhenEnabled() {
        properties.setGlobalEnabled(true);
        stubProblem();
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(problem("p1")));
        ContestWriteRequest body = write("Global", START, END, List.of(), List.of("p1"));
        body.setAudienceType(ContestAudienceType.ALL_USERS);
        service.create(body, "admin-1");
        Contest stored = storedDraft(body);
        stored.setAudienceType(ContestAudienceType.ALL_USERS);
        stored.setBatchIds(List.of());
        stored.setProblemIds(List.of("p1"));
        when(mongo.findById("c1", Contest.class)).thenReturn(stored);
        ContestAdminResponse published = service.publish("c1");
        assertTrue(published.isPublished());
        assertEquals(ContestStatus.UPCOMING, published.getStatus());
    }

    @Test
    void upcomingContestCanBeEditedAndUnpublished() {
        stubProblemAndBatch();
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(problem("p1")));
        Contest stored = publishedContest("c1", START, END);
        stored.setBatchIds(List.of("b1"));
        stored.setProblemIds(List.of("p1"));
        when(mongo.findById("c1", Contest.class)).thenReturn(stored);
        ContestWriteRequest patch = new ContestWriteRequest();
        patch.setTitle("Renamed");
        ContestAdminResponse updated = service.update("c1", patch);
        assertEquals("Renamed", updated.getTitle());
        ContestAdminResponse unpublished = service.unpublish("c1");
        assertEquals(ContestStatus.DRAFT, unpublished.getStatus());
        verify(mongo).remove(any(Query.class), eq(ContestProblemSnapshot.class));
    }

    @Test
    void publishCreatesOneSnapshotPerProblemAndDoesNotOverwriteOnRetry() {
        stubProblemAndBatch();
        Problem publicProblem = problem("p1");
        publicProblem.setAvailability(ProblemAvailability.PUBLIC);
        publicProblem.setPublicTestCases(List.of(new TestCase("A-in", "A-out", null)));
        publicProblem.setHiddenTestCases(List.of(new TestCase("A-hidden", "A-hidden-out", null)));
        publicProblem.setExecutionMode(ExecutionMode.STANDARD_PER_CASE);
        Problem secret = problem("p2");
        secret.setAvailability(ProblemAvailability.CONTEST_ONLY);
        secret.setTitle("Secret");
        when(mongo.findById("p1", Problem.class)).thenReturn(publicProblem);
        when(mongo.findById("p2", Problem.class)).thenReturn(secret);
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(publicProblem, secret));
        ContestWriteRequest body = write("Weekly", START, END, List.of("b1"), List.of("p1", "p2"));
        Contest stored = storedDraft(body);
        stored.setProblemIds(List.of("p1", "p2"));
        when(mongo.findById("c1", Contest.class)).thenReturn(stored);
        when(mongo.findOne(any(Query.class), eq(ContestProblemSnapshot.class))).thenReturn(null);
        when(mongo.insert(any(ContestProblemSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContestAdminResponse published = service.publish("c1");
        assertTrue(published.isPublished());
        verify(mongo, org.mockito.Mockito.times(2)).insert(any(ContestProblemSnapshot.class));

        ContestProblemSnapshot existing = snapshot("c1", "p1", "Two Sum", "A-in", "A-out");
        when(mongo.findOne(any(Query.class), eq(ContestProblemSnapshot.class))).thenReturn(existing);
        service.publish("c1");
        verify(mongo, org.mockito.Mockito.times(2)).insert(any(ContestProblemSnapshot.class));
    }

    @Test
    void publishRejectsNegativeProblemPointsAndStaysUnpublished() {
        stubProblemAndBatch();
        Problem invalid = problem("p1");
        invalid.setPoints(-8);
        when(mongo.findById("p1", Problem.class)).thenReturn(invalid);
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(invalid));
        Contest stored = storedDraft(write("Weekly", START, END, List.of("b1"), List.of("p1")));
        stored.setProblemIds(List.of("p1"));
        when(mongo.findById("c1", Contest.class)).thenReturn(stored);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.publish("c1"));
        assertTrue(ex.getMessage().contains("points must be 0 or greater"));
        verify(mongo, never()).insert(any(ContestProblemSnapshot.class));
        verify(mongo, never()).save(org.mockito.ArgumentMatchers.argThat(contest ->
            contest instanceof Contest && ((Contest) contest).isPublished()));
    }

    @Test
    void partialSnapshotFailureDoesNotPublish() {
        stubProblemAndBatch();
        Problem p1 = problem("p1");
        Problem p2 = problem("p2");
        when(mongo.findById("p1", Problem.class)).thenReturn(p1);
        when(mongo.findById("p2", Problem.class)).thenReturn(p2);
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(p1, p2));
        Contest stored = storedDraft(write("Weekly", START, END, List.of("b1"), List.of("p1", "p2")));
        stored.setProblemIds(List.of("p1", "p2"));
        when(mongo.findById("c1", Contest.class)).thenReturn(stored);
        when(mongo.findOne(any(Query.class), eq(ContestProblemSnapshot.class))).thenReturn(null);
        when(mongo.insert(any(ContestProblemSnapshot.class)))
            .thenAnswer(invocation -> invocation.getArgument(0))
            .thenThrow(new RuntimeException("write failed"));
        assertThrows(RuntimeException.class, () -> service.publish("c1"));
        verify(mongo, never()).save(org.mockito.ArgumentMatchers.argThat(contest ->
            contest instanceof Contest && ((Contest) contest).isPublished()));
    }

    @Test
    void liveEligibleProblemDetailUsesSnapshotNotMutableProblem() {
        ContestService live = new ContestService(mongo, properties, Clock.fixed(START, ZoneOffset.UTC));
        Contest contest = publishedContest("c1", START, END);
        contest.setBatchIds(List.of("b1"));
        contest.setProblemIds(List.of("p1"));
        Problem mutated = problem("p1");
        mutated.setTitle("Mutated");
        mutated.setPublicTestCases(List.of(new TestCase("B-in", "B-out", null)));
        when(mongo.findById("c1", Contest.class)).thenReturn(contest);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        stubActiveBatches("b1");
        when(mongo.findById("p1", Problem.class)).thenReturn(mutated);
        ContestProblemSnapshot snapshot = snapshot("c1", "p1", "Two Sum", "A-in", "A-out");
        snapshot.setHiddenTestCases(List.of(new TestCase("secret-in", "secret-out", null)));
        when(mongo.findOne(any(Query.class), eq(ContestProblemSnapshot.class))).thenReturn(snapshot);

        ContestProblemDetailResponse detail = live.getVisibleProblem(user("u1"), "c1", "p1");
        assertEquals("p1", detail.getId());
        assertEquals("Two Sum", detail.getTitle());
        assertEquals("A-in", detail.getPublicTestCases().get(0).getInput());
        assertThrows(NoSuchFieldException.class, () -> ContestProblemDetailResponse.class.getDeclaredField("hiddenTestCases"));
    }

    @Test
    void contestProblemDetailRejectsOutsiderUpcomingAndUnknownProblem() {
        Contest contest = publishedContest("c1", START, END);
        contest.setBatchIds(List.of("b1"));
        contest.setProblemIds(List.of("p1"));
        when(mongo.findById("c1", Contest.class)).thenReturn(contest);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of());
        ResponseStatusException outsider = assertThrows(ResponseStatusException.class,
            () -> service.getVisibleProblem(user("outsider"), "c1", "p1"));
        assertEquals(404, outsider.getStatusCode().value());

        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        stubActiveBatches("b1");
        ResponseStatusException upcoming = assertThrows(ResponseStatusException.class,
            () -> service.getVisibleProblem(user("u1"), "c1", "p1"));
        assertEquals(404, upcoming.getStatusCode().value());

        ContestService live = new ContestService(mongo, properties, Clock.fixed(START, ZoneOffset.UTC));
        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
            () -> live.getVisibleProblem(user("u1"), "c1", "other"));
        assertEquals(404, missing.getStatusCode().value());
    }

    @Test
    void endedEligibleCanReadSnapshot() {
        ContestService ended = new ContestService(mongo, properties, Clock.fixed(END, ZoneOffset.UTC));
        Contest contest = publishedContest("c1", START, END);
        contest.setBatchIds(List.of("b1"));
        contest.setProblemIds(List.of("p1"));
        when(mongo.findById("c1", Contest.class)).thenReturn(contest);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        stubActiveBatches("b1");
        when(mongo.findOne(any(Query.class), eq(ContestProblemSnapshot.class)))
            .thenReturn(snapshot("c1", "p1", "Two Sum", "A-in", "A-out"));
        ContestProblemDetailResponse detail = ended.getVisibleProblem(user("u1"), "c1", "p1");
        assertEquals("Two Sum", detail.getTitle());
    }

    @Test
    void liveContestCannotChangeAudienceProblemsOrStartAt() {
        stubProblemAndBatch();
        Contest stored = publishedContest("c1", NOW.minusSeconds(60), END);
        stored.setBatchIds(List.of("b1"));
        stored.setProblemIds(List.of("p1"));
        when(mongo.findById("c1", Contest.class)).thenReturn(stored);
        assertEquals(ContestStatus.LIVE, service.status(stored));

        ContestWriteRequest audience = new ContestWriteRequest();
        audience.setAudienceType(ContestAudienceType.ALL_USERS);
        audience.setBatchIds(List.of());
        IllegalArgumentException audienceEx = assertThrows(IllegalArgumentException.class, () -> service.update("c1", audience));
        assertTrue(audienceEx.getMessage().contains("cannot change audience"));

        ContestWriteRequest problems = new ContestWriteRequest();
        problems.setProblemIds(List.of("p2"));
        when(mongo.findById("p2", Problem.class)).thenReturn(problem("p2"));
        IllegalArgumentException problemEx = assertThrows(IllegalArgumentException.class, () -> service.update("c1", problems));
        assertTrue(problemEx.getMessage().contains("cannot change problems"));

        ContestWriteRequest start = new ContestWriteRequest();
        start.setStartAt(NOW.minusSeconds(30));
        IllegalArgumentException startEx = assertThrows(IllegalArgumentException.class, () -> service.update("c1", start));
        assertTrue(startEx.getMessage().contains("cannot change startAt"));
    }

    @Test
    void liveContestCannotBeDeletedOrUnpublished() {
        Contest stored = publishedContest("c1", NOW.minusSeconds(60), END);
        when(mongo.findById("c1", Contest.class)).thenReturn(stored);
        IllegalArgumentException deleteEx = assertThrows(IllegalArgumentException.class, () -> service.delete("c1"));
        assertTrue(deleteEx.getMessage().contains("Live contests cannot be deleted"));
        IllegalArgumentException unpublishEx = assertThrows(IllegalArgumentException.class, () -> service.unpublish("c1"));
        assertTrue(unpublishEx.getMessage().contains("Only upcoming contests can be unpublished"));
        verify(mongo, never()).remove(any(Contest.class));
        verify(mongo, never()).remove(any(Query.class), eq(ContestProblemSnapshot.class));
    }

    @Test
    void endedContestCannotBeMutatedOrDeleted() {
        Contest stored = publishedContest("c1", NOW.minusSeconds(7200), NOW.minusSeconds(60));
        when(mongo.findById("c1", Contest.class)).thenReturn(stored);
        assertEquals(ContestStatus.ENDED, service.status(stored));
        ContestWriteRequest patch = new ContestWriteRequest();
        patch.setTitle("Nope");
        IllegalArgumentException updateEx = assertThrows(IllegalArgumentException.class, () -> service.update("c1", patch));
        assertTrue(updateEx.getMessage().contains("Ended contests cannot be modified"));
        IllegalArgumentException deleteEx = assertThrows(IllegalArgumentException.class, () -> service.delete("c1"));
        assertTrue(deleteEx.getMessage().contains("Ended contests cannot be deleted"));
        verify(mongo, never()).save(any(Contest.class));
        verify(mongo, never()).remove(any(Contest.class));
        verify(mongo, never()).remove(any(Query.class), eq(ContestProblemSnapshot.class));
    }

    @Test
    void userInEitherAssignedBatchCanAccess() {
        Contest contest = publishedContest("c1", START, END);
        contest.setBatchIds(List.of("b1", "b2"));
        contest.setProblemIds(List.of("p1"));
        when(mongo.findById("c1", Contest.class)).thenReturn(contest);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        stubActiveBatches("b1", "b2");
        ContestUserResponse first = service.getVisible(user("u1"), "c1");
        assertEquals("c1", first.getId());
        assertEquals(1, first.getProblemCount());
        assertTrue(first.getProblems().isEmpty());

        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b2", "u2")));
        ContestUserResponse second = service.getVisible(user("u2"), "c1");
        assertEquals("Weekly", second.getTitle());
    }

    @Test
    void userOutsideAssignedBatchesCannotAccess() {
        Contest contest = publishedContest("c1", START, END);
        contest.setBatchIds(List.of("b1", "b2"));
        when(mongo.findById("c1", Contest.class)).thenReturn(contest);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("other", "u3")));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getVisible(user("u3"), "c1"));
        assertEquals(404, ex.getStatusCode().value());
        assertEquals("Contest not found", ex.getReason());
    }

    @Test
    void unpublishedContestIsNotFoundToUsers() {
        Contest contest = publishedContest("c1", START, END);
        contest.setPublished(false);
        contest.setBatchIds(List.of("b1"));
        when(mongo.findById("c1", Contest.class)).thenReturn(contest);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getVisible(user("u1"), "c1"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void missingContestDoesNotLeakExistence() {
        when(mongo.findById("missing", Contest.class)).thenReturn(null);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getVisible(user("u1"), "missing"));
        assertEquals(404, ex.getStatusCode().value());
        assertEquals("Contest not found", ex.getReason());
    }

    @Test
    void allUsersHiddenFromUsersWhenFlagDisabled() {
        Contest contest = publishedContest("c1", START, END);
        contest.setAudienceType(ContestAudienceType.ALL_USERS);
        contest.setBatchIds(List.of());
        when(mongo.findById("c1", Contest.class)).thenReturn(contest);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getVisible(user("u1"), "c1"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void listDoesNotScanContestsWhenUserHasNoMembershipsAndGlobalDisabled() {
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of());
        var page = service.listVisible(user("u1"), 0, 20);
        assertEquals(0L, page.get("totalItems"));
        verify(mongo, never()).find(any(Query.class), eq(Contest.class));
        verify(mongo, never()).count(any(Query.class), eq(Contest.class));
    }

    @Test
    void upcomingUserDtoHidesProblemDetails() {
        Contest contest = publishedContest("c1", START, END);
        contest.setBatchIds(List.of("b1"));
        contest.setProblemIds(List.of("p1"));
        Problem leaky = problem("p1");
        leaky.setStatement("secret statement");
        leaky.setHiddenTestCases(List.of());
        when(mongo.findById("c1", Contest.class)).thenReturn(contest);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        stubActiveBatches("b1");
        ContestUserResponse response = service.getVisible(user("u1"), "c1");
        assertEquals(ContestStatus.UPCOMING, response.getStatus());
        assertEquals(1, response.getProblemCount());
        assertTrue(response.getProblems().isEmpty());
        verify(mongo, never()).find(any(Query.class), eq(Problem.class));
    }

    @Test
    void liveUserDtoExposesSafeSummariesOnly() {
        ContestService live = new ContestService(mongo, properties, Clock.fixed(START, ZoneOffset.UTC));
        Contest contest = publishedContest("c1", START, END);
        contest.setBatchIds(List.of("b1"));
        contest.setProblemIds(List.of("p1"));
        Problem leaky = problem("p1");
        leaky.setTitle("Mutated title");
        leaky.setStatement("secret statement");
        when(mongo.findById("c1", Contest.class)).thenReturn(contest);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        stubActiveBatches("b1");
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(leaky));
        when(mongo.find(any(Query.class), eq(ContestProblemSnapshot.class)))
            .thenReturn(List.of(snapshot("c1", "p1", "Two Sum", "A-in", "A-out")));
        ContestUserResponse response = live.getVisible(user("u1"), "c1");
        assertEquals(ContestStatus.LIVE, response.getStatus());
        assertEquals(1, response.getProblems().size());
        ContestProblemSummary summary = response.getProblems().get(0);
        assertEquals("p1", summary.getId());
        assertEquals("Two Sum", summary.getTitle());
        assertThrows(NoSuchFieldException.class, () -> ContestProblemSummary.class.getDeclaredField("hiddenTestCases"));
        assertThrows(NoSuchFieldException.class, () -> ContestProblemSummary.class.getDeclaredField("statement"));
        assertThrows(NoSuchFieldException.class, () -> ContestUserResponse.class.getDeclaredField("hiddenTestCases"));
        assertThrows(NoSuchFieldException.class, () -> ContestProblemDetailResponse.class.getDeclaredField("hiddenTestCases"));
    }

    @Test
    void draftDeleteIsAllowed() {
        Contest stored = storedDraft(write("Weekly", START, END, List.of("b1"), List.of("p1")));
        when(mongo.findById("c1", Contest.class)).thenReturn(stored);
        service.delete("c1");
        verify(mongo).remove(stored);
    }

    @Test
    void draftMayReferenceInactiveBatchButPublishRequiresActive() {
        stubProblem();
        Batch inactive = batch("b1", false);
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of(inactive));
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(problem("p1")));
        ContestWriteRequest body = write("Weekly", START, END, List.of("b1"), List.of("p1"));
        ContestAdminResponse draft = service.create(body, "admin-1");
        assertEquals(ContestStatus.DRAFT, draft.getStatus());

        Contest stored = storedDraft(body);
        stored.setProblemIds(List.of("p1"));
        when(mongo.findById("c1", Contest.class)).thenReturn(stored);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.publish("c1"));
        assertTrue(ex.getMessage().contains("Batch is not active"));
    }

    @Test
    void archivedBatchMemberCannotSeeUpcomingOrLiveContest() {
        Contest contest = publishedContest("c1", START, END);
        contest.setBatchIds(List.of("b1"));
        when(mongo.findById("c1", Contest.class)).thenReturn(contest);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of(batch("b1", false)));
        ResponseStatusException upcoming = assertThrows(ResponseStatusException.class,
            () -> service.getVisible(user("u1"), "c1"));
        assertEquals(404, upcoming.getStatusCode().value());

        ContestService live = new ContestService(mongo, properties, Clock.fixed(START, ZoneOffset.UTC));
        ResponseStatusException liveEx = assertThrows(ResponseStatusException.class,
            () -> live.getVisible(user("u1"), "c1"));
        assertEquals(404, liveEx.getStatusCode().value());
    }

    @Test
    void endedContestRemainsVisibleAfterBatchArchive() {
        ContestService ended = new ContestService(mongo, properties, Clock.fixed(END, ZoneOffset.UTC));
        Contest contest = publishedContest("c1", START, END);
        contest.setBatchIds(List.of("b1"));
        contest.setProblemIds(List.of("p1"));
        when(mongo.findById("c1", Contest.class)).thenReturn(contest);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of(batch("b1", false)));
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(problem("p1")));
        ContestUserResponse response = ended.getVisible(user("u1"), "c1");
        assertEquals(ContestStatus.ENDED, response.getStatus());
        assertEquals("c1", response.getId());
    }

    @Test
    void livePatchMayExtendOrShortenEndAtOnlyWhileStillInTheFuture() {
        stubProblemAndBatch();
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(problem("p1")));
        Contest stored = publishedContest("c1", NOW.minusSeconds(60), END);
        stored.setBatchIds(List.of("b1"));
        stored.setProblemIds(List.of("p1"));
        when(mongo.findById("c1", Contest.class)).thenReturn(stored);
        assertEquals(ContestStatus.LIVE, service.status(stored));

        ContestWriteRequest extend = new ContestWriteRequest();
        extend.setEndAt(END.plusSeconds(3600));
        assertEquals(END.plusSeconds(3600), service.update("c1", extend).getEndAt());

        ContestWriteRequest shorten = new ContestWriteRequest();
        shorten.setEndAt(NOW.plusSeconds(1));
        assertEquals(NOW.plusSeconds(1), service.update("c1", shorten).getEndAt());

        stored.setEndAt(NOW.plusSeconds(1));
        ContestWriteRequest equalNow = new ContestWriteRequest();
        equalNow.setEndAt(NOW);
        IllegalArgumentException equalEx = assertThrows(IllegalArgumentException.class, () -> service.update("c1", equalNow));
        assertTrue(equalEx.getMessage().contains("endAt must be in the future"));

        stored.setEndAt(NOW.plusSeconds(1));
        ContestWriteRequest past = new ContestWriteRequest();
        past.setEndAt(NOW.minusNanos(1));
        IllegalArgumentException pastEx = assertThrows(IllegalArgumentException.class, () -> service.update("c1", past));
        assertTrue(pastEx.getMessage().contains("endAt must be in the future"));
    }

    @Test
    void titleOnlyPatchDoesNotClearOtherFieldsOrPublish() {
        stubBatch();
        Contest stored = storedDraft(write("Weekly", START, END, List.of("b1"), List.of("p1")));
        stored.setDescription("Keep me");
        stored.setPublished(false);
        when(mongo.findById("c1", Contest.class)).thenReturn(stored);
        ContestWriteRequest patch = new ContestWriteRequest();
        patch.setTitle("New title");
        ContestAdminResponse updated = service.update("c1", patch);
        assertEquals("New title", updated.getTitle());
        assertEquals("Keep me", updated.getDescription());
        assertEquals(START, updated.getStartAt());
        assertEquals(END, updated.getEndAt());
        assertEquals(ContestAudienceType.BATCHES, updated.getAudienceType());
        assertEquals(List.of("b1"), updated.getBatchIds());
        assertEquals(List.of("p1"), updated.getProblemIds());
        assertEquals(false, updated.isPublished());
        verify(mongo, never()).findById("p1", Problem.class);
    }

    @Test
    void adminListPaginatesInMongoWithCreatedAtDescAndMaxSize() {
        when(mongo.count(any(Query.class), eq(Contest.class))).thenReturn(200L);
        when(mongo.find(any(Query.class), eq(Contest.class))).thenReturn(List.of());
        var page = service.adminList(2, 999);
        assertEquals(2, page.get("currentPage"));
        assertEquals(4, page.get("totalPages"));
        assertEquals(200L, page.get("totalItems"));
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(captor.capture(), eq(Contest.class));
        Query query = captor.getValue();
        assertEquals(100L, query.getSkip());
        assertEquals(50, query.getLimit());
        assertEquals(-1, query.getSortObject().getInteger("createdAt"));
    }

    @Test
    void userListPaginatesInMongoWithStartAtAsc() {
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        stubActiveBatches("b1");
        when(mongo.count(any(Query.class), eq(Contest.class))).thenReturn(3L);
        when(mongo.find(any(Query.class), eq(Contest.class))).thenReturn(List.of());
        var page = service.listVisible(user("u1"), 1, 2);
        assertEquals(1, page.get("currentPage"));
        assertEquals(2, page.get("totalPages"));
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(captor.capture(), eq(Contest.class));
        Query query = captor.getValue();
        assertEquals(2L, query.getSkip());
        assertEquals(2, query.getLimit());
        assertEquals(1, query.getSortObject().getInteger("startAt"));
        String queryText = String.valueOf(query.getQueryObject());
        assertTrue(queryText.contains("published"));
        assertTrue(queryText.contains("BATCHES"));
    }

    @Test
    void defaultPageSizeUsedWhenSizeNotPositive() {
        when(mongo.count(any(Query.class), eq(Contest.class))).thenReturn(0L);
        when(mongo.find(any(Query.class), eq(Contest.class))).thenReturn(List.of());
        service.adminList(0, 0);
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(captor.capture(), eq(Contest.class));
        assertEquals(20, captor.getValue().getLimit());
        assertEquals(0L, captor.getValue().getSkip());
    }

    private void stubProblemAndBatch() {
        stubProblem();
        stubBatch();
    }

    private void stubProblem() {
        when(mongo.findById("p1", Problem.class)).thenReturn(problem("p1"));
    }

    private void stubBatch() {
        stubActiveBatches("b1");
        when(mongo.findById("b1", Batch.class)).thenReturn(batch("b1", true));
    }

    private void stubActiveBatches(String... ids) {
        List<Batch> batches = new java.util.ArrayList<>();
        for (String id : ids) {
            batches.add(batch(id, true));
        }
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(batches);
    }

    private static Batch batch(String id, boolean active) {
        Batch batch = new Batch();
        batch.setId(id);
        batch.setActive(active);
        return batch;
    }

    private static ContestWriteRequest write(String title, Instant start, Instant end, List<String> batches, List<String> problems) {
        ContestWriteRequest body = new ContestWriteRequest();
        body.setTitle(title);
        body.setStartAt(start);
        body.setEndAt(end);
        body.setAudienceType(ContestAudienceType.BATCHES);
        body.setBatchIds(batches);
        body.setProblemIds(problems);
        return body;
    }

    private static Contest publishedContest(String id, Instant start, Instant end) {
        Contest contest = new Contest();
        contest.setId(id);
        contest.setTitle("Weekly");
        contest.setStartAt(start);
        contest.setEndAt(end);
        contest.setPublished(true);
        contest.setAudienceType(ContestAudienceType.BATCHES);
        return contest;
    }

    private Contest storedDraft(ContestWriteRequest body) {
        Contest contest = new Contest();
        contest.setId("c1");
        contest.setTitle(body.getTitle());
        contest.setStartAt(body.getStartAt());
        contest.setEndAt(body.getEndAt());
        contest.setPublished(false);
        contest.setAudienceType(body.getAudienceType() == null ? ContestAudienceType.BATCHES : body.getAudienceType());
        contest.setBatchIds(body.getBatchIds());
        contest.setProblemIds(body.getProblemIds());
        contest.setCreatedBy("admin-1");
        return contest;
    }

    private static Contest storedFrom(ContestAdminResponse response, boolean published) {
        Contest contest = new Contest();
        contest.setId(response.getId());
        contest.setTitle(response.getTitle());
        contest.setStartAt(response.getStartAt());
        contest.setEndAt(response.getEndAt());
        contest.setPublished(published);
        contest.setAudienceType(response.getAudienceType());
        contest.setBatchIds(response.getBatchIds());
        contest.setProblemIds(response.getProblemIds());
        return contest;
    }

    private static ContestProblemSnapshot snapshot(String contestId, String problemId, String title,
                                                  String publicIn, String publicOut) {
        ContestProblemSnapshot snapshot = new ContestProblemSnapshot();
        snapshot.setId("snap-" + problemId);
        snapshot.setContestId(contestId);
        snapshot.setProblemId(problemId);
        snapshot.setTitle(title);
        snapshot.setDifficulty("EASY");
        snapshot.setPoints(10);
        snapshot.setExecutionMode(ExecutionMode.STANDARD_PER_CASE);
        snapshot.setTestcaseVersion("v1");
        snapshot.setPublicTestCases(List.of(new TestCase(publicIn, publicOut, null)));
        snapshot.setHiddenTestCases(List.of());
        return snapshot;
    }

    private static Problem problem(String id) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setTitle("Two Sum");
        problem.setDifficulty("EASY");
        problem.setPoints(10);
        return problem;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setUsername(id);
        user.setRole("USER");
        user.setAccountStatus("ACTIVE");
        return user;
    }

    private static BatchMember member(String batchId, String userId) {
        BatchMember member = new BatchMember();
        member.setBatchId(batchId);
        member.setUserId(userId);
        return member;
    }
}
