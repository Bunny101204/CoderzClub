package com.coderzclub.config;

import com.coderzclub.model.Problem;
import com.coderzclub.model.TestCase;
import com.coderzclub.repository.ProblemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

// @Component  // Temporarily disabled to avoid MongoDB connection issues
public class ProblemDataInitializer implements CommandLineRunner {
    private final ProblemRepository problemRepository;

    public ProblemDataInitializer(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    @Override
    public void run(String... args) {
        // Temporarily disabled to avoid MongoDB connection issues
        /*
        if (problemRepository.count() == 0) {
            // Demo Problem 1
            Problem p1 = Problem.builder()
                .title("Two Sum")
                .statement("Given an array of integers and a target sum, return indices of the two numbers that add up to the target.")
                .template("public class TwoSum {\n    public int[] twoSum(int[] nums, int target) {\n        // Your code here\n    }\n}")
                .language("java")
                .className("TwoSum")
                .functionName("twoSum")
                .testCases(Arrays.asList(
                    TestCase.builder().input("nums = [2, 7, 11, 15], target = 9").output("[0, 1]").build()
                ))
                .hiddenTestCases(Arrays.asList(
                    TestCase.builder().input("nums = [3, 2, 4], target = 6").output("[1, 2]").build()
                ))
                .build();

            // Demo Problem 2
            Problem p2 = Problem.builder()
                .title("Palindrome Check")
                .statement("Check if a given string is a palindrome.")
                .template("public class Palindrome {\n    public boolean isPalindrome(String str) {\n        // Your code here\n    }\n}")
                .language("java")
                .className("Palindrome")
                .functionName("isPalindrome")
                .testCases(Arrays.asList(
                    TestCase.builder().input("\"racecar\"").output("true").build()
                ))
                .hiddenTestCases(Arrays.asList(
                    TestCase.builder().input("\"hello\"").output("false").build()
                ))
                .build();

            // Demo Problem 3
            Problem p3 = Problem.builder()
                .title("Fibonacci Series")
                .statement("Print the Fibonacci series up to n terms.")
                .template("public class Fibonacci {\n    public String fibonacci(int n) {\n        // Your code here\n    }\n}")
                .language("java")
                .className("Fibonacci")
                .functionName("fibonacci")
                .testCases(Arrays.asList(
                    TestCase.builder().input("5").output("0 1 1 2 3").build()
                ))
                .hiddenTestCases(Arrays.asList(
                    TestCase.builder().input("7").output("0 1 1 2 3 5 8").build()
                ))
                .build();

            problemRepository.saveAll(List.of(p1, p2, p3));
        }
        */
    }
} 