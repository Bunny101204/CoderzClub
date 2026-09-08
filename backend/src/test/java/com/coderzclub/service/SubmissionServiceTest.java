package com.coderzclub.service;

import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
import com.coderzclub.model.User;
import com.coderzclub.model.UserSolvedProblem;
import com.coderzclub.repository.SubmissionRepository;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.UserSolvedProblemRepository;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private UserSolvedProblemRepository userSolvedProblemRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private UserService userService;

    @InjectMocks
    private SubmissionService submissionService;

    private User user;
    private Problem problem;
    private Submission submission;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId("user-1");
        user.setUsername("alice");
        user.setTotalPoints(0);
        user.setProblemsSolved(0);
        user.setSolvedProblemIds(new java.util.ArrayList<>());

        problem = new Problem();
        problem.setId("problem-1");
        problem.setPoints(150);
        problem.setDifficulty("HARD");

        submission = Submission.builder()
            .id("submission-1")
            .userId(user.getId())
            .problemId(problem.getId())
            .language("JAVA")
            .build();
    }

    @Test
    void firstAcceptedSubmissionAwardsPoints() {
        when(userSolvedProblemRepository.insert(any(UserSolvedProblem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongoTemplate.updateFirst(any(), any(), any(Class.class))).thenReturn(UpdateResult.acknowledged(1, 1L, null));

        submissionService.applyAcceptedSubmission(user, problem, submission);

        ArgumentCaptor<UserSolvedProblem> solvedProblemCaptor = ArgumentCaptor.forClass(UserSolvedProblem.class);
        verify(userSolvedProblemRepository).insert(solvedProblemCaptor.capture());
        verify(mongoTemplate).updateFirst(any(), any(), any(Class.class));

        UserSolvedProblem solvedProblem = solvedProblemCaptor.getValue();
        assertEquals(user.getId(), solvedProblem.getUserId());
        assertEquals(problem.getId(), solvedProblem.getProblemId());
        assertEquals(submission.getId(), solvedProblem.getSubmissionId());
        assertEquals(problem.getPoints(), solvedProblem.getPointsAwarded());
        assertEquals(problem.getDifficulty(), solvedProblem.getDifficulty());
        assertEquals(submission.getLanguage(), solvedProblem.getLanguage());
        verify(userSolvedProblemRepository).save(solvedProblem);
        assertEquals(UserSolvedProblem.RewardStatus.APPLIED, solvedProblem.getRewardStatus());
        assertNotNull(solvedProblem.getSolvedAt());
    }

    @Test
    void repeatedAcceptedSubmissionDoesNotAwardPointsAgain() {
        when(userSolvedProblemRepository.insert(any(UserSolvedProblem.class)))
            .thenThrow(new DuplicateKeyException("duplicate key error"));
        UserSolvedProblem applied = solvedProblem(UserSolvedProblem.RewardStatus.APPLIED);
        when(userSolvedProblemRepository.findByUserIdAndProblemId(user.getId(), problem.getId()))
            .thenReturn(java.util.Optional.of(applied));

        submissionService.applyAcceptedSubmission(user, problem, submission);

        verify(userSolvedProblemRepository).insert(any(UserSolvedProblem.class));
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void concurrentAcceptedSubmissionsAwardOnce() {
        final int[] insertionCount = {0};
        doAnswer(invocation -> {
            if (insertionCount[0]++ > 0) {
                throw new DuplicateKeyException("duplicate key error");
            }
            return invocation.getArgument(0);
        }).when(userSolvedProblemRepository).insert(any(UserSolvedProblem.class));

        when(mongoTemplate.updateFirst(any(), any(), any(Class.class))).thenReturn(UpdateResult.acknowledged(1, 1L, null));

        submissionService.applyAcceptedSubmission(user, problem, submission);
        submissionService.applyAcceptedSubmission(user, problem, submission);

        verify(userSolvedProblemRepository, times(2)).insert(any(UserSolvedProblem.class));
        verify(mongoTemplate).updateFirst(any(), any(), any(Class.class));
    }

    @Test
    void failedRewardLeavesRecoverableFailedRecord() {
        when(userSolvedProblemRepository.insert(any(UserSolvedProblem.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(mongoTemplate.updateFirst(any(), any(), any(Class.class)))
            .thenThrow(new IllegalStateException("temporary Mongo failure"));

        submissionService.applyAcceptedSubmission(user, problem, submission);

        ArgumentCaptor<UserSolvedProblem> captor = ArgumentCaptor.forClass(UserSolvedProblem.class);
        verify(userSolvedProblemRepository).save(captor.capture());
        assertEquals(UserSolvedProblem.RewardStatus.FAILED, captor.getValue().getRewardStatus());
    }

    @Test
    void appliedRecordIsNotRewardedAgain() {
        UserSolvedProblem applied = solvedProblem(UserSolvedProblem.RewardStatus.APPLIED);
        when(userSolvedProblemRepository.findByRewardStatusIn(any())).thenReturn(java.util.List.of(applied));

        submissionService.reconcilePendingRewards();

        verifyNoMoreInteractions(mongoTemplate);
        verify(userSolvedProblemRepository).findByRewardStatusIn(any());
    }

    @Test
    void reconciliationAwardsPendingSolveExactlyOnce() {
        UserSolvedProblem pending = solvedProblem(UserSolvedProblem.RewardStatus.PENDING);
        when(userSolvedProblemRepository.findByRewardStatusIn(any())).thenReturn(java.util.List.of(pending));
        when(mongoTemplate.updateFirst(any(), any(), any(Class.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        submissionService.reconcilePendingRewards();

        assertEquals(UserSolvedProblem.RewardStatus.APPLIED, pending.getRewardStatus());
        verify(mongoTemplate).updateFirst(any(), any(), any(Class.class));
        verify(userSolvedProblemRepository).save(pending);
    }

    private UserSolvedProblem solvedProblem(UserSolvedProblem.RewardStatus status) {
        UserSolvedProblem solvedProblem = new UserSolvedProblem();
        solvedProblem.setUserId(user.getId());
        solvedProblem.setProblemId(problem.getId());
        solvedProblem.setPointsAwarded(problem.getPoints());
        solvedProblem.setRewardStatus(status);
        return solvedProblem;
    }
}
