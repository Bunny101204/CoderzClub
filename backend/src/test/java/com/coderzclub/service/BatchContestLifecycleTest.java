package com.coderzclub.service;

import com.coderzclub.dto.ContestSubmissionJobRequest;
import com.coderzclub.dto.ContestWriteRequest;
import com.coderzclub.model.Batch;
import com.coderzclub.model.Contest;
import com.coderzclub.model.ContestAudienceType;
import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.ContestScore;
import com.coderzclub.model.ContestScoringStatus;
import com.coderzclub.model.ContestStatus;
import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
import com.coderzclub.model.TestCase;
import com.coderzclub.model.User;
import com.coderzclub.config.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BatchContestLifecycleTest {

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> "mongodb://127.0.0.1:27017/coderzclub_batch_review");
        registry.add("jwt.secret", () -> "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    }

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private BatchService batchService;
    @Autowired private ContestService contestService;
    @Autowired private ContestAccessService access;
    @Autowired private ContestSubmissionAdmissionService admission;
    @Autowired private ContestScoringService scoring;
    @Autowired private ContestProblemSnapshotService snapshots;
    @Autowired private StudentAccountService students;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ObjectMapper objectMapper;

    @AfterEach
    void dropIsolatedData() {
        mongoTemplate.getDb().drop();
    }

    @Test
    void batchContestLifecycleUpdatesEligibilityAccessAndScore() throws Exception {
        User admin = saveUser("review-admin", "ADMIN", true);
        User student = saveUser("review-student", "USER", true);
        User outsider = saveUser("review-outsider", "USER", true);
        Batch batch = batchService.create("Review batch", "isolated", admin.getId());
        batchService.addMembers(batch.getId(), List.of(student.getId()));
        assertEquals(1L, batchService.detail(batch.getId()).get("memberCount"));
        assertEquals(1, batchService.removeMembers(batch.getId(), List.of(student.getId())));
        assertEquals(0L, batchService.detail(batch.getId()).get("memberCount"));
        batchService.addMembers(batch.getId(), List.of(student.getId()));

        Problem problem = new Problem();
        problem.setTitle("Review problem");
        problem.setFunctionName("solve");
        problem.setPoints(100);
        TestCase visible = new TestCase("1", "1");
        TestCase hidden = new TestCase("SECRET_HIDDEN_INPUT", "SECRET_HIDDEN_OUTPUT");
        problem.setPublicTestCases(List.of(visible));
        problem.setHiddenTestCases(List.of(hidden));
        final Problem savedProblem = mongoTemplate.save(problem);

        ContestWriteRequest draft = new ContestWriteRequest();
        draft.setTitle("Review contest");
        draft.setAudienceType(ContestAudienceType.BATCHES);
        draft.setBatchIds(List.of(batch.getId()));
        draft.setProblemIds(List.of(savedProblem.getId()));
        draft.setStartAt(Instant.now().plusSeconds(3600));
        draft.setEndAt(Instant.now().plusSeconds(7200));
        String contestId = contestService.create(draft, admin.getId()).getId();
        assertEquals(404, statusOf(() -> contestService.getVisible(student, contestId)));

        contestService.publish(contestId);
        assertEquals(ContestStatus.UPCOMING, contestService.getVisible(student, contestId).getStatus());
        assertEquals(1, contestService.adminGet(contestId).getEligibleMemberCount());
        assertEquals(404, statusOf(() -> contestService.getVisible(outsider, contestId)));
        assertEquals(404, statusOf(() -> contestService.getVisibleProblem(student, contestId, savedProblem.getId())));
        ContestAdmissionException beforeStart = assertThrows(ContestAdmissionException.class,
            () -> admission.admit(student, contestId, savedProblem.getId(), submission("class Main {}")));
        assertEquals("CONTEST_NOT_LIVE", beforeStart.getError());

        ContestWriteRequest liveWindow = new ContestWriteRequest();
        liveWindow.setStartAt(Instant.now().minusSeconds(30));
        liveWindow.setEndAt(Instant.now().plusSeconds(120));
        contestService.update(contestId, liveWindow);
        String liveJson = objectMapper.writeValueAsString(
            contestService.getVisibleProblem(student, contestId, savedProblem.getId()));
        assertTrue(liveJson.contains("\"input\":\"1\""));
        assertFalse(liveJson.contains("SECRET_HIDDEN"));
        assertEquals(404, statusOf(() -> contestService.getVisibleProblem(outsider, contestId, savedProblem.getId())));

        batchService.update(batch.getId(), null, null, false);
        assertFalse(eligible(student, contestId));
        batchService.update(batch.getId(), null, null, true);
        assertTrue(eligible(student, contestId));

        String issuedWhileActive = jwtUtil.generateToken(student.getId(), student.getUsername(), student.getRole());
        students.setActive(student.getId(), false, admin.getUsername());
        User inactive = mongoTemplate.findById(student.getId(), User.class);
        assertFalse(eligible(inactive, contestId));
        mockMvc.perform(get("/api/contests").header("Authorization", "Bearer " + issuedWhileActive))
            .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/admin/students").header("Authorization", "Bearer " + issuedWhileActive))
            .andExpect(status().is4xxClientError());
        students.setActive(student.getId(), true, admin.getUsername());
        User restored = mongoTemplate.findById(student.getId(), User.class);
        assertTrue(eligible(restored, contestId));
        String studentToken = jwtUtil.generateToken(restored.getId(), restored.getUsername(), restored.getRole());
        mockMvc.perform(get("/api/admin/students").header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());

        ContestProblemSnapshot snapshot = snapshots.find(contestId, savedProblem.getId());
        scoring.apply(savedSubmission(restored, savedProblem, contestId, snapshot, "review-job-1"));
        ContestScore score = scoreFor(contestId, restored.getId());
        assertEquals(100, score.getTotalPoints());
        assertEquals(1, score.getSolvedCount());
        scoring.apply(savedSubmission(restored, savedProblem, contestId, snapshot, "review-job-2"));
        ContestScore afterDuplicate = scoreFor(contestId, restored.getId());
        assertEquals(100, afterDuplicate.getTotalPoints());
        assertEquals(1, afterDuplicate.getSolvedCount());

        assertEquals(1, batchService.removeMembers(batch.getId(), List.of(restored.getId())));
        assertFalse(eligible(restored, contestId));
        batchService.addMembers(batch.getId(), List.of(restored.getId()));
        assertTrue(eligible(restored, contestId));

        ContestWriteRequest ending = new ContestWriteRequest();
        ending.setEndAt(Instant.now().plusSeconds(2));
        contestService.update(contestId, ending);
        Thread.sleep(2500);
        ContestAdmissionException ended = assertThrows(ContestAdmissionException.class,
            () -> admission.admit(restored, contestId, savedProblem.getId(), submission("class Main {}")));
        assertEquals("CONTEST_ENDED", ended.getError());
        assertEquals(ContestStatus.ENDED, contestService.getVisible(restored, contestId).getStatus());
        assertFalse(objectMapper.writeValueAsString(
            contestService.getVisibleProblem(restored, contestId, savedProblem.getId())).contains("SECRET_HIDDEN"));
    }

    private boolean eligible(User user, String contestId) {
        Contest contest = mongoTemplate.findById(contestId, Contest.class);
        return access.isEligible(user, contest, access.scopeForUser(user.getId()));
    }

    private ContestScore scoreFor(String contestId, String userId) {
        return mongoTemplate.findOne(
            Query.query(Criteria.where("contestId").is(contestId).and("userId").is(userId)),
            ContestScore.class);
    }

    private Submission savedSubmission(User user, Problem problem, String contestId,
                                       ContestProblemSnapshot snapshot, String jobId) {
        Submission submission = Submission.builder()
            .userId(user.getId())
            .problemId(problem.getId())
            .result("ACCEPTED")
            .contestId(contestId)
            .contestSubmittedAt(Instant.now())
            .contestProblemSnapshotId(snapshot.getId())
            .contestScoringStatus(ContestScoringStatus.PENDING)
            .submissionJobId(jobId)
            .build();
        return mongoTemplate.save(submission);
    }

    private static ContestSubmissionJobRequest submission(String code) {
        ContestSubmissionJobRequest request = new ContestSubmissionJobRequest();
        request.setCode(code);
        request.setLanguage("java");
        request.setLanguageId(62);
        return request;
    }

    private User saveUser(String username, String role, boolean verified) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@review.local");
        user.setRole(role);
        user.setEmailVerified(verified);
        user.setAccountStatus("ACTIVE");
        return mongoTemplate.save(user);
    }

    private int statusOf(Runnable action) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, action::run);
        return ex.getStatusCode().value();
    }
}
