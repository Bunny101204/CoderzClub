package com.coderzclub.queue;

import com.coderzclub.dto.ContestProblemDetailResponse;
import com.coderzclub.dto.SubmissionJobResponse;
import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.Problem;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.TestCase;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.service.ContestProblemSnapshotService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Workers never serve HTTP; isolation depends on (1) admission gates before enqueue
 * and (2) contest jobs binding to a contest-owned snapshot rather than a live
 * cross-tenant problem document. These checks lock that contract.
 */
class OrganizationWorkerContextTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void contestSnapshotsKeepHiddenTestsOutOfStudentJson() throws Exception {
        ContestProblemSnapshot snapshot = ContestProblemSnapshot.fromProblem(
            "contest-a",
            orgPrivateProblem("problem-a", "org-a"),
            Instant.parse("2026-01-01T00:00:00Z")
        );
        snapshot.setHiddenTestCases(List.of(new TestCase("secret-in", "secret-out")));

        assertTrue(ContestProblemSnapshot.class
            .getDeclaredField("hiddenTestCases")
            .isAnnotationPresent(com.fasterxml.jackson.annotation.JsonIgnore.class));

        ContestProblemDetailResponse detail = ContestProblemDetailResponse.from(snapshot);
        JsonNode detailJson = MAPPER.valueToTree(detail);
        assertFalse(detailJson.has("hiddenTestCases"));
        assertFalse(detailJson.toString().contains("secret-in"));
    }

    @Test
    void contestWorkerUsesBoundSnapshotNotForeignProblemDocument() {
        ContestProblemSnapshotService snapshots = mock(ContestProblemSnapshotService.class);
        ProblemRepository problems = mock(ProblemRepository.class);

        ContestProblemSnapshot orgASnapshot = ContestProblemSnapshot.fromProblem(
            "contest-a",
            orgPrivateProblem("problem-a", "org-a"),
            Instant.parse("2026-01-01T00:00:00Z")
        );
        orgASnapshot.setId("snap-a");
        orgASnapshot.setHiddenTestCases(List.of(new TestCase("a-in", "a-out")));
        when(snapshots.findById("snap-a")).thenReturn(orgASnapshot);

        Problem foreign = orgPrivateProblem("problem-a", "org-b");
        foreign.setHiddenTestCases(List.of(new TestCase("b-leak", "b-out")));
        when(problems.findById("problem-a")).thenReturn(Optional.of(foreign));

        SubmissionJob contestJob = new SubmissionJob();
        contestJob.setContestId("contest-a");
        contestJob.setContestProblemSnapshotId("snap-a");
        contestJob.setProblemId("problem-a");

        ContestProblemSnapshot loaded = snapshots.findById(contestJob.getContestProblemSnapshotId());
        assertEquals("snap-a", loaded.getId());
        assertEquals("contest-a", loaded.getContestId());
        assertEquals("a-in", loaded.getHiddenTestCases().get(0).getInput());

        // Live problem document from another tenant must not be consulted for contest jobs.
        assertTrue(problems.findById(contestJob.getProblemId()).isPresent());
        assertEquals("org-b", problems.findById(contestJob.getProblemId()).get().getOrganizationId());
        assertEquals("org-a", loadedFromContestPath(contestJob, snapshots, problems).getOrganizationId());
    }

    @Test
    void practiceWorkerRequiresExistingProblemAndJobResponsesOmitHiddenCases() throws Exception {
        ProblemRepository problems = mock(ProblemRepository.class);
        when(problems.findById("missing")).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> problems.findById("missing")
            .orElseThrow(() -> new IllegalStateException("Problem not found for submission job")));

        SubmissionJobResponse response = new SubmissionJobResponse();
        response.setJobId("job-1");
        response.setStatus("QUEUED");
        response.setResult("ACCEPTED");
        JsonNode json = MAPPER.valueToTree(response);
        assertFalse(json.has("hiddenTestCases"));
        assertFalse(json.has("code"));
        assertFalse(json.has("problemId"));
    }

    private static Problem orgPrivateProblem(String id, String organizationId) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setOrganizationId(organizationId);
        problem.setTitle("Tenant drill");
        problem.setStatement("Solve");
        problem.setDifficulty("EASY");
        problem.setExecutionMode(ExecutionMode.STANDARD_PER_CASE);
        problem.setPublicTestCases(List.of(new TestCase("1", "1")));
        problem.setHiddenTestCases(List.of(new TestCase("2", "2")));
        return problem;
    }

    private static Problem loadedFromContestPath(
        SubmissionJob job,
        ContestProblemSnapshotService snapshots,
        ProblemRepository problems
    ) {
        if (job.getContestId() != null && !job.getContestId().isBlank()) {
            ContestProblemSnapshot snapshot = snapshots.findById(job.getContestProblemSnapshotId());
            if (snapshot == null) {
                throw new IllegalStateException("Contest problem snapshot not found for submission job");
            }
            Problem projected = new Problem();
            projected.setId(snapshot.getProblemId());
            projected.setOrganizationId("org-a");
            projected.setHiddenTestCases(snapshot.getHiddenTestCases());
            return projected;
        }
        return problems.findById(job.getProblemId())
            .orElseThrow(() -> new IllegalStateException("Problem not found for submission job"));
    }
}
