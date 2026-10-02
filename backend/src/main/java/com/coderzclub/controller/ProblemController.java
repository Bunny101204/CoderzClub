package com.coderzclub.controller;

import com.coderzclub.dto.ProblemListResponse;
import com.coderzclub.dto.ProblemDetailResponse;
import com.coderzclub.dto.ProblemExecutionConfigUpdate;
import com.coderzclub.model.Problem;
import com.coderzclub.model.User;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ProblemNumericIdAllocator;

import com.coderzclub.service.SubmissionValidator;
import com.coderzclub.service.ExecutionOutcome;
import com.coderzclub.service.IncompatibleExecutionException;
import com.coderzclub.service.ProblemExecutionConfigService;
import com.coderzclub.service.ProblemRunService;
import com.coderzclub.service.RunLimitExceededException;
import com.coderzclub.model.ExecutionMode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {
    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private SubmissionValidator submissionValidator;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ProblemNumericIdAllocator numericIdAllocator;

    @Autowired
    private ProblemRunService problemRunService;

    @Autowired
    private ProblemExecutionConfigService executionConfigService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<?> getAllProblems(
        @RequestParam(required = false) String difficulty,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) List<String> tags,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        try {
            int requestedLimit = limit == null ? size : limit;
            if (page < 0 || size <= 0 || requestedLimit <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "page must be non-negative and size must be positive"));
            }
            difficulty = (difficulty == null || difficulty.trim().isEmpty()) ? null : difficulty.trim();
            category = (category == null || category.trim().isEmpty()) ? null : category.trim();
            search = (search == null || search.trim().isEmpty()) ? null : search.trim();
            if (tags != null && tags.isEmpty()) {
                tags = null;
            }

            Query query = new Query();
            if (difficulty != null) {
                query.addCriteria(Criteria.where("difficulty").is(difficulty));
            }
            if (category != null) {
                query.addCriteria(Criteria.where("category").is(category));
            }
            if (tags != null && !tags.isEmpty()) {
                query.addCriteria(Criteria.where("tags").in(tags));
            }
            if (search != null) {
                String escapedSearch = Pattern.quote(search);
                query.addCriteria(new Criteria().orOperator(
                    Criteria.where("title").regex("^" + escapedSearch, "i"),
                    Criteria.where("_id").regex("^" + escapedSearch, "i")
                ));
            }
            boolean cursorMode = cursor != null && !cursor.trim().isEmpty();
            long totalItems = 0;
            int totalPages = 0;
            if (cursorMode) {
                CursorPosition position = decodeCursor(cursor);
                query.addCriteria(new Criteria().orOperator(
                    Criteria.where("numericId").gt(position.numericId()),
                    new Criteria().andOperator(
                        Criteria.where("numericId").is(position.numericId()),
                        Criteria.where("_id").gt(position.id())
                    )
                ));
            } else {
                totalItems = mongoTemplate.count(query, Problem.class);
                totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
            }
            query.with(Sort.by(Sort.Order.asc("numericId"), Sort.Order.asc("_id")))
                .limit(cursorMode ? requestedLimit + 1 : size);
            if (!cursorMode) {
                query.skip((long) page * size);
            }
            List<Problem> pageProblems = mongoTemplate.find(query, Problem.class);
            String nextCursor = null;
            if (cursorMode && pageProblems.size() > requestedLimit) {
                pageProblems.remove(requestedLimit);
                Problem last = pageProblems.get(pageProblems.size() - 1);
                nextCursor = encodeCursor(last);
            }


            // Ensure hidden testcases are not returned to clients
            for (Problem p : pageProblems) {
                p.setHiddenTestCases(null);
            }

            List<ProblemListResponse> sanitizedProblems = pageProblems.stream()
                    .map(ProblemListResponse::new)
                    .collect(Collectors.toList());


            Map<String, Object> response = new HashMap<>();
            response.put("problems", sanitizedProblems);
            response.put("currentPage", page);
            response.put("totalPages", totalPages);
            response.put("totalItems", totalItems);
            response.put("hasNext", cursorMode ? nextCursor != null : page < totalPages - 1);
            response.put("hasPrevious", cursorMode ? cursorMode : page > 0);
            response.put("nextCursor", nextCursor);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.out.println("Error fetching problems: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> response = new HashMap<>();
            response.put("problems", new ArrayList<ProblemListResponse>());
            response.put("currentPage", 0);
            response.put("totalPages", 0);
            response.put("totalItems", 0);
            response.put("hasNext", false);
            response.put("hasPrevious", false);
            response.put("nextCursor", null);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/by-ids")
    public ResponseEntity<List<ProblemListResponse>> getProblemsByIds(
            @RequestParam List<String> ids) {
        try {
            List<Problem> problems = new ArrayList<>();
            for (String rawId : ids) {
                if (rawId == null || rawId.isBlank()) continue;
                Problem problem = problemRepository.findById(rawId).orElse(null);
                if (problem == null) {
                    try {
                        problem = problemRepository.findByNumericId(Integer.valueOf(rawId)).orElse(null);
                    } catch (NumberFormatException ignored) { }
                }
                if (problem != null) {
                    problem.setHiddenTestCases(null);
                    problems.add(problem);
                }
            }
            return ResponseEntity.ok(problems.stream().map(ProblemListResponse::new).toList());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProblemById(@PathVariable String id) {
        Optional<Problem> problem = problemRepository.findById(id);
        if (problem.isEmpty()) {
            try {
                problem = problemRepository.findByNumericId(Integer.valueOf(id));
            } catch (NumberFormatException ignored) {
                // The path was not a numeric problem ID.
            }
        }

        if (problem.isEmpty()) return ResponseEntity.notFound().build();
        Problem p = problem.get();
        // Remove hidden testcases from API response
        p.setHiddenTestCases(null);
        return ResponseEntity.ok(p);

        /*if (problem.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new ProblemDetailResponse(problem.get()));
        */      
    }

    @GetMapping("/test")
    public ResponseEntity<String> testProblems() {
        return ResponseEntity.ok("Problems endpoint is working!");
    }

    @PostMapping
    public ResponseEntity<?> addProblem(@Valid @RequestBody Problem problem) {
        try {

            if (problem.getNumericId() == null) {
                problem.setNumericId(numericIdAllocator.allocateNext());
            }
            if (problem.getNumericId() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "numericId must be positive"));
            }

            submissionValidator.validateProblemTestCases(problem);
            validateExecutionConfig(problem);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        try {
            Problem saved = problemRepository.save(problem);
            return ResponseEntity.ok(saved);
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "A problem with this numericId already exists"));
        }
    }

    @PatchMapping("/{id}/execution")
    public ResponseEntity<?> updateExecutionConfig(@PathVariable String id,
                                                   @RequestBody ProblemExecutionConfigUpdate update) {
        Optional<Problem> existingOpt = problemRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            Problem existing = existingOpt.get();
            ExecutionMode requested = update == null || update.getExecutionMode() == null
                ? existing.getExecutionMode()
                : ExecutionMode.fromValue(update.getExecutionMode());
            String version = update == null ? null : update.getTestcaseVersion();
            executionConfigService.apply(existing, requested, version);
            return ResponseEntity.ok(problemRepository.save(existing));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/run-public")
    public ResponseEntity<?> runPublicTests(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Optional<User> userOpt = currentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        Optional<Problem> problemOpt = problemRepository.findById(id);
        if (problemOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Problem not found"));
        }
        Problem problem = problemOpt.get();
        String code = body.get("source_code") == null ? (String) body.get("code") : String.valueOf(body.get("source_code"));
        Object languageRaw = body.get("language_id") != null ? body.get("language_id") : body.get("languageId");
        Integer languageId = languageRaw == null ? null : Integer.parseInt(languageRaw.toString());
        if (code == null || languageId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "source_code and language_id are required"));
        }
        try {
            ExecutionOutcome outcome = problemRunService.runPublicCases(userOpt.get().getId(), problem, code, languageId);
            Map<String, Object> response = new HashMap<>();
            response.put("results", outcome.getResults());
            response.put("configuredExecutionMode", outcome.getConfiguredMode() == null ? null : outcome.getConfiguredMode().name());
            response.put("executionModeUsed", outcome.getExecutionModeUsed() == null ? null : outcome.getExecutionModeUsed().name());
            response.put("fallbackReason", outcome.getFallbackReason());
            response.put("logicalTestcases", outcome.getLogicalTestcases());
            response.put("providerExecutions", outcome.getProviderExecutions());
            return ResponseEntity.ok(response);
        } catch (RunLimitExceededException limited) {
            return RateLimitResponses.from(limited);
        } catch (IncompatibleExecutionException incompatible) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", incompatible.getMessage(),
                "reason", incompatible.getReason(),
                "configuredExecutionMode", ExecutionMode.canonical(problem.getExecutionMode()).name()
            ));
        }
    }

    private Optional<User> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
            || "anonymousUser".equals(auth.getName())) {
            return Optional.empty();
        }
        return userRepository.findByUsername(auth.getName());
    }

    private void validateExecutionConfig(Problem problem) {
        if (problem.getExecutionMode() == null) {
            problem.setExecutionMode(ExecutionMode.STANDARD_PER_CASE);
        }
        executionConfigService.apply(problem, problem.getExecutionMode(), problem.getTestcaseVersion());
    }

    private String encodeCursor(Problem problem) {
        String value = problem.getNumericId() + "|" + problem.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private CursorPosition decodeCursor(String cursor) {
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = value.indexOf('|');
            if (separator <= 0 || separator == value.length() - 1) {
                throw new IllegalArgumentException("Invalid cursor");
            }
            return new CursorPosition(Integer.parseInt(value.substring(0, separator)), value.substring(separator + 1));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid cursor", e);
        }
    }

    private record CursorPosition(int numericId, String id) {
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProblem(@PathVariable String id) {
        problemRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
} 