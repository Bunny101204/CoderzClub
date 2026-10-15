package com.coderzclub.service;

import com.coderzclub.config.ContestProperties;
import com.coderzclub.dto.ContestAdminResponse;
import com.coderzclub.dto.ContestProblemDetailResponse;
import com.coderzclub.dto.ContestProblemSummary;
import com.coderzclub.dto.ContestUserResponse;
import com.coderzclub.dto.ContestWriteRequest;
import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Contest;
import com.coderzclub.model.ContestAudienceType;
import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.ContestScoringMode;
import com.coderzclub.model.ContestStatus;
import com.coderzclub.model.Problem;
import com.coderzclub.model.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Contest administration, derived status, and user eligibility.
 *
 * Lifecycle mutation rules (enforced here, not in the UI):
 * <ul>
 *   <li>DRAFT / UPCOMING: title, description, times, problems, and audience may change;
 *       contests may be published, unpublished (UPCOMING only), or deleted.</li>
 *   <li>LIVE: title, description, and endAt may change. audience, problemIds, and startAt
 *       cannot change. A LIVE PATCH cannot set endAt &le; now. Cannot delete or unpublish.</li>
 *   <li>ENDED: historical. Cannot update, delete, publish, or unpublish.</li>
 * </ul>
 * Status is never stored; it is computed from published + startAt/endAt + {@link Clock}.
 */
@Service
public class ContestService {
    public static final int MAX_PAGE_SIZE = 50;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PROBLEMS = 80;
    public static final int MAX_TITLE_LENGTH = 120;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    private final MongoTemplate mongoTemplate;
    private final ContestProperties contestProperties;
    private final Clock clock;
    private final ContestAccessService contestAccess;
    private final ContestProblemSnapshotService snapshotService;

    public ContestService(MongoTemplate mongoTemplate, ContestProperties contestProperties, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.contestProperties = contestProperties;
        this.clock = clock;
        this.contestAccess = new ContestAccessService(mongoTemplate, contestProperties, clock);
        this.snapshotService = new ContestProblemSnapshotService(mongoTemplate, clock);
    }

    public ContestStatus status(Contest contest) {
        return contestAccess.status(contest);
    }

    public ContestAdminResponse create(ContestWriteRequest body, String createdBy) {
        if (body == null) {
            throw new IllegalArgumentException("Contest body is required");
        }
        Contest contest = new Contest();
        contest.setPublished(false);
        contest.setCreatedBy(createdBy);
        Instant now = clock.instant();
        contest.setCreatedAt(now);
        contest.setUpdatedAt(now);
        applyWrite(contest, body, true);
        validate(contest, false);
        return toAdminResponse(mongoTemplate.save(contest), true);
    }

    public ContestAdminResponse update(String id, ContestWriteRequest body) {
        Contest contest = requireAdminContest(id);
        ContestStatus current = status(contest);
        if (current == ContestStatus.ENDED) {
            throw new IllegalArgumentException("Ended contests cannot be modified");
        }
        if (body == null) {
            throw new IllegalArgumentException("Contest body is required");
        }
        Contest snapshot = copyOf(contest);
        applyWrite(contest, body, false);
        if (current == ContestStatus.LIVE) {
            rejectLiveImmutableChanges(snapshot, contest);
            rejectLiveEndInThePast(contest);
        }
        validate(contest, contest.isPublished());
        contest.setUpdatedAt(clock.instant());
        return toAdminResponse(mongoTemplate.save(contest), true);
    }

    public ContestAdminResponse publish(String id) {
        Contest contest = requireAdminContest(id);
        ContestStatus current = status(contest);
        if (current == ContestStatus.ENDED) {
            throw new IllegalArgumentException("Ended contests cannot be published");
        }
        if (current == ContestStatus.LIVE && contest.isPublished()) {
            return toAdminResponse(contest, true);
        }
        validate(contest, true);
        snapshotService.ensureSnapshots(contest);
        contest.setPublished(true);
        contest.setUpdatedAt(clock.instant());
        return toAdminResponse(mongoTemplate.save(contest), true);
    }

    public ContestAdminResponse unpublish(String id) {
        Contest contest = requireAdminContest(id);
        ContestStatus current = status(contest);
        if (current != ContestStatus.UPCOMING) {
            throw new IllegalArgumentException("Only upcoming contests can be unpublished");
        }
        snapshotService.deleteByContestId(contest.getId());
        contest.setPublished(false);
        contest.setUpdatedAt(clock.instant());
        return toAdminResponse(mongoTemplate.save(contest), true);
    }

    public void delete(String id) {
        Contest contest = requireAdminContest(id);
        ContestStatus current = status(contest);
        if (current == ContestStatus.LIVE) {
            throw new IllegalArgumentException("Live contests cannot be deleted");
        }
        if (current == ContestStatus.ENDED) {
            throw new IllegalArgumentException("Ended contests cannot be deleted");
        }
        snapshotService.deleteByContestId(contest.getId());
        mongoTemplate.remove(contest);
    }

    public ContestAdminResponse adminGet(String id) {
        return toAdminResponse(requireAdminContest(id), true);
    }

    public Map<String, Object> adminList(int page, int size) {
        int safeSize = boundSize(size);
        int safePage = Math.max(0, page);
        Query query = new Query();
        long total = mongoTemplate.count(query, Contest.class);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"))
            .skip((long) safePage * safeSize)
            .limit(safeSize);
        List<ContestAdminResponse> items = new ArrayList<>();
        for (Contest contest : mongoTemplate.find(query, Contest.class)) {
            items.add(toAdminResponse(contest, false));
        }
        return pageResponse("contests", items, safePage, safeSize, total);
    }

    public Map<String, Object> listVisible(User user, int page, int size) {
        requireAuthenticated(user);
        int safeSize = boundSize(size);
        int safePage = Math.max(0, page);
        ContestAccessService.UserBatchScope scope = contestAccess.scopeForUser(user.getId());
        Criteria eligibility = contestAccess.publishedEligibilityCriteria(scope, clock.instant());
        if (eligibility == null) {
            return pageResponse("contests", List.of(), safePage, safeSize, 0);
        }
        Query query = new Query(eligibility);
        long total = mongoTemplate.count(query, Contest.class);
        query.with(Sort.by(Sort.Direction.ASC, "startAt"))
            .skip((long) safePage * safeSize)
            .limit(safeSize);
        List<ContestUserResponse> items = new ArrayList<>();
        for (Contest contest : mongoTemplate.find(query, Contest.class)) {
            items.add(toUserResponse(contest, false));
        }
        return pageResponse("contests", items, safePage, safeSize, total);
    }

    public ContestUserResponse getVisible(User user, String id) {
        requireAuthenticated(user);
        Contest contest = mongoTemplate.findById(id, Contest.class);
        if (contest == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found");
        }
        if (!contestAccess.isEligible(user, contest, contestAccess.scopeForUser(user.getId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found");
        }
        return toUserResponse(contest, true);
    }

    public ContestProblemDetailResponse getVisibleProblem(User user, String contestId, String problemId) {
        requireAuthenticated(user);
        Instant now = clock.instant();
        Contest contest = mongoTemplate.findById(contestId, Contest.class);
        if (contest == null || !contest.isPublished()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found");
        }
        ContestAccessService.UserBatchScope scope = contestAccess.scopeForUser(user.getId());
        if (!contestAccess.isEligible(user, contest, scope, now)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found");
        }
        ContestStatus current = contestAccess.status(contest, now);
        if (current != ContestStatus.LIVE && current != ContestStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found");
        }
        String canonicalProblemId = canonicalContestProblemId(contest, problemId);
        if (canonicalProblemId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found");
        }
        ContestProblemSnapshot snapshot = snapshotService.find(contest.getId(), canonicalProblemId);
        if (snapshot == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found");
        }
        return ContestProblemDetailResponse.from(snapshot);
    }

    /**
     * Same visibility as contest problem detail: published, eligible, and LIVE or ENDED.
     * A draft save is not a submission and has no scoring effect.
     */
    public ContestDraftTarget requireContestDraftTarget(User user, String contestId, String problemId) {
        ContestProblemDetailResponse detail = getVisibleProblem(user, contestId, problemId);
        Contest contest = mongoTemplate.findById(contestId, Contest.class);
        if (contest == null || contest.getId() == null || detail.getId() == null || detail.getId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found");
        }
        return new ContestDraftTarget(contest.getId(), detail.getId());
    }

    public record ContestDraftTarget(String contestId, String problemId) {}

    private void applyWrite(Contest contest, ContestWriteRequest body, boolean creating) {
        if (creating || body.getTitle() != null) {
            contest.setTitle(body.getTitle());
        }
        if (creating || body.getDescription() != null) {
            contest.setDescription(body.getDescription());
        }
        if (creating || body.getStartAt() != null) {
            contest.setStartAt(body.getStartAt());
        }
        if (creating || body.getEndAt() != null) {
            contest.setEndAt(body.getEndAt());
        }
        if (creating || body.getAudienceType() != null) {
            contest.setAudienceType(body.getAudienceType());
        }
        boolean batchesSupplied = creating || body.getBatchIds() != null || body.getAudienceType() != null;
        boolean problemsSupplied = creating || body.getProblemIds() != null;
        if (creating || body.getBatchIds() != null) {
            contest.setBatchIds(body.getBatchIds() == null ? new ArrayList<>() : new ArrayList<>(body.getBatchIds()));
        }
        if (creating || body.getProblemIds() != null) {
            contest.setProblemIds(body.getProblemIds() == null ? new ArrayList<>() : new ArrayList<>(body.getProblemIds()));
        }
        contest.setTitle(requireTitle(contest.getTitle()));
        if (creating || body.getDescription() != null) {
            contest.setDescription(normalizeDescription(contest.getDescription()));
        }
        if (batchesSupplied) {
            contest.setBatchIds(canonicalizeBatchIds(contest.getAudienceType(), contest.getBatchIds()));
        }
        if (problemsSupplied) {
            contest.setProblemIds(canonicalizeProblemIds(contest.getProblemIds()));
        }
    }

    private void validate(Contest contest, boolean publishing) {
        requireTitle(contest.getTitle());
        if (contest.getStartAt() == null) {
            throw new IllegalArgumentException("startAt is required");
        }
        if (contest.getEndAt() == null) {
            throw new IllegalArgumentException("endAt is required");
        }
        if (!contest.getEndAt().isAfter(contest.getStartAt())) {
            throw new IllegalArgumentException("endAt must be after startAt");
        }
        if (contest.getAudienceType() == null) {
            throw new IllegalArgumentException("audienceType is required");
        }
        contest.setBatchIds(canonicalizeBatchIds(contest.getAudienceType(), contest.getBatchIds()));
        if (contest.getAudienceType() == ContestAudienceType.BATCHES) {
            if (contest.getBatchIds() == null || contest.getBatchIds().isEmpty()) {
                throw new IllegalArgumentException("BATCHES contests require at least one batch ID");
            }
            Query batchQuery = Query.query(Criteria.where("_id").in(contest.getBatchIds()));
            List<Batch> batches = mongoTemplate.find(batchQuery, Batch.class);
            Map<String, Batch> byId = new HashMap<>();
            for (Batch batch : batches) {
                if (batch.getId() != null) {
                    byId.put(batch.getId(), batch);
                }
            }
            for (String batchId : contest.getBatchIds()) {
                Batch batch = byId.get(batchId);
                if (batch == null) {
                    throw new IllegalArgumentException("Batch not found: " + batchId);
                }
                if (publishing && !batch.isActive()) {
                    throw new IllegalArgumentException("Batch is not active: " + batchId);
                }
            }
        }
        if (contest.getAudienceType() == ContestAudienceType.ALL_USERS
            && contest.getBatchIds() != null && !contest.getBatchIds().isEmpty()) {
            throw new IllegalArgumentException("ALL_USERS contests cannot include batch IDs");
        }
        if (publishing) {
            List<String> problemIds = contest.getProblemIds() == null ? List.of() : contest.getProblemIds();
            if (problemIds.isEmpty()) {
                throw new IllegalArgumentException("at least one problem is required to publish");
            }
            Query problemQuery = Query.query(Criteria.where("_id").in(problemIds));
            List<Problem> problems = mongoTemplate.find(problemQuery, Problem.class);
            if (problems.size() != problemIds.size()) {
                throw new IllegalArgumentException("Problem not found");
            }
            if (contest.getAudienceType() == ContestAudienceType.ALL_USERS && !contestProperties.isGlobalEnabled()) {
                throw new IllegalArgumentException("Global contests are disabled");
            }
        }
    }

    private List<String> canonicalizeBatchIds(ContestAudienceType audienceType, List<String> raw) {
        if (audienceType == ContestAudienceType.ALL_USERS) {
            if (raw == null || raw.isEmpty()) {
                return new ArrayList<>();
            }
            List<String> leftover = new ArrayList<>();
            for (String id : raw) {
                if (id != null && !id.isBlank()) {
                    leftover.add(id.trim());
                }
            }
            if (!leftover.isEmpty()) {
                throw new IllegalArgumentException("ALL_USERS contests cannot include batch IDs");
            }
            return new ArrayList<>();
        }
        if (raw == null) {
            return new ArrayList<>();
        }
        List<String> ids = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String rawId : raw) {
            if (rawId == null || rawId.isBlank()) {
                throw new IllegalArgumentException("batch ID is required");
            }
            String id = rawId.trim();
            if (!seen.add(id)) {
                throw new IllegalArgumentException("duplicate batch IDs");
            }
            ids.add(id);
        }
        return ids;
    }

    private List<String> canonicalizeProblemIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        if (raw.size() > MAX_PROBLEMS) {
            throw new IllegalArgumentException("A contest can have at most " + MAX_PROBLEMS + " problems");
        }
        List<String> ids = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String rawId : raw) {
            if (rawId == null || rawId.isBlank()) {
                throw new IllegalArgumentException("problem ID is required");
            }
            Problem problem = resolveProblem(rawId.trim());
            if (problem == null) {
                throw new IllegalArgumentException("Problem not found: " + rawId);
            }
            String canonical = problem.getId();
            if (!seen.add(canonical)) {
                throw new IllegalArgumentException("duplicate problem IDs");
            }
            ids.add(canonical);
        }
        return ids;
    }

    private String canonicalContestProblemId(Contest contest, String problemId) {
        List<String> problemIds = contest.getProblemIds() == null ? List.of() : contest.getProblemIds();
        if (problemId != null && problemIds.contains(problemId)) {
            return problemId;
        }
        Integer numericId;
        try {
            numericId = Integer.valueOf(problemId);
        } catch (NumberFormatException | NullPointerException ignored) {
            return null;
        }
        for (ContestProblemSnapshot snapshot : snapshotService.findByContestId(contest.getId())) {
            if (numericId.equals(snapshot.getNumericId()) && snapshot.getProblemId() != null
                && problemIds.contains(snapshot.getProblemId())) {
                return snapshot.getProblemId();
            }
        }
        return null;
    }

    private Problem resolveProblem(String id) {
        Problem problem = mongoTemplate.findById(id, Problem.class);
        if (problem != null) {
            return problem;
        }
        try {
            return mongoTemplate.findOne(Query.query(Criteria.where("numericId").is(Integer.valueOf(id))), Problem.class);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void rejectLiveImmutableChanges(Contest before, Contest after) {
        if (!Objects.equals(before.getAudienceType(), after.getAudienceType())
            || !Objects.equals(before.getBatchIds(), after.getBatchIds())) {
            throw new IllegalArgumentException("Live contests cannot change audience");
        }
        if (!Objects.equals(before.getProblemIds(), after.getProblemIds())) {
            throw new IllegalArgumentException("Live contests cannot change problems");
        }
        if (!Objects.equals(before.getStartAt(), after.getStartAt())) {
            throw new IllegalArgumentException("Live contests cannot change startAt");
        }
    }

    private void rejectLiveEndInThePast(Contest contest) {
        Instant now = clock.instant();
        if (contest.getEndAt() == null || !contest.getEndAt().isAfter(now)) {
            throw new IllegalArgumentException("Live contest endAt must be in the future");
        }
    }

    private Contest requireAdminContest(String id) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found");
        }
        Contest contest = mongoTemplate.findById(id, Contest.class);
        if (contest == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found");
        }
        return contest;
    }

    /**
     * Verified, active students in the contest's active batches.
     * Contest status itself still comes only from start and end timestamps.
     */
    private int countEligibleMembers(Contest contest) {
        if (contest == null || contest.getAudienceType() == ContestAudienceType.ALL_USERS) {
            return 0;
        }
        List<String> batchIds = contest.getBatchIds() == null ? List.of() : contest.getBatchIds();
        if (batchIds.isEmpty()) {
            return 0;
        }
        List<Batch> activeBatches = mongoTemplate.find(
            Query.query(Criteria.where("_id").in(batchIds).and("active").is(true)),
            Batch.class);
        if (activeBatches == null || activeBatches.isEmpty()) {
            return 0;
        }
        List<String> activeIds = activeBatches.stream().map(Batch::getId).filter(id -> id != null && !id.isBlank()).toList();
        List<BatchMember> memberships = mongoTemplate.find(
            Query.query(Criteria.where("batchId").in(activeIds)),
            BatchMember.class);
        if (memberships == null || memberships.isEmpty()) {
            return 0;
        }
        List<String> userIds = memberships.stream()
            .map(BatchMember::getUserId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();
        if (userIds.isEmpty()) {
            return 0;
        }
        Query users = Query.query(new Criteria().andOperator(
            Criteria.where("_id").in(userIds),
            Criteria.where("emailVerified").is(true),
            new Criteria().orOperator(
                Criteria.where("accountStatus").exists(false),
                Criteria.where("accountStatus").is("ACTIVE")
            )
        ));
        return (int) Math.min(Integer.MAX_VALUE, mongoTemplate.count(users, User.class));
    }

    private static void requireAuthenticated(User user) {
        if (user == null || !user.canAuthenticate() || user.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
    }

    private ContestAdminResponse toAdminResponse(Contest contest, boolean includeProblemSummaries) {
        ContestAdminResponse response = new ContestAdminResponse();
        fillCommon(contest, response);
        response.setPublished(contest.isPublished());
        response.setBatchIds(contest.getBatchIds() == null ? List.of() : List.copyOf(contest.getBatchIds()));
        response.setProblemIds(contest.getProblemIds() == null ? List.of() : List.copyOf(contest.getProblemIds()));
        response.setCreatedBy(contest.getCreatedBy());
        response.setCreatedAt(contest.getCreatedAt());
        response.setUpdatedAt(contest.getUpdatedAt());
        if (includeProblemSummaries) {
            response.setProblems(contest.isPublished()
                ? snapshotSummaries(contest)
                : safeProblemSummaries(contest.getProblemIds()));
            response.setEligibleMemberCount(countEligibleMembers(contest));
        }
        return response;
    }

    private ContestUserResponse toUserResponse(Contest contest, boolean includeProblemsWhenStarted) {
        ContestUserResponse response = new ContestUserResponse();
        response.setId(contest.getId());
        response.setTitle(contest.getTitle());
        response.setDescription(contest.getDescription());
        response.setStartAt(contest.getStartAt());
        response.setEndAt(contest.getEndAt());
        response.setStatus(status(contest));
        response.setAudienceType(contest.getAudienceType());
        response.setScoringMode(ContestScoringMode.POINTS_FIRST_ACCEPTED);
        int count = contest.getProblemIds() == null ? 0 : contest.getProblemIds().size();
        response.setProblemCount(count);
        ContestStatus current = response.getStatus();
        if (includeProblemsWhenStarted && (current == ContestStatus.LIVE || current == ContestStatus.ENDED)) {
            response.setProblems(snapshotSummaries(contest));
        } else {
            response.setProblems(List.of());
        }
        return response;
    }

    private void fillCommon(Contest contest, ContestAdminResponse response) {
        response.setId(contest.getId());
        response.setTitle(contest.getTitle());
        response.setDescription(contest.getDescription());
        response.setStartAt(contest.getStartAt());
        response.setEndAt(contest.getEndAt());
        response.setStatus(status(contest));
        response.setAudienceType(contest.getAudienceType());
        response.setScoringMode(ContestScoringMode.POINTS_FIRST_ACCEPTED);
        response.setProblemCount(contest.getProblemIds() == null ? 0 : contest.getProblemIds().size());
    }

    private List<ContestProblemSummary> snapshotSummaries(Contest contest) {
        List<String> problemIds = contest.getProblemIds() == null ? List.of() : contest.getProblemIds();
        if (problemIds.isEmpty()) {
            return List.of();
        }
        Map<String, ContestProblemSnapshot> byId = new HashMap<>();
        for (ContestProblemSnapshot snapshot : snapshotService.findByContestId(contest.getId())) {
            if (snapshot.getProblemId() != null) {
                byId.put(snapshot.getProblemId(), snapshot);
            }
        }
        List<ContestProblemSummary> summaries = new ArrayList<>();
        for (String problemId : problemIds) {
            ContestProblemSnapshot snapshot = byId.get(problemId);
            ContestProblemSummary summary = new ContestProblemSummary();
            summary.setId(problemId);
            if (snapshot != null) {
                summary.setNumericId(snapshot.getNumericId());
                summary.setTitle(snapshot.getTitle());
                summary.setDifficulty(snapshot.getDifficulty());
                summary.setCategory(snapshot.getCategory());
                summary.setPoints(snapshot.getPoints());
                summary.setTags(snapshot.getTags());
            }
            summaries.add(summary);
        }
        return summaries;
    }

    private List<ContestProblemSummary> safeProblemSummaries(List<String> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return List.of();
        }
        Query query = Query.query(Criteria.where("_id").in(problemIds));
        query.fields()
            .include("title")
            .include("numericId")
            .include("difficulty")
            .include("category")
            .include("points")
            .include("tags");
        Map<String, Problem> byId = new HashMap<>();
        for (Problem problem : mongoTemplate.find(query, Problem.class)) {
            byId.put(problem.getId(), problem);
        }
        List<ContestProblemSummary> summaries = new ArrayList<>();
        for (String problemId : problemIds) {
            Problem problem = byId.get(problemId);
            if (problem == null) {
                ContestProblemSummary missing = new ContestProblemSummary();
                missing.setId(problemId);
                summaries.add(missing);
            } else {
                summaries.add(ContestProblemSummary.from(problem));
            }
        }
        return summaries;
    }

    private static Contest copyOf(Contest source) {
        Contest copy = new Contest();
        copy.setId(source.getId());
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setStartAt(source.getStartAt());
        copy.setEndAt(source.getEndAt());
        copy.setPublished(source.isPublished());
        copy.setAudienceType(source.getAudienceType());
        copy.setBatchIds(source.getBatchIds() == null ? new ArrayList<>() : new ArrayList<>(source.getBatchIds()));
        copy.setProblemIds(source.getProblemIds() == null ? new ArrayList<>() : new ArrayList<>(source.getProblemIds()));
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title must be " + MAX_TITLE_LENGTH + " characters or fewer");
        }
        return trimmed;
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("description must be " + MAX_DESCRIPTION_LENGTH + " characters or fewer");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int boundSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static Map<String, Object> pageResponse(String key, List<?> items, int page, int size, long total) {
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(key, items);
        response.put("currentPage", page);
        response.put("totalPages", totalPages);
        response.put("totalItems", total);
        return response;
    }
}
