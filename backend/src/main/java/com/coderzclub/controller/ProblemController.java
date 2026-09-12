package com.coderzclub.controller;

import com.coderzclub.dto.ProblemListResponse;
import com.coderzclub.model.Problem;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.service.ProblemNumericIdAllocator;

import com.coderzclub.service.SubmissionValidator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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