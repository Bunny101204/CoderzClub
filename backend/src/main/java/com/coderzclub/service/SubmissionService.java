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

        // Add details for failed tests
        job.getTestResults().stream()
            .filter(r -> !r.isPassed())
            .findFirst()
            .ifPresent(failed -> {
                summary.append(". Failed case - Expected: ")
                       .append(failed.getExpectedOutput())
                       .append(", Got: ")
                       .append(failed.getActualOutput());
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
        details.put("testResults", job.getTestResults());
        return details;
    }

    /**
     * Update user stats after successful submission
     */
    private void updateUserStats(User user, Problem problem) {
        // This would be implemented based on your existing logic
        // For now, just log
        logger.info("User {} solved problem {}", user.getUsername(), problem.getTitle());
    }
}