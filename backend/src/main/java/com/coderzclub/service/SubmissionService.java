package com.coderzclub.service;

import com.coderzclub.model.Submission;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.User;
import com.coderzclub.model.Problem;
import com.coderzclub.model.UserSolvedProblem;
import com.coderzclub.repository.SubmissionRepository;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.UserSolvedProblemRepository;
import com.coderzclub.repository.SubmissionTestResultRepository;
import com.coderzclub.model.SubmissionTestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Date;
import java.util.Optional;

@Service
public class SubmissionService {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionService.class);

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private SubmissionTestResultRepository resultRepository;

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserSolvedProblemRepository userSolvedProblemRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserService userService;

    @Autowired(required = false)
    private MongoTransactionManager mongoTransactionManager;

    /**
     * Create a submission record from a completed job
     */
    public void createSubmissionFromJob(SubmissionJob job) {
        try {
            Optional<User> userOpt = userRepository.findById(job.getUserId());
            Optional<Problem> problemOpt = problemRepository.findById(job.getProblemId());

            if (!userOpt.isPresent() || !problemOpt.isPresent()) {
                logger.error("User or problem not found for job {}", job.getId());
                return;
            }

            User user = userOpt.get();
            Problem problem = problemOpt.get();

            java.util.List<SubmissionTestResult> storedResults = resultRepository.findByJobIdOrderByTestcaseIndexAsc(job.getId());
            long passedCount = storedResults.isEmpty() && job.getTestResults() != null
                ? job.getTestResults().stream().filter(SubmissionJob.TestResult::isPassed).count()
                : storedResults.stream().filter(SubmissionTestResult::isPassed).count();
            // Count passed test cases
            if (storedResults.isEmpty() && job.getTestResults() == null) {
                passedCount = 0;
            }

            // Create submission
            Submission submission = Submission.builder()
                .userId(user.getId())
                .problemId(job.getProblemId())
                .code(job.getCode())
                .language(job.getLanguage())
                .result(job.getFinalResult())
                .output(buildOutputSummary(job, storedResults))
                .runtime(job.getTotalRuntime())
                .memory(job.getTotalMemory())
                .errorMessage(job.getErrorMessage())
                .passedTestCases((int) passedCount)
                .totalTestCases(job.getTotalTests())
                .executionDetails(buildExecutionDetails(job, storedResults))
                .build();

            submission = submissionRepository.save(submission);

            // Update user stats if solution is correct
            if ("ACCEPTED".equals(submission.getResult())) {
                applyAcceptedSubmission(user, problem, submission);
                userRepository.findById(user.getId()).ifPresent(leaderboardService::update);
            }

            // Update streak
            userService.updateUserStreak(user.getId());
            userService.recordFinalSubmission(submission);

            logger.info("Created submission {} from job {} with result {}",
                submission.getId(), job.getId(), job.getFinalResult());

        } catch (Exception e) {
            logger.error("Failed to create submission from job {}", job.getId(), e);
        }
    }

    /**
     * Build output summary from job results
     */
    private String buildOutputSummary(SubmissionJob job, java.util.List<SubmissionTestResult> storedResults) {
        if ((storedResults == null || storedResults.isEmpty()) && (job.getTestResults() == null || job.getTestResults().isEmpty())) {
            return "No test results";
        }
        long passed = storedResults.isEmpty() ? job.getTestResults().stream().filter(SubmissionJob.TestResult::isPassed).count()
            : storedResults.stream().filter(SubmissionTestResult::isPassed).count();
        long total = storedResults.isEmpty() ? job.getTestResults().size() : storedResults.size();

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Passed %d/%d test cases", passed, total));

        // Add details for first failed public test only; do not reveal hidden test expected output
        if (!storedResults.isEmpty()) {
            storedResults.stream().filter(r -> !r.isPassed()).findFirst().ifPresent(failed -> {
                summary.append(failed.getTestcaseType() == SubmissionTestResult.TestcaseType.PUBLIC
                    ? ". Failed case - Expected: " + failed.getExpectedOutput() + ", Got: " + failed.getActualOutput()
                    : ". Failed on a hidden testcase");
            });
        }

        return summary.toString();
    }

    /**
     * Build execution details map
     */
    private java.util.Map<String, Object> buildExecutionDetails(SubmissionJob job, java.util.List<SubmissionTestResult> storedResults) {
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("jobId", job.getId());
        details.put("completedAt", job.getCompletedAt());

        // Sanitize test results for storage/return: never include hidden test input/expected
        java.util.List<java.util.Map<String, Object>> sanitized = new java.util.ArrayList<>();
        int publicCount = job.getPublicTestCases() != null ? job.getPublicTestCases().size() : 0;
        if (!storedResults.isEmpty()) {
            for (SubmissionTestResult r : storedResults) {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("type", r.getTestcaseType().name().toLowerCase());
                m.put("passed", r.isPassed());
                m.put("runtime", r.getRuntime());
                m.put("memory", r.getMemory());
                if (r.getTestcaseType() == SubmissionTestResult.TestcaseType.PUBLIC) {
                    m.put("input", r.getInput());
                    m.put("expectedOutput", r.getExpectedOutput());
                    m.put("actualOutput", r.getActualOutput());
                }
                sanitized.add(m);
            }
        } else if (job.getTestResults() != null) {
            for (int i = 0; i < job.getTestResults().size(); i++) {
                SubmissionJob.TestResult r = job.getTestResults().get(i);
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                if (i < publicCount) {
                    m.put("input", r.getInput());
                    m.put("expectedOutput", r.getExpectedOutput());
                    m.put("actualOutput", r.getActualOutput());
                    m.put("passed", r.isPassed());
                    m.put("runtime", r.getRuntime());
                    m.put("memory", r.getMemory());
                    m.put("errorType", r.getErrorType());
                    m.put("errorMessage", r.getErrorMessage());
                } else {
                    // Hidden tests: only record pass/fail summary
                    m.put("type", "hidden");
                    m.put("status", r.isPassed() ? "PASSED" : "FAILED");
                    if (!r.isPassed()) m.put("message", "Failed on hidden testcase");
                }
                sanitized.add(m);
            }
        }

        details.put("testResults", sanitized);
        return details;
    }

    /**
     * Apply accepted submission updates atomically.
     */
    public void applyAcceptedSubmission(User user, Problem problem, Submission submission) {
        UserSolvedProblem solvedProblem = createSolvedProblem(user, problem, submission);

        try {
            if (mongoTransactionManager != null) {
                new org.springframework.transaction.support.TransactionTemplate(mongoTransactionManager)
                    .execute(status -> {
                        userSolvedProblemRepository.insert(solvedProblem);
                        applyRewardAtomically(solvedProblem);
                        markApplied(solvedProblem);
                        return null;
                    });
            } else {
                reconcileWithoutTransaction(solvedProblem);
            }
            logger.info("Awarded {} points to user {} for problem {}",
                problem.getPoints(), user.getUsername(), problem.getId());
        } catch (Exception e) {
            logger.warn("Mongo transaction unavailable for user {} and problem {}; using reconciliation fallback",
                user.getId(), problem.getId(), e);
            reconcileWithoutTransaction(solvedProblem);
        }
    }

    private UserSolvedProblem createSolvedProblem(User user, Problem problem, Submission submission) {
        UserSolvedProblem solvedProblem = new UserSolvedProblem();
        solvedProblem.setUserId(user.getId());
        solvedProblem.setProblemId(problem.getId());
        solvedProblem.setSubmissionId(submission.getId());
        solvedProblem.setSolvedAt(new Date());
        solvedProblem.setPointsAwarded(problem.getPoints());
        solvedProblem.setDifficulty(problem.getDifficulty());
        solvedProblem.setLanguage(submission.getLanguage());
        solvedProblem.setRewardStatus(UserSolvedProblem.RewardStatus.PENDING);
        return solvedProblem;
    }

    private void reconcileWithoutTransaction(UserSolvedProblem candidate) {
        UserSolvedProblem solvedProblem;
        try {
            solvedProblem = userSolvedProblemRepository.insert(candidate);
        } catch (DuplicateKeyException duplicate) {
            solvedProblem = userSolvedProblemRepository
                .findByUserIdAndProblemId(candidate.getUserId(), candidate.getProblemId())
                .orElse(null);
            if (solvedProblem == null) {
                logger.error("Solved record duplicate detected but could not be read for user {} problem {}",
                    candidate.getUserId(), candidate.getProblemId());
                return;
            }
        }

        reconcileExistingReward(solvedProblem);
    }

    private void reconcileExistingReward(UserSolvedProblem solvedProblem) {
        if (solvedProblem.getRewardStatus() == UserSolvedProblem.RewardStatus.APPLIED) {
            return;
        }

        try {
            applyRewardAtomically(solvedProblem);
            markApplied(solvedProblem);
        } catch (Exception rewardFailure) {
            solvedProblem.setRewardStatus(UserSolvedProblem.RewardStatus.FAILED);
            try {
                userSolvedProblemRepository.save(solvedProblem);
            } catch (Exception statusFailure) {
                logger.error("Failed to persist reward failure for solved record {}", solvedProblem.getId(), statusFailure);
            }
            logger.error("Reward remains recoverable for user {} problem {}",
                solvedProblem.getUserId(), solvedProblem.getProblemId(), rewardFailure);
        }
    }

    private void applyRewardAtomically(UserSolvedProblem solvedProblem) {
        Query query = Query.query(Criteria.where("_id").is(solvedProblem.getUserId())
            .and("solvedProblemIds").ne(solvedProblem.getProblemId()));
        Update update = new Update()
            .inc("totalPoints", solvedProblem.getPointsAwarded())
            .inc("problemsSolved", 1)
            .addToSet("solvedProblemIds", solvedProblem.getProblemId());

        com.mongodb.client.result.UpdateResult result = mongoTemplate.updateFirst(query, update, User.class);
        if (result.getModifiedCount() == 0
            && !mongoTemplate.exists(Query.query(Criteria.where("_id").is(solvedProblem.getUserId())
                .and("solvedProblemIds").is(solvedProblem.getProblemId())), User.class)) {
            throw new IllegalStateException("User reward update did not modify a user");
        }
    }

    private void markApplied(UserSolvedProblem solvedProblem) {
        solvedProblem.setRewardStatus(UserSolvedProblem.RewardStatus.APPLIED);
        userSolvedProblemRepository.save(solvedProblem);
    }

    @Scheduled(fixedDelayString = "${submission.reward-reconciliation.fixed-delay-ms:60000}")
    public void reconcilePendingRewards() {
        userSolvedProblemRepository.findByRewardStatusIn(java.util.List.of(
                UserSolvedProblem.RewardStatus.PENDING, UserSolvedProblem.RewardStatus.FAILED))
            .forEach(this::reconcileExistingReward);
    }
}