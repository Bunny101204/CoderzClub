package com.coderzclub.controller;

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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

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
            List<Problem> matchedProblems = mongoTemplate.find(query.with(Sort.by(Sort.Order.asc("id"))), Problem.class);
            matchedProblems.sort(Comparator.comparingInt(p -> parseNumericId(p.getId())));

            int totalItems = matchedProblems.size();
            int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
            int startIndex = Math.min(page * size, totalItems);
            int endIndex = Math.min(startIndex + size, totalItems);
            List<Problem> pageProblems = startIndex < endIndex ? matchedProblems.subList(startIndex, endIndex) : new ArrayList<>();

            // Ensure hidden testcases are not returned to clients
            for (Problem p : pageProblems) {
                p.setHiddenTestCases(null);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("problems", pageProblems);
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
            response.put("problems", new ArrayList<Problem>());
            response.put("currentPage", 0);
            response.put("totalPages", 0);
            response.put("totalItems", 0);
            response.put("hasNext", false);
            response.put("hasPrevious", false);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private int parseNumericId(String id) {
        if (id == null) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Problem> getProblemById(@PathVariable String id) {
        Optional<Problem> problem = problemRepository.findById(id);
        if (problem.isEmpty()) return ResponseEntity.notFound().build();
        Problem p = problem.get();
        // Remove hidden testcases from API response
        p.setHiddenTestCases(null);
        return ResponseEntity.ok(p);
    }

    @GetMapping("/test")
    public ResponseEntity<String> testProblems() {
        return ResponseEntity.ok("Problems endpoint is working!");
    }

    @PostMapping
    public ResponseEntity<?> addProblem(@RequestBody Problem problem) {
        try {
            submissionValidator.validateProblemTestCases(problem);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        Problem saved = problemRepository.save(problem);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProblem(@PathVariable String id) {
        problemRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
} 