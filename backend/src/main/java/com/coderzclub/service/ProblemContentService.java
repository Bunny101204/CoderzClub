package com.coderzclub.service;

import com.coderzclub.dto.ProblemContentUpdate;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.Problem;
import org.springframework.stereotype.Service;

@Service
public class ProblemContentService {
    private final ProblemExecutionConfigService executionConfigService;
    private final SubmissionValidator submissionValidator;

    public ProblemContentService(ProblemExecutionConfigService executionConfigService,
                                 SubmissionValidator submissionValidator) {
        this.executionConfigService = executionConfigService;
        this.submissionValidator = submissionValidator;
    }

    public Problem apply(Problem existing, ProblemContentUpdate update) {
        if (existing == null) {
            throw new IllegalArgumentException("Problem is required");
        }
        if (update == null) {
            return existing;
        }
        if (update.getTitle() != null) {
            if (update.getTitle().isBlank()) {
                throw new IllegalArgumentException("title must not be blank");
            }
            existing.setTitle(update.getTitle().trim());
        }
        if (update.getStatement() != null) {
            if (update.getStatement().isBlank()) {
                throw new IllegalArgumentException("statement must not be blank");
            }
            existing.setStatement(update.getStatement());
        }
        if (update.getDifficulty() != null) {
            if (update.getDifficulty().isBlank()) {
                throw new IllegalArgumentException("difficulty must not be blank");
            }
            existing.setDifficulty(update.getDifficulty().trim().toUpperCase());
        }
        if (update.getCategory() != null) {
            existing.setCategory(update.getCategory());
        }
        if (update.getTags() != null) {
            existing.setTags(update.getTags());
        }
        if (update.getInputFormat() != null) {
            existing.setInputFormat(update.getInputFormat());
        }
        if (update.getOutputFormat() != null) {
            existing.setOutputFormat(update.getOutputFormat());
        }
        if (update.getConstraints() != null) {
            existing.setConstraints(update.getConstraints());
        }
        if (update.getExampleInput() != null) {
            existing.setExampleInput(update.getExampleInput());
        }
        if (update.getExampleOutput() != null) {
            existing.setExampleOutput(update.getExampleOutput());
        }
        if (update.getExampleExplanation() != null) {
            existing.setExampleExplanation(update.getExampleExplanation());
        }
        if (update.getPublicTestCases() != null) {
            existing.setPublicTestCases(update.getPublicTestCases());
        }
        if (update.getHiddenTestCases() != null) {
            existing.setHiddenTestCases(update.getHiddenTestCases());
        }
        if (update.getBundleId() != null) {
            existing.setBundleId(update.getBundleId().isBlank() ? null : update.getBundleId());
        }
        if (update.getPremium() != null) {
            existing.setPremium(update.getPremium());
        }
        if (update.getPoints() != null) {
            existing.setPoints(update.getPoints());
        }
        if (update.getEstimatedTime() != null) {
            existing.setEstimatedTime(update.getEstimatedTime());
        }
        if (update.getPublicTestCases() != null || update.getHiddenTestCases() != null) {
            submissionValidator.validateProblemTestCases(existing);
        }
        if (update.getExecutionMode() != null || update.getTestcaseVersion() != null) {
            ExecutionMode mode = update.getExecutionMode() == null
                ? existing.getExecutionMode()
                : ExecutionMode.fromValue(update.getExecutionMode());
            executionConfigService.apply(existing, mode, update.getTestcaseVersion());
        }
        return existing;
    }
}
