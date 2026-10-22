package com.coderzclub.service;

import com.coderzclub.config.SubmissionLimitsConfig;
import com.coderzclub.dto.CodeDraftResponse;
import com.coderzclub.model.CodeDraft;
import com.coderzclub.model.Problem;
import com.coderzclub.model.User;
import com.coderzclub.repository.ProblemRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Current-draft persistence. Updates match identity and revision in one Mongo operation.
 */
@Service
public class CodeDraftService {
    public static final String PRACTICE_SCOPE_KEY = "practice";

    private final MongoTemplate mongoTemplate;
    private final ProblemRepository problemRepository;
    private final ProblemAccessService problemAccessService;
    private final ContestService contestService;
    private final SubmissionValidator submissionValidator;
    private final SubmissionLimitsConfig limitsConfig;
    private final Clock clock;
    private final DraftMetrics metrics;

    public CodeDraftService(
        MongoTemplate mongoTemplate,
        ProblemRepository problemRepository,
        ProblemAccessService problemAccessService,
        ContestService contestService,
        SubmissionValidator submissionValidator,
        SubmissionLimitsConfig limitsConfig,
        Clock clock,
        DraftMetrics metrics
    ) {
        this.mongoTemplate = mongoTemplate;
        this.problemRepository = problemRepository;
        this.problemAccessService = problemAccessService;
        this.contestService = contestService;
        this.submissionValidator = submissionValidator;
        this.limitsConfig = limitsConfig;
        this.clock = clock;
        this.metrics = metrics;
    }

    public static String contestScopeKey(String canonicalContestId) {
        if (canonicalContestId == null || canonicalContestId.isBlank()) {
            throw new IllegalArgumentException("contestId is required");
        }
        return "contest:" + canonicalContestId;
    }

    public CodeDraftResponse loadPractice(User user, String problemId, Integer languageId) {
        Problem problem = requirePracticeProblem(user, problemId);
        submissionValidator.validateLanguageId(languageId);
        metrics.load();
        return toResponse(find(user.getId(), PRACTICE_SCOPE_KEY, problem.getId(), languageId));
    }

    public CodeDraftResponse savePractice(User user, String problemId, Integer languageId, String code, Long expectedRevision) {
        Problem problem = requirePracticeProblem(user, problemId);
        validatePayload(languageId, code);
        return save(
            user.getId(),
            CodeDraft.ScopeType.PRACTICE,
            PRACTICE_SCOPE_KEY,
            null,
            problem.getId(),
            languageId,
            code,
            expectedRevision
        );
    }

    public CodeDraftResponse loadContest(User user, String contestId, String problemId, Integer languageId) {
        ContestService.ContestDraftTarget target = requireContestTarget(user, contestId, problemId);
        submissionValidator.validateLanguageId(languageId);
        metrics.load();
        return toResponse(find(user.getId(), contestScopeKey(target.contestId()), target.problemId(), languageId));
    }

    public CodeDraftResponse saveContest(
        User user,
        String contestId,
        String problemId,
        Integer languageId,
        String code,
        Long expectedRevision
    ) {
        ContestService.ContestDraftTarget target = requireContestTarget(user, contestId, problemId);
        validatePayload(languageId, code);
        return save(
            user.getId(),
            CodeDraft.ScopeType.CONTEST,
            contestScopeKey(target.contestId()),
            target.contestId(),
            target.problemId(),
            languageId,
            code,
            expectedRevision
        );
    }

    private ContestService.ContestDraftTarget requireContestTarget(User user, String contestId, String problemId) {
        requireUser(user);
        return contestService.requireContestDraftTarget(user, contestId, problemId);
    }

    private Problem requirePracticeProblem(User user, String rawId) {
        requireUser(user);
        if (rawId == null || rawId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found");
        }
        Optional<Problem> found = problemRepository.findById(rawId);
        if (found.isEmpty()) {
            try {
                found = problemRepository.findByNumericId(Integer.valueOf(rawId.trim()));
            } catch (NumberFormatException ignored) {
                found = Optional.empty();
            }
        }
        Problem problem = found.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found"));
        problemAccessService.requirePracticeExecute(user, problem);
        if (problem.getId() == null || problem.getId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found");
        }
        return problem;
    }

    private static void requireUser(User user) {
        if (user == null || user.isDeleted() || user.getId() == null || user.getId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
    }

    private void validatePayload(Integer languageId, String code) {
        submissionValidator.validateLanguageId(languageId);
        if (code == null) {
            throw new IllegalArgumentException("Code is required");
        }
        if (code.length() > limitsConfig.getMaxCodeLength()) {
            throw new IllegalArgumentException(
                "Code exceeds maximum length of " + limitsConfig.getMaxCodeLength() + " characters"
            );
        }
    }

    private CodeDraftResponse save(
        String userId,
        CodeDraft.ScopeType scopeType,
        String scopeKey,
        String contestId,
        String problemId,
        Integer languageId,
        String code,
        Long expectedRevision
    ) {
        long expected = expectedRevision == null ? 0L : expectedRevision;
        if (expected < 0) {
            throw new IllegalArgumentException("expectedRevision is invalid");
        }
        Instant now = clock.instant();
        if (expected == 0L) {
            return insertNew(userId, scopeType, scopeKey, contestId, problemId, languageId, code, now);
        }
        Query query = identity(userId, scopeKey, problemId, languageId)
            .addCriteria(Criteria.where("revision").is(expected));
        Update update = new Update()
            .set("code", code)
            .set("revision", expected + 1)
            .set("updatedAt", now);
        try {
            CodeDraft updated = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                CodeDraft.class
            );
            if (updated == null) {
                metrics.conflict();
                throw conflict(userId, scopeKey, problemId, languageId);
            }
            metrics.sync();
            return toResponse(updated);
        } catch (DraftConflictException conflict) {
            throw conflict;
        } catch (DataAccessException ex) {
            metrics.syncFailure();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Draft sync is unavailable");
        }
    }

    private CodeDraftResponse insertNew(
        String userId,
        CodeDraft.ScopeType scopeType,
        String scopeKey,
        String contestId,
        String problemId,
        Integer languageId,
        String code,
        Instant now
    ) {
        CodeDraft created = new CodeDraft();
        created.setUserId(userId);
        created.setScopeType(scopeType);
        created.setScopeKey(scopeKey);
        created.setContestId(contestId);
        created.setProblemId(problemId);
        created.setLanguageId(languageId);
        created.setCode(code);
        created.setRevision(1);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        try {
            mongoTemplate.insert(created);
            metrics.sync();
            return toResponse(created);
        } catch (DuplicateKeyException ex) {
            metrics.conflict();
            throw conflict(userId, scopeKey, problemId, languageId);
        } catch (DataAccessException ex) {
            metrics.syncFailure();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Draft sync is unavailable");
        }
    }

    private DraftConflictException conflict(String userId, String scopeKey, String problemId, Integer languageId) {
        CodeDraft current = find(userId, scopeKey, problemId, languageId);
        if (current == null) {
            return new DraftConflictException(null, 0, null);
        }
        return new DraftConflictException(current.getCode(), current.getRevision(), current.getUpdatedAt());
    }

    private CodeDraft find(String userId, String scopeKey, String problemId, Integer languageId) {
        return mongoTemplate.findOne(identity(userId, scopeKey, problemId, languageId), CodeDraft.class);
    }

    private static Query identity(String userId, String scopeKey, String problemId, Integer languageId) {
        return Query.query(Criteria.where("userId").is(userId)
            .and("scopeKey").is(scopeKey)
            .and("problemId").is(problemId)
            .and("languageId").is(languageId));
    }

    private static CodeDraftResponse toResponse(CodeDraft draft) {
        if (draft == null) {
            return new CodeDraftResponse(null, 0, null);
        }
        return new CodeDraftResponse(draft.getCode(), draft.getRevision(), draft.getUpdatedAt());
    }
}
