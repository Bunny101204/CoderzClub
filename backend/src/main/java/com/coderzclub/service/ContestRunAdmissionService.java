package com.coderzclub.service;

import com.coderzclub.config.ContestRunProperties;
import com.coderzclub.dto.ContestRunRequest;
import com.coderzclub.model.Contest;
import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ContestRunJob;
import com.coderzclub.model.ContestStatus;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.TestCase;
import com.coderzclub.model.User;
import com.coderzclub.queue.ContestRunPublishRejectedException;
import com.coderzclub.queue.ContestRunPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class ContestRunAdmissionService {
    private static final Logger logger = LoggerFactory.getLogger(ContestRunAdmissionService.class);

    private final Clock clock;
    private final MongoTemplate mongoTemplate;
    private final ContestAccessService contestAccessService;
    private final ContestProblemSnapshotService snapshotService;
    private final SubmissionValidator submissionValidator;
    private final SubmissionValidationService validationService;
    private final ExecutionCompatibilityService compatibilityService;
    private final ContestRunQueueAdmissionService queueAdmissionService;
    private final SubmissionLimitService submissionLimitService;
    private final ContestRunPublisher publisher;
    private final ContestRunProperties properties;
    private final ContestRunMetrics metrics;

    public ContestRunAdmissionService(
        Clock clock,
        MongoTemplate mongoTemplate,
        ContestAccessService contestAccessService,
        ContestProblemSnapshotService snapshotService,
        SubmissionValidator submissionValidator,
        SubmissionValidationService validationService,
        ExecutionCompatibilityService compatibilityService,
        ContestRunQueueAdmissionService queueAdmissionService,
        SubmissionLimitService submissionLimitService,
        ContestRunPublisher publisher,
        ContestRunProperties properties,
        ContestRunMetrics metrics
    ) {
        this.clock = clock;
        this.mongoTemplate = mongoTemplate;
        this.contestAccessService = contestAccessService;
        this.snapshotService = snapshotService;
        this.submissionValidator = submissionValidator;
        this.validationService = validationService;
        this.compatibilityService = compatibilityService;
        this.queueAdmissionService = queueAdmissionService;
        this.submissionLimitService = submissionLimitService;
        this.publisher = publisher;
        this.properties = properties;
        this.metrics = metrics;
    }

    public ContestRunJob admit(User user, String contestId, String problemId, ContestRunRequest request) {
        Instant now = clock.instant();

        if (request == null) {
            metrics.rejected("VALIDATION");
            throw new IllegalArgumentException("code is required");
        }
        try {
            submissionValidator.validateCode(request.getCode());
            submissionValidator.validateLanguageId(request.getLanguageId());
        } catch (IllegalArgumentException e) {
            metrics.rejected("VALIDATION");
            throw e;
        }

        if (user == null || !user.canAuthenticate() || contestId == null || contestId.isBlank()
            || problemId == null || problemId.isBlank()) {
            metrics.rejected("NOT_FOUND");
            throw ContestAdmissionException.notFound();
        }

        Contest contest = mongoTemplate.findById(contestId, Contest.class);
        if (contest == null || !contest.isPublished()) {
            metrics.rejected("NOT_FOUND");
            throw ContestAdmissionException.notFound();
        }

        ContestAccessService.UserBatchScope scope = contestAccessService.scopeForUser(user.getId());
        if (!contestAccessService.isEligible(user, contest, scope, now)) {
            metrics.rejected("NOT_FOUND");
            throw ContestAdmissionException.notFound();
        }

        ContestStatus status = contestAccessService.status(contest, now);
        if (status == ContestStatus.UPCOMING) {
            metrics.rejected("NOT_LIVE");
            throw ContestAdmissionException.notLive();
        }
        if (status == ContestStatus.ENDED) {
            metrics.rejected("ENDED");
            throw ContestAdmissionException.ended();
        }
        if (status != ContestStatus.LIVE) {
            metrics.rejected("NOT_FOUND");
            throw ContestAdmissionException.notFound();
        }

        String canonicalProblemId = canonicalContestProblemId(contest, problemId);
        if (canonicalProblemId == null) {
            metrics.rejected("NOT_FOUND");
            throw ContestAdmissionException.notFound();
        }

        ContestProblemSnapshot snapshot = snapshotService.require(contest.getId(), canonicalProblemId);
        List<TestCase> publicCases = snapshot.getPublicTestCases();
        if (publicCases == null || publicCases.isEmpty()) {
            metrics.rejected("NO_PUBLIC_TESTS");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This contest problem has no public tests for Run");
        }

        try {
            Problem publicOnly = snapshot.toValidationProblem();
            publicOnly.setHiddenTestCases(List.of());
            submissionValidator.validateProblemTestCases(publicOnly);
        } catch (IllegalArgumentException e) {
            metrics.rejected("VALIDATION");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Problem configuration error: " + e.getMessage());
        }
        validationService.validateSubmissionRequest(canonicalProblemId, request.getCode(), request.getLanguageId());

        List<SubmissionJob.TestCase> publicTests = convertTests(publicCases);
        try {
            compatibilityService.validate(snapshot.getExecutionMode(), request.getLanguageId(),
                request.getCode(), publicTests, List.of(), snapshot.getTestcaseVersion());
        } catch (IncompatibleExecutionException incompatible) {
            metrics.rejected("INCOMPATIBLE");
            throw incompatible;
        }

        requireQueueHealthy();
        requireRunSlot(user.getId(), canonicalProblemId);

        Date createdAt = Date.from(now);
        ContestRunJob job = new ContestRunJob();
        job.setUserId(user.getId());
        job.setContestId(contest.getId());
        job.setProblemId(canonicalProblemId);
        job.setContestProblemSnapshotId(snapshot.getId());
        job.setCode(request.getCode());
        job.setLanguage(request.getLanguage());
        job.setLanguageId(request.getLanguageId());
        job.setStatus(ContestRunJob.Status.QUEUED);
        job.setCreatedAt(createdAt);
        job.setAttemptCount(0);
        job.setMaxAttempts(properties.getMaxAttempts());
        job.setTotalTests(publicTests.size());
        job.setCompletedTests(0);
        job.setExpiresAt(new Date(createdAt.getTime() + ttlMillis()));
        mongoTemplate.save(job);

        try {
            publisher.publishRun(job.getId());
        } catch (ContestRunPublishRejectedException capacityRejection) {
            mongoTemplate.remove(job);
            metrics.rejected("queue_capacity");
            logger.warn("contest_run_publish_rejected reason=queue_capacity runId={}", job.getId());
            throw new SubmissionAdmissionHttpException(HttpStatus.TOO_MANY_REQUESTS, Map.of(
                "error", "Run is busy. Please retry.",
                "reason", "QUEUE_CAPACITY",
                "retryAfterSeconds", 5
            ));
        } catch (Exception publishFailure) {
            mongoTemplate.remove(job);
            metrics.rejected("broker_unavailable");
            logger.warn("contest_run_publish_failed reason=broker_unavailable runId={}", job.getId());
            throw new SubmissionAdmissionHttpException(HttpStatus.SERVICE_UNAVAILABLE, Map.of(
                "error", "Run service is unavailable. Please retry.",
                "reason", "BROKER_UNAVAILABLE"
            ));
        }

        metrics.admitted();
        logger.info("contest_run_admitted status=QUEUED");
        return job;
    }

    private void requireQueueHealthy() {
        ContestRunQueueAdmissionService.Admission admission = queueAdmissionService.check();
        if (admission.allowed()) {
            return;
        }
        metrics.rejected(metricReason(admission.reason()));
        logger.info("contest_run_queue_rejected reason={} depth={} oldestAgeSeconds={}",
            admission.reason(), admission.depth(), admission.oldestAgeSeconds());
        HttpStatus status = "QUEUE_UNAVAILABLE".equals(admission.reason()) || "DISABLED".equals(admission.reason())
            ? HttpStatus.SERVICE_UNAVAILABLE
            : HttpStatus.TOO_MANY_REQUESTS;
        throw new SubmissionAdmissionHttpException(status, Map.of(
            "error", "Run is busy. Please retry.",
            "reason", admission.reason(),
            "retryAfterSeconds", 5
        ));
    }

    private static String metricReason(String admissionReason) {
        if ("QUEUE_DEPTH".equals(admissionReason)) {
            return "queue_depth";
        }
        if ("QUEUE_AGE".equals(admissionReason)) {
            return "queue_age";
        }
        return admissionReason;
    }

    private void requireRunSlot(String userId, String resourceId) {
        SubmissionLimitDecision decision = submissionLimitService.tryAcquireRunSlot(userId, resourceId);
        if (decision.isAllowed()) {
            return;
        }
        metrics.rejected(decision.getReason());
        throw new RunLimitExceededException(decision);
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

    private long ttlMillis() {
        return Math.max(5, properties.getJobTtlMinutes()) * 60_000L;
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
