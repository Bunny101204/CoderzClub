package com.coderzclub.service;

import com.coderzclub.dto.ContestSubmissionJobRequest;
import com.coderzclub.model.Contest;
import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.ContestStatus;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.TestCase;
import com.coderzclub.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Contest-specific durable submit admission. Once this returns a job, workers
 * must not re-check contest time or eligibility.
 */
@Service
public class ContestSubmissionAdmissionService {
    private static final Logger logger = LoggerFactory.getLogger(ContestSubmissionAdmissionService.class);

    private final Clock clock;
    private final MongoTemplate mongoTemplate;
    private final ContestAccessService contestAccessService;
    private final ContestProblemSnapshotService snapshotService;
    private final SubmissionValidator submissionValidator;
    private final SubmissionValidationService validationService;
    private final ExecutionCompatibilityService compatibilityService;
    private final SubmissionLimitService submissionLimitService;
    private final SubmissionQueueAdmissionService queueAdmissionService;
    private final SubmissionJobService jobService;

    public ContestSubmissionAdmissionService(
        Clock clock,
        MongoTemplate mongoTemplate,
        ContestAccessService contestAccessService,
        ContestProblemSnapshotService snapshotService,
        SubmissionValidator submissionValidator,
        SubmissionValidationService validationService,
        ExecutionCompatibilityService compatibilityService,
        SubmissionLimitService submissionLimitService,
        SubmissionQueueAdmissionService queueAdmissionService,
        SubmissionJobService jobService
    ) {
        this.clock = clock;
        this.mongoTemplate = mongoTemplate;
        this.contestAccessService = contestAccessService;
        this.snapshotService = snapshotService;
        this.submissionValidator = submissionValidator;
        this.validationService = validationService;
        this.compatibilityService = compatibilityService;
        this.submissionLimitService = submissionLimitService;
        this.queueAdmissionService = queueAdmissionService;
        this.jobService = jobService;
    }

    public SubmissionJob admit(User user, String contestId, String problemId, ContestSubmissionJobRequest request) {
        Instant now = clock.instant();

        if (user == null || !user.canAuthenticate() || contestId == null || contestId.isBlank()
            || problemId == null || problemId.isBlank()) {
            throw ContestAdmissionException.notFound();
        }

        Contest contest = mongoTemplate.findById(contestId, Contest.class);
        if (contest == null || !contest.isPublished()) {
            throw ContestAdmissionException.notFound();
        }

        ContestAccessService.UserBatchScope scope = contestAccessService.scopeForUser(user.getId());
        if (!contestAccessService.isEligible(user, contest, scope, now)) {
            throw ContestAdmissionException.notFound();
        }

        ContestStatus status = contestAccessService.status(contest, now);
        if (status == ContestStatus.UPCOMING) {
            throw ContestAdmissionException.notLive();
        }
        if (status == ContestStatus.ENDED) {
            throw ContestAdmissionException.ended();
        }
        if (status != ContestStatus.LIVE) {
            throw ContestAdmissionException.notFound();
        }

        String canonicalProblemId = canonicalContestProblemId(contest, problemId);
        if (canonicalProblemId == null) {
            throw ContestAdmissionException.notFound();
        }

        ContestProblemSnapshot snapshot = snapshotService.require(contest.getId(), canonicalProblemId);

        submissionValidator.validateCode(request.getCode());
        submissionValidator.validateLanguageId(request.getLanguageId());
        try {
            submissionValidator.validateProblemTestCases(snapshot.toValidationProblem());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Problem configuration error: " + e.getMessage());
        }
        validationService.validateSubmissionRequest(canonicalProblemId, request.getCode(), request.getLanguageId());

        List<SubmissionJob.TestCase> publicTests = convertTests(snapshot.getPublicTestCases());
        List<SubmissionJob.TestCase> hiddenTests = convertTests(snapshot.getHiddenTestCases());
        compatibilityService.validate(snapshot.getExecutionMode(), request.getLanguageId(),
            request.getCode(), publicTests, hiddenTests, snapshot.getTestcaseVersion());

        requireQueueHealthy();
        requireSubmissionSlot(user.getId(), canonicalProblemId);

        int totalTests = publicTests.size() + hiddenTests.size();
        String idempotencyKey = request.getIdempotencyKey();
        SubmissionJob job = idempotencyKey == null || idempotencyKey.isBlank()
            ? jobService.createJob(
                user.getId(),
                canonicalProblemId,
                request.getCode(),
                request.getLanguage(),
                request.getLanguageId(),
                snapshot.getTestcaseVersion(),
                totalTests,
                snapshot.getExecutionMode(),
                request.getCodingDurationSeconds(),
                contest.getId(),
                now,
                snapshot.getId()
            )
            : jobService.createJob(
                user.getId(),
                canonicalProblemId,
                request.getCode(),
                request.getLanguage(),
                request.getLanguageId(),
                snapshot.getTestcaseVersion(),
                totalTests,
                snapshot.getExecutionMode(),
                request.getCodingDurationSeconds(),
                contest.getId(),
                now,
                snapshot.getId(),
                "contest-submission",
                idempotencyKey
            );

        logger.info("contest_submission_job_admitted jobId={} contestId={} snapshotId={} userId={} problemId={} contestSubmittedAt={}",
            job.getId(), contest.getId(), snapshot.getId(), user.getId(), canonicalProblemId, now);
        return job;
    }

    private String canonicalContestProblemId(Contest contest, String problemId) {
        List<String> contestProblems = contest.getProblemIds() == null ? List.of() : contest.getProblemIds();
        if (contestProblems.contains(problemId)) {
            return problemId;
        }
        Integer numericId;
        try {
            numericId = Integer.valueOf(problemId);
        } catch (NumberFormatException ignored) {
            return null;
        }
        for (ContestProblemSnapshot snapshot : snapshotService.findByContestId(contest.getId())) {
            if (numericId.equals(snapshot.getNumericId()) && snapshot.getProblemId() != null
                && contestProblems.contains(snapshot.getProblemId())) {
                return snapshot.getProblemId();
            }
        }
        return null;
    }

    private void requireSubmissionSlot(String userId, String problemId) {
        SubmissionLimitDecision decision = submissionLimitService.tryAcquireSubmissionSlot(userId, problemId);
        if (decision.isAllowed()) {
            return;
        }
        String reason = decision.getReason();
        if ("COOLDOWN".equals(reason)) {
            throw new SubmissionAdmissionHttpException(HttpStatus.TOO_MANY_REQUESTS, Map.of(
                "error", "Please wait before submitting again.",
                "retryAfterSeconds", submissionLimitService.getCooldownSeconds(userId)
            ));
        }
        if ("DAILY_LIMIT".equals(reason)) {
            throw new SubmissionAdmissionHttpException(HttpStatus.TOO_MANY_REQUESTS, Map.of(
                "error", "Daily submission limit exceeded."
            ));
        }
        if ("PROBLEM_LIMIT".equals(reason)) {
            throw new SubmissionAdmissionHttpException(HttpStatus.TOO_MANY_REQUESTS, Map.of(
                "error", "Daily submission limit for this problem exceeded."
            ));
        }
        if ("REDIS_UNAVAILABLE".equals(reason)) {
            throw new SubmissionAdmissionHttpException(HttpStatus.SERVICE_UNAVAILABLE, Map.of(
                "error", "Rate limit service unavailable. Please try again later."
            ));
        }
        throw new SubmissionAdmissionHttpException(HttpStatus.TOO_MANY_REQUESTS, Map.of(
            "error", "Submission limit exceeded."
        ));
    }

    private void requireQueueHealthy() {
        SubmissionQueueAdmissionService.Admission admission = queueAdmissionService.check();
        if (admission.allowed()) {
            return;
        }
        HttpStatus status = "QUEUE_UNAVAILABLE".equals(admission.reason())
            ? HttpStatus.SERVICE_UNAVAILABLE
            : HttpStatus.TOO_MANY_REQUESTS;
        throw new SubmissionAdmissionHttpException(status, Map.of(
            "error", "Submission service is busy. Please retry later.",
            "reason", admission.reason(),
            "retryAfterSeconds", 30
        ));
    }

    private static List<SubmissionJob.TestCase> convertTests(List<TestCase> testCases) {
        if (testCases == null) {
            return List.of();
        }
        return testCases.stream()
            .map(test -> new SubmissionJob.TestCase(test.getInput(), test.getOutput(), test.getExplanation()))
            .toList();
    }
}
