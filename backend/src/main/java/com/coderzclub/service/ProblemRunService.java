package com.coderzclub.service;

import com.coderzclub.model.Problem;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.TestCase;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProblemRunService {
    public static final String CUSTOM_STDIN_RESOURCE = "custom-stdin";

    private final Judge0ExecutionService executionService;
    private final ExecutionCompatibilityService compatibilityService;
    private final SubmissionLimitService limitService;

    public ProblemRunService(Judge0ExecutionService executionService,
                             ExecutionCompatibilityService compatibilityService,
                             SubmissionLimitService limitService) {
        this.executionService = executionService;
        this.compatibilityService = compatibilityService;
        this.limitService = limitService;
    }

    public ExecutionOutcome runPublicCases(String userId, Problem problem, String code, Integer languageId) {
        acquireRunSlot(userId, problem.getId());
        List<SubmissionJob.TestCase> publicTests = publicOnly(problem);
        compatibilityService.validate(problem.getExecutionMode(), languageId, code, publicTests, List.of(),
            problem.getTestcaseVersion());
        return executionService.executeTestCases(
            code, languageId, publicTests, List.of(), problem.getExecutionMode(), problem.getTestcaseVersion());
    }

    public java.util.Map<String, Object> runCustomStdin(String userId, String code, Integer languageId, String stdin) {
        acquireRunSlot(userId, CUSTOM_STDIN_RESOURCE);
        return executionService.executeCustomStdin(code, languageId, stdin);
    }

    private void acquireRunSlot(String userId, String resourceId) {
        SubmissionLimitDecision decision = limitService.tryAcquireRunSlot(userId, resourceId);
        if (!decision.isAllowed()) {
            throw new RunLimitExceededException(decision);
        }
    }

    static List<SubmissionJob.TestCase> publicOnly(Problem problem) {
        List<TestCase> publicCases = problem == null ? null : problem.getPublicTestCases();
        if (publicCases == null) {
            return List.of();
        }
        return publicCases.stream()
            .map(test -> new SubmissionJob.TestCase(test.getInput(), test.getOutput(), test.getExplanation()))
            .toList();
    }
}
