package com.coderzclub.controller;

import com.coderzclub.model.Problem;
import com.coderzclub.repository.ProblemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {
    @Autowired
    private ProblemRepository problemRepository;

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
            // Create pageable with sorting
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Problem> problems;
            
            // Apply filters based on what's provided
            if (difficulty != null || category != null || search != null || (tags != null && !tags.isEmpty())) {
                // Use combined filter query
                problems = problemRepository.findWithFilters(
                    difficulty, 
                    category, 
                    search, 
                    tags, 
                    pageable
                );
            } else {
                // No filters, return all problems
                problems = problemRepository.findAll(pageable);
            }
            
            // Build response with pagination info
            Map<String, Object> response = new HashMap<>();
            response.put("problems", problems.getContent());
            response.put("currentPage", problems.getNumber());
            response.put("totalPages", problems.getTotalPages());
            response.put("totalItems", problems.getTotalElements());
            response.put("hasNext", problems.hasNext());
            response.put("hasPrevious", problems.hasPrevious());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.out.println("Error fetching problems: " + e.getMessage());
            e.printStackTrace();
            
            // Fallback: return sample problems (for backward compatibility)
            List<Problem> sampleProblems = new ArrayList<>();
            
            Problem p1 = new Problem();
            p1.setId("1");
            p1.setTitle("Two Sum");
            p1.setStatement("Given an array of integers and a target sum, return indices of the two numbers that add up to the target.");
            p1.setTemplate("public class TwoSum {\n    public int[] twoSum(int[] nums, int target) {\n        // Your code here\n    }\n}");
            p1.setLanguage("java");
            p1.setClassName("TwoSum");
            p1.setFunctionName("twoSum");
                
            Problem p2 = new Problem();
            p2.setId("2");
            p2.setTitle("Palindrome Check");
            p2.setStatement("Check if a given string is a palindrome.");
            p2.setTemplate("public class Palindrome {\n    public boolean isPalindrome(String str) {\n        // Your code here\n    }\n}");
            p2.setLanguage("java");
            p2.setClassName("Palindrome");
            p2.setFunctionName("isPalindrome");
                
            Problem p3 = new Problem();
            p3.setId("3");
            p3.setTitle("Fibonacci Series");
            p3.setStatement("Print the Fibonacci series up to n terms.");
            p3.setTemplate("public class Fibonacci {\n    public String fibonacci(int n) {\n        // Your code here\n    }\n}");
            p3.setLanguage("java");
            p3.setClassName("Fibonacci");
            p3.setFunctionName("fibonacci");
                
            sampleProblems.add(p1);
            sampleProblems.add(p2);
            sampleProblems.add(p3);
            
            // Return as list for backward compatibility
            Map<String, Object> response = new HashMap<>();
            response.put("problems", sampleProblems);
            response.put("currentPage", 0);
            response.put("totalPages", 1);
            response.put("totalItems", sampleProblems.size());
            response.put("hasNext", false);
            response.put("hasPrevious", false);
            
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Problem> getProblemById(@PathVariable String id) {
        Optional<Problem> problem = problemRepository.findById(id);
        return problem.map(ResponseEntity::ok)
                      .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/test")
    public ResponseEntity<String> testProblems() {
        return ResponseEntity.ok("Problems endpoint is working!");
    }

    @PostMapping
    public ResponseEntity<Problem> addProblem(@RequestBody Problem problem) {
        Problem saved = problemRepository.save(problem);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProblem(@PathVariable String id) {
        problemRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
} 