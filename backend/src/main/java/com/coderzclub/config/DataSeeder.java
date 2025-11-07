package com.coderzclub.config;

import com.coderzclub.model.ProblemBundle;
import com.coderzclub.repository.ProblemBundleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private ProblemBundleRepository problemBundleRepository;

    @Override
    public void run(String... args) throws Exception {
        // Only seed if no bundles exist
        if (problemBundleRepository.count() == 0) {
            seedProblemBundles();
        }
    }

    private void seedProblemBundles() {
        // Basic Level Bundle
        ProblemBundle basicBundle = new ProblemBundle();
        basicBundle.setName("Fundamentals of Programming");
        basicBundle.setDescription("Master the basics of programming with simple algorithms and data structures");
        basicBundle.setDifficulty("BASIC");
        basicBundle.setCategory("ALGORITHMS");
        basicBundle.setProblemIds(Arrays.asList("1", "2", "3", "4", "5"));
        basicBundle.setTotalProblems(5);
        basicBundle.setTotalPoints(50);
        basicBundle.setEstimatedTotalTime(120);
        basicBundle.setPremium(false);
        basicBundle.setPrice(0.0);
        basicBundle.setCurrency("USD");
        basicBundle.setTags(Arrays.asList("beginner", "fundamentals", "algorithms"));
        basicBundle.setCreatedBy("system");
        basicBundle.setActive(true);
        basicBundle.setCreatedAt(new Date());
        basicBundle.setUpdatedAt(new Date());
        basicBundle.setSharedTemplate("public class Solution {\n    public int solve(int[] nums) {\n        // Your code here\n        return 0;\n    }\n}");
        
        problemBundleRepository.save(basicBundle);

        // Intermediate Level Bundle
        ProblemBundle intermediateBundle = new ProblemBundle();
        intermediateBundle.setName("Data Structures Mastery");
        intermediateBundle.setDescription("Deep dive into arrays, linked lists, trees, and graphs");
        intermediateBundle.setDifficulty("INTERMEDIATE");
        intermediateBundle.setCategory("DATA_STRUCTURES");
        intermediateBundle.setProblemIds(Arrays.asList("6", "7", "8", "9", "10", "11", "12"));
        intermediateBundle.setTotalProblems(7);
        intermediateBundle.setTotalPoints(100);
        intermediateBundle.setEstimatedTotalTime(240);
        intermediateBundle.setPremium(false);
        intermediateBundle.setPrice(0.0);
        intermediateBundle.setCurrency("USD");
        intermediateBundle.setTags(Arrays.asList("intermediate", "data-structures", "trees", "graphs"));
        intermediateBundle.setCreatedBy("system");
        intermediateBundle.setActive(true);
        intermediateBundle.setCreatedAt(new Date());
        intermediateBundle.setUpdatedAt(new Date());
        intermediateBundle.setSharedTemplate("public class Solution {\n    public int solve(TreeNode root) {\n        // Your code here\n        return 0;\n    }\n}");
        
        problemBundleRepository.save(intermediateBundle);

        // Advanced Level Bundle
        ProblemBundle advancedBundle = new ProblemBundle();
        advancedBundle.setName("Advanced Algorithms");
        advancedBundle.setDescription("Complex algorithms including dynamic programming and advanced graph algorithms");
        advancedBundle.setDifficulty("ADVANCED");
        advancedBundle.setCategory("ALGORITHMS");
        advancedBundle.setProblemIds(Arrays.asList("13", "14", "15", "16", "17", "18"));
        advancedBundle.setTotalProblems(6);
        advancedBundle.setTotalPoints(150);
        advancedBundle.setEstimatedTotalTime(300);
        advancedBundle.setPremium(true);
        advancedBundle.setPrice(9.99);
        advancedBundle.setCurrency("USD");
        advancedBundle.setTags(Arrays.asList("advanced", "dynamic-programming", "graph-algorithms"));
        advancedBundle.setCreatedBy("system");
        advancedBundle.setActive(true);
        advancedBundle.setCreatedAt(new Date());
        advancedBundle.setUpdatedAt(new Date());
        advancedBundle.setSharedTemplate("public class Solution {\n    public int solve(int[][] grid) {\n        // Your code here\n        return 0;\n    }\n}");
        
        problemBundleRepository.save(advancedBundle);

        // SDE Level Bundle
        ProblemBundle sdeBundle = new ProblemBundle();
        sdeBundle.setName("System Design & Architecture");
        sdeBundle.setDescription("Design scalable systems, microservices, and distributed systems");
        sdeBundle.setDifficulty("SDE");
        sdeBundle.setCategory("SYSTEM_DESIGN");
        sdeBundle.setProblemIds(Arrays.asList("19", "20", "21", "22", "23"));
        sdeBundle.setTotalProblems(5);
        sdeBundle.setTotalPoints(200);
        sdeBundle.setEstimatedTotalTime(400);
        sdeBundle.setPremium(true);
        sdeBundle.setPrice(19.99);
        sdeBundle.setCurrency("USD");
        sdeBundle.setTags(Arrays.asList("sde", "system-design", "microservices", "scalability"));
        sdeBundle.setCreatedBy("system");
        sdeBundle.setActive(true);
        sdeBundle.setCreatedAt(new Date());
        sdeBundle.setUpdatedAt(new Date());
        sdeBundle.setSharedTemplate("public class Solution {\n    public String solve(String input) {\n        // Your code here\n        return \"\";\n    }\n}");
        
        problemBundleRepository.save(sdeBundle);

        // Expert Level Bundle
        ProblemBundle expertBundle = new ProblemBundle();
        expertBundle.setName("Competitive Programming");
        expertBundle.setDescription("Ultra-hard problems for competitive programming and interviews");
        expertBundle.setDifficulty("EXPERT");
        expertBundle.setCategory("ALGORITHMS");
        expertBundle.setProblemIds(Arrays.asList("24", "25", "26", "27", "28"));
        expertBundle.setTotalProblems(5);
        expertBundle.setTotalPoints(250);
        expertBundle.setEstimatedTotalTime(500);
        expertBundle.setPremium(true);
        expertBundle.setPrice(29.99);
        expertBundle.setCurrency("USD");
        expertBundle.setTags(Arrays.asList("expert", "competitive-programming", "interview-prep"));
        expertBundle.setCreatedBy("system");
        expertBundle.setActive(true);
        expertBundle.setCreatedAt(new Date());
        expertBundle.setUpdatedAt(new Date());
        expertBundle.setSharedTemplate("public class Solution {\n    public int solve(int[] nums, int target) {\n        // Your code here\n        return 0;\n    }\n}");
        
        problemBundleRepository.save(expertBundle);

        System.out.println("✅ Problem bundles seeded successfully!");
    }
}

