package com.coderzclub.controller;

import com.coderzclub.dto.ProblemListResponse;
import com.coderzclub.model.Problem;
import com.coderzclub.repository.ProblemRepository;

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
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {
    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private SubmissionValidator submissionValidator;

    @Autowired
    private MongoTemplate mongoTemplate;

    @GetMapping
    public ResponseEntity<?> getAllProblems(
        @RequestParam(required = false) String difficulty,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) List<String> tags,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        try {
            if (page < 0 || size <= 0) {
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
            long totalItems = mongoTemplate.count(query, Problem.class);
            int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
            query.with(Sort.by(Sort.Order.asc("numericId"), Sort.Order.asc("_id")))
                .skip((long) page * size)
                .limit(size);
            List<Problem> pageProblems = mongoTemplate.find(query, Problem.class);


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
            response.put("hasNext", page < totalPages - 1);
            response.put("hasPrevious", page > 0);

            return ResponseEntity.ok(response);
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
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProblemById(@PathVariable String id) {
        Optional<Problem> problem = problemRepository.findById(id);

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
                problem.setNumericId(parseNumericIdForCreation(problem.getId()));
            }
            if (problem.getNumericId() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "numericId must be positive"));
            }

            submissionValidator.validateProblemTestCases(problem);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        Problem saved = problemRepository.save(problem);
        return ResponseEntity.ok(saved);

            /*validationService.validateProblemTestcases(problem);
            Problem saved = problemRepository.save(problem);
            return ResponseEntity.ok(new AdminProblemResponse(saved));
        //} catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
        */

    }

    private int parseNumericIdForCreation(String id) {
        if (id != null) {
            try {
                int parsed = Integer.parseInt(id);
                if (parsed > 0) return parsed;
            } catch (NumberFormatException ignored) {
                // Allocate after the current highest persisted ordering value.
            }
        }
        return problemRepository.findTopByOrderByNumericIdDesc()
            .map(problem -> problem.getNumericId() + 1)
            .orElse(1);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProblem(@PathVariable String id) {
        problemRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
} 