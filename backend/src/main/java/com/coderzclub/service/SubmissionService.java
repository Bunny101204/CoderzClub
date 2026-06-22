package com.coderzclub.service;

import com.coderzclub.model.Submission;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.User;
import com.coderzclub.model.Problem;
import com.coderzclub.repository.SubmissionRepository;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.repository.ProblemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SubmissionService {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionService.class);

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserService userService;

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

            // Count passed test cases
            long passedCount = job.getTestResults().stream()
                .filter(SubmissionJob.TestResult::isPassed)
                .count();

            // Create submission
            Submission submission = Submission.builder()
                .userId(user.getId())
                .problemId(job.getProblemId())
                .code(job.getCode())
                .language(job.getLanguage())
                .result(job.getFinalResult())
                .output(buildOutputSummary(job))
                .runtime(job.getTotalRuntime())
                .memory(job.getTotalMemory())
                .errorMessage(job.getErrorMessage())
                .passedTestCases((int) passedCount)
                .totalTestCases(job.getTotalTests())
                .executionDetails(buildExecutionDetails(job))
                .build();

            submission = submissionRepository.save(submission);

            // Update user stats if solution is correct
            if ("ACCEPTED".equals(submission.getResult())) {
                updateUserStats(user, problem);
            }

            // Update streak
            userService.updateUserStreak(user.getId());

            logger.info("Created submission {} from job {} with result {}",
                submission.getId(), job.getId(), job.getFinalResult());

        } catch (Exception e) {
            logger.error("Failed to create submission from job {}", job.getId(), e);
        }
    }

    /**
     * Build output summary from job results
     */
    private String buildOutputSummary(SubmissionJob job) {
        if (job.getTestResults() == null || job.getTestResults().isEmpty()) {
            return "No test results";
        }
        long passed = job.getTestResults().stream().filter(SubmissionJob.TestResult::isPassed).count();
        long total = job.getTestResults().size();

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Passed %d/%d test cases", passed, total));

        // Add details for first failed public test only; do not reveal hidden test expected output
        job.getTestResults().stream()
            .filter(r -> !r.isPassed())
            .findFirst()
            .ifPresent(failed -> {
                int failedIndex = job.getTestResults().indexOf(failed);
                int publicCount = job.getPublicTestCases() != null ? job.getPublicTestCases().size() : 0;
                if (failedIndex < publicCount) {
                    summary.append(". Failed case - Expected: ")
                           .append(failed.getExpectedOutput())
                           .append(", Got: ")
                           .append(failed.getActualOutput());
                } else {
                    summary.append(". Failed on a hidden testcase");
                }

                if (failed.getErrorMessage() != null) {
                    summary.append(" (").append(failed.getErrorMessage()).append(")");
                }
            });

        return summary.toString();
    }

    /**
     * Build execution details map
     */
    private java.util.Map<String, Object> buildExecutionDetails(SubmissionJob job) {
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("jobId", job.getId());
        details.put("completedAt", job.getCompletedAt());

        // Sanitize test results for storage/return: never include hidden test input/expected
        java.util.List<java.util.Map<String, Object>> sanitized = new java.util.ArrayList<>();
        int publicCount = job.getPublicTestCases() != null ? job.getPublicTestCases().size() : 0;
        if (job.getTestResults() != null) {
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
     * Update user stats after successful submission
     */
    private void updateUserStats(User user, Problem problem) {
        // Check if user already solved this problem
        if (user.getSolvedProblemIds() != null && 
            user.getSolvedProblemIds().contains(problem.getId())) {
            return; // Already solved, don't award points again
        }
        
        // Add problem to solved list
        if (user.getSolvedProblemIds() == null) {
            user.setSolvedProblemIds(new java.util.ArrayList<>());
        }
        user.getSolvedProblemIds().add(problem.getId());
        
        // Update stats
        user.setProblemsSolved(user.getProblemsSolved() + 1);
        user.setTotalPoints(user.getTotalPoints() + problem.getPoints());
        
        // Save the updated user
        userRepository.save(user);
        
        logger.info("Updated user {} stats: +{} points, total problems solved: {}",
            user.getUsername(), problem.getPoints(), user.getProblemsSolved());
    }
}