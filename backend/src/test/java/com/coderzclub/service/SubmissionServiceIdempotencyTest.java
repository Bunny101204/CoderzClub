package com.coderzclub.service;

import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.User;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.SubmissionRepository;
import com.coderzclub.repository.SubmissionTestResultRepository;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.repository.UserSolvedProblemRepository;
import com.coderzclub.model.UserSolvedProblem;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceIdempotencyTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private SubmissionTestResultRepository resultRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProblemRepository problemRepository;
    @Mock private UserSolvedProblemRepository userSolvedProblemRepository;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private UserService userService;
    @Mock private LeaderboardService leaderboardService;
    @InjectMocks private SubmissionService submissionService;

    private SubmissionJob job;
    private User user;
    private Problem problem;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(submissionService, "operationalMetrics",
            new OperationalMetrics(new SimpleMeterRegistry()));
        user = new User();
        user.setId("user-1");
        problem = new Problem();
        problem.setId("problem-1");
        problem.setPoints(10);
        job = new SubmissionJob();
        job.setId("job-1");
        job.setUserId("user-1");
        job.setProblemId("problem-1");
        job.setFinalResult("ACCEPTED");
        job.setTotalTests(1);
        job.setCode("print(1)");
        job.setLanguage("PYTHON");
    }

    @Test
    void createSubmissionFromJobTwiceReturnsSameRowAndDoesNotDoubleReward() {
        Submission first = Submission.builder()
            .id("sub-1")
            .userId("user-1")
            .problemId("problem-1")
            .submissionJobId("job-1")
            .result("ACCEPTED")
            .build();
        AtomicInteger saves = new AtomicInteger();
        when(submissionRepository.findBySubmissionJobId("job-1"))
            .thenAnswer(invocation -> saves.get() == 0 ? Optional.empty() : Optional.of(first));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(problemRepository.findById("problem-1")).thenReturn(Optional.of(problem));
        when(resultRepository.findByJobIdOrderByTestcaseIndexAsc("job-1")).thenReturn(List.of());
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            saves.incrementAndGet();
            Submission saved = invocation.getArgument(0);
            saved.setId("sub-1");
            return saved;
        });
        when(userSolvedProblemRepository.insert(any(UserSolvedProblem.class))).thenThrow(new DuplicateKeyException("dup"));
        when(userSolvedProblemRepository.findByUserIdAndProblemId("user-1", "problem-1"))
            .thenReturn(Optional.of(applied()));

        Submission one = submissionService.createSubmissionFromJob(job);
        Submission two = submissionService.createSubmissionFromJob(job);

        assertEquals("sub-1", one.getId());
        assertSame(first, two);
        verify(submissionRepository, times(1)).save(any(Submission.class));
        verify(userService, times(1)).recordFinalSubmission(any());
        verify(userSolvedProblemRepository, times(1)).insert(any(UserSolvedProblem.class));
        verify(mongoTemplate, never()).updateFirst(any(), any(), any(Class.class));
    }

    @Test
    void concurrentSaveDuplicateKeyReusesExistingRow() {
        Submission existing = Submission.builder().id("sub-1").submissionJobId("job-1").build();
        when(submissionRepository.findBySubmissionJobId("job-1"))
            .thenReturn(Optional.empty(), Optional.of(existing));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(problemRepository.findById("problem-1")).thenReturn(Optional.of(problem));
        when(resultRepository.findByJobIdOrderByTestcaseIndexAsc("job-1")).thenReturn(List.of());
        when(submissionRepository.save(any(Submission.class))).thenThrow(new DuplicateKeyException("dup"));

        Submission reused = submissionService.createSubmissionFromJob(job);

        assertEquals("sub-1", reused.getId());
        verify(userService, never()).recordFinalSubmission(any());
    }

    @Test
    void legacySubmissionWithoutJobIdIsUnrelated() {
        job.setFinalResult("WRONG_ANSWER");
        when(submissionRepository.findBySubmissionJobId("job-1")).thenReturn(Optional.empty());
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(problemRepository.findById("problem-1")).thenReturn(Optional.of(problem));
        when(resultRepository.findByJobIdOrderByTestcaseIndexAsc("job-1")).thenReturn(List.of());
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission saved = invocation.getArgument(0);
            saved.setId("new-1");
            return saved;
        });

        Submission created = submissionService.createSubmissionFromJob(job);

        assertEquals("job-1", created.getSubmissionJobId());
        assertNull(Submission.builder().userId("legacy").result("ACCEPTED").build().getSubmissionJobId());
    }

    private static com.coderzclub.model.UserSolvedProblem applied() {
        com.coderzclub.model.UserSolvedProblem solved = new com.coderzclub.model.UserSolvedProblem();
        solved.setRewardStatus(com.coderzclub.model.UserSolvedProblem.RewardStatus.APPLIED);
        solved.setUserId("user-1");
        solved.setProblemId("problem-1");
        return solved;
    }
}
