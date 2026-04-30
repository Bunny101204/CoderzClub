package com.coderzclub.config;

import com.coderzclub.model.Problem;
import com.coderzclub.model.TestCase;
import com.coderzclub.repository.ProblemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Component
public class ProblemDataInitializer implements CommandLineRunner {
    private final ProblemRepository problemRepository;

    public ProblemDataInitializer(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    @Override
    public void run(String... args) {
        long existingCount = problemRepository.count();
        boolean needsSeed = existingCount < 30;

        if (!needsSeed) {
            var maybeProblemOne = problemRepository.findById("1");
            if (maybeProblemOne.isPresent()) {
                String exampleInput = maybeProblemOne.get().getExampleInput();
                if (exampleInput != null && exampleInput.contains("\\n")) {
                    System.out.println("ProblemDataInitializer: detected bad newline formatting in seeded problems, reseeding default problems...");
                    needsSeed = true;
                }
            }
        }

        if (needsSeed) {
            System.out.println("ProblemDataInitializer: existing problem count = " + existingCount + ", seeding default problems...");
            seedProblems();
        } else {
            System.out.println("ProblemDataInitializer: database already has " + existingCount + " problems, skipping seeding.");
        }
    }

    private void seedProblems() {
        List<Problem> problems = new ArrayList<>();

        problems.add(createProblem(
            "1",
            "Reverse Array",
            "Given an integer n and a list of n integers, reverse the order of the array and print the result.",
            "ARRAYS",
            Arrays.asList("arrays", "basic", "reverse"),
            10,
            15,
            "EASY",
            false,
            "The first line contains n, the number of elements. The second line contains n integers separated by spaces.",
            "Print the reversed array elements separated by spaces.",
            "1 <= n <= 1000, -100000 <= arr[i] <= 100000",
            Arrays.asList("Read the array into a list first.", "Print the numbers in reverse order."),
            "5\n1 2 3 4 5",
            "5 4 3 2 1",
            "The input list reversed becomes 5 4 3 2 1.",
            Arrays.asList(
                new TestCase("5\n1 2 3 4 5", "5 4 3 2 1", "Reverse the array."),
                new TestCase("4\n10 0 -1 7", "7 -1 0 10", "Reverse the order of elements.")
            ),
            Arrays.asList(
                new TestCase("3\n5 5 5", "5 5 5"),
                new TestCase("1\n100", "100")
            )
        ));

        problems.add(createProblem(
            "2",
            "Sum of Array",
            "Given an array of integers, compute the sum of all elements.",
            "ARRAYS",
            Arrays.asList("arrays", "sum", "basic"),
            10,
            15,
            "EASY",
            false,
            "The first line contains n. The second line contains n integers separated by spaces.",
            "Print a single integer denoting the sum of the array.",
            "1 <= n <= 1000, -100000 <= arr[i] <= 100000",
            Arrays.asList("Use a running total.", "Handle negative values correctly."),
            "5\n1 2 3 4 5",
            "15",
            "The sum of 1+2+3+4+5 is 15.",
            Arrays.asList(
                new TestCase("4\n-1 2 -3 4", "2", "Calculate the sum including negatives."),
                new TestCase("3\n0 0 5", "5", "Sum zeros and a positive number.")
            ),
            Arrays.asList(
                new TestCase("1\n10", "10"),
                new TestCase("2\n100 200", "300")
            )
        ));

        problems.add(createProblem(
            "3",
            "Maximum Element",
            "Given an array of integers, find the largest element in the array.",
            "ARRAYS",
            Arrays.asList("arrays", "max", "basic"),
            10,
            15,
            "EASY",
            false,
            "The first line contains n. The second line contains n integers separated by spaces.",
            "Print the maximum array element.",
            "1 <= n <= 1000, -1000000000 <= arr[i] <= 1000000000",
            Arrays.asList("Scan through the array once.", "Keep track of the current maximum."),
            "6\n-5 0 3 1 2 -1",
            "3",
            "The largest element among the list is 3.",
            Arrays.asList(
                new TestCase("3\n7 7 7", "7", "All numbers are identical."),
                new TestCase("5\n-2 -8 -1 -3 -5", "-1", "Maximum among negatives.")
            ),
            Arrays.asList(
                new TestCase("4\n1 2 3 4", "4"),
                new TestCase("2\n100 99", "100")
            )
        ));

        problems.add(createProblem(
            "4",
            "Count Even Numbers",
            "Count how many even numbers are present in an integer array.",
            "ARRAYS",
            Arrays.asList("arrays", "count", "basic"),
            10,
            15,
            "EASY",
            false,
            "The first line contains n. The second line contains n integers separated by spaces.",
            "Print a single integer for the count of even numbers.",
            "1 <= n <= 1000, -100000 <= arr[i] <= 100000",
            Arrays.asList("Check each number using modulo 2.", "Do not count odd numbers."),
            "7\n1 2 3 4 5 6 7",
            "3",
            "The even numbers are 2, 4, and 6 for a total of 3.",
            Arrays.asList(
                new TestCase("5\n2 2 2 2 2", "5", "All values are even."),
                new TestCase("4\n1 3 5 7", "0", "No even numbers.")
            ),
            Arrays.asList(
                new TestCase("3\n10 11 12", "2"),
                new TestCase("6\n8 9 10 11 12 13", "3")
            )
        ));

        problems.add(createProblem(
            "5",
            "Move Zeroes",
            "Move all zeroes in an array to the end while preserving the order of non-zero elements.",
            "ARRAYS",
            Arrays.asList("arrays", "two-pointers", "basic"),
            10,
            20,
            "EASY",
            false,
            "The first line contains n. The second line contains n integers separated by spaces.",
            "Print the transformed array after moving zeroes to the end.",
            "1 <= n <= 1000, 0 <= arr[i] <= 1000",
            Arrays.asList("Use two indices to slide non-zero values forward.", "Write zeroes at the end."),
            "6\n0 1 0 3 12 0",
            "1 3 12 0 0 0",
            "All non-zero values remain in order and zeroes shift to the end.",
            Arrays.asList(
                new TestCase("4\n0 0 1 0", "1 0 0 0", "All zeroes move to the right."),
                new TestCase("5\n1 2 3 0 0", "1 2 3 0 0", "No change if zeroes already at the end.")
            ),
            Arrays.asList(
                new TestCase("3\n0 5 0", "5 0 0"),
                new TestCase("5\n4 0 4 0 4", "4 4 4 0 0")
            )
        ));

        problems.add(createProblem(
            "6",
            "Remove Duplicates from Sorted Array",
            "Given a sorted array, remove duplicate values and return the length of the unique prefix.",
            "ARRAYS",
            Arrays.asList("arrays", "duplicates", "basic"),
            10,
            20,
            "EASY",
            false,
            "The first line contains n. The second line contains n sorted integers separated by spaces.",
            "Print the new length after duplicates are removed.",
            "1 <= n <= 1000, -1000000000 <= arr[i] <= 1000000000",
            Arrays.asList("Use a write pointer for unique elements.", "Do not allocate a second array."),
            "7\n1 1 2 2 2 3 4",
            "4",
            "The unique values are [1,2,3,4], length 4.",
            Arrays.asList(
                new TestCase("5\n2 2 2 2 2", "1", "Only one unique element."),
                new TestCase("3\n1 2 3", "3", "No duplicates in the input.")
            ),
            Arrays.asList(
                new TestCase("6\n1 1 1 2 3 3", "3"),
                new TestCase("4\n0 0 1 1", "2")
            )
        ));

        problems.add(createProblem(
            "7",
            "Rotate Array Right",
            "Rotate an array to the right by k positions.",
            "ARRAYS",
            Arrays.asList("arrays", "rotation", "basic"),
            10,
            20,
            "EASY",
            false,
            "The first line contains n and k. The second line contains n integers separated by spaces.",
            "Print the array after rotating right by k positions.",
            "1 <= n <= 1000, 0 <= k <= 1000, -100000 <= arr[i] <= 100000",
            Arrays.asList("Compute k modulo n.", "Preserve element order while rotating."),
            "5 2\n1 2 3 4 5",
            "4 5 1 2 3",
            "Rotate the array right by 2 positions.",
            Arrays.asList(
                new TestCase("4 1\n1 2 3 4", "4 1 2 3", "A single rotation to the right."),
                new TestCase("3 3\n5 6 7", "5 6 7", "A full rotation returns the original array.")
            ),
            Arrays.asList(
                new TestCase("5 4\n1 2 3 4 5", "2 3 4 5 1"),
                new TestCase("2 5\n9 8", "8 9")
            )
        ));

        problems.add(createProblem(
            "8",
            "Merge Sorted Arrays",
            "Merge two sorted arrays into a single sorted array.",
            "ARRAYS",
            Arrays.asList("arrays", "merge", "basic"),
            10,
            20,
            "EASY",
            false,
            "The first line contains n and m. The second line contains n sorted integers. The third line contains m sorted integers.",
            "Print the merged sorted array.",
            "0 <= n,m <= 1000, -100000 <= arr[i], brr[i] <= 100000",
            Arrays.asList("Use two pointers to merge efficiently.", "Handle empty arrays gracefully."),
            "3 3\n1 3 5\n2 4 6",
            "1 2 3 4 5 6",
            "Merge the two sorted input arrays into one sorted output.",
            Arrays.asList(
                new TestCase("2 3\n1 2\n3 4 5", "1 2 3 4 5", "Merge two sorted lists."),
                new TestCase("0 3\n\n1 2 3", "1 2 3", "Merge when first array is empty.")
            ),
            Arrays.asList(
                new TestCase("3 2\n2 4 6\n1 3", "1 2 3 4 6 6"),
                new TestCase("1 1\n5\n5", "5 5")
            )
        ));

        problems.add(createProblem(
            "9",
            "Two Sum",
            "Given an array of integers and a target value, find two numbers that add up to the target and print their indices.",
            "ARRAYS",
            Arrays.asList("arrays", "two-sum", "basic"),
            10,
            25,
            "EASY",
            false,
            "The first line contains n and the target value. The second line contains n integers.",
            "Print two indices (0-based) separated by a space. If multiple answers exist, any valid pair is acceptable.",
            "2 <= n <= 1000, -100000 <= arr[i], target <= 100000",
            Arrays.asList("Use a hash map to track seen values.", "Return immediately after finding a valid pair."),
            "4 9\n2 7 11 15",
            "0 1",
            "Indices 0 and 1 correspond to values 2 and 7, which sum to 9.",
            Arrays.asList(
                new TestCase("3 6\n3 2 4", "1 2", "Indices 1 and 2 sum to 6."),
                new TestCase("5 0\n-3 4 3 90 1", "0 2", "Negative plus positive equals target.")
            ),
            Arrays.asList(
                new TestCase("2 5\n1 4", "0 1"),
                new TestCase("4 8\n4 4 2 6", "0 1")
            )
        ));

        problems.add(createProblem(
            "10",
            "Contains Duplicate",
            "Determine whether an array contains any duplicate elements.",
            "ARRAYS",
            Arrays.asList("arrays", "duplicate", "basic"),
            10,
            15,
            "EASY",
            false,
            "The first line contains n. The second line contains n integers.",
            "Print YES if a duplicate exists, otherwise print NO.",
            "1 <= n <= 1000, -100000 <= arr[i] <= 100000",
            Arrays.asList("Use a set to detect duplicates.", "Stop scanning once you find a repeat."),
            "5\n1 2 3 4 5",
            "NO",
            "The array contains no duplicate values.",
            Arrays.asList(
                new TestCase("4\n1 2 2 3", "YES", "Value 2 appears twice."),
                new TestCase("3\n7 7 7", "YES", "All values are duplicates." )
            ),
            Arrays.asList(
                new TestCase("3\n-1 0 1", "NO"),
                new TestCase("5\n1 2 3 4 4", "YES")
            )
        ));

        problems.add(createProblem(
            "11",
            "Intersection of Two Arrays",
            "Find the unique intersection of two arrays and print the sorted result.",
            "ARRAYS",
            Arrays.asList("arrays", "set", "basic"),
            10,
            20,
            "EASY",
            false,
            "The first line contains n and m. The second line contains n integers. The third line contains m integers.",
            "Print the unique intersection values in sorted order separated by spaces.",
            "0 <= n,m <= 1000, -100000 <= arr[i], brr[i] <= 100000",
            Arrays.asList("Use sets for both arrays.", "Only output each intersection value once."),
            "4 5\n1 2 2 3\n2 3 4 2 2",
            "2 3",
            "The common unique values between both arrays are 2 and 3.",
            Arrays.asList(
                new TestCase("3 3\n1 1 2\n2 2 3", "2", "Only value 2 appears in both arrays."),
                new TestCase("2 2\n5 6\n7 8", "", "No common values produces empty output.")
            ),
            Arrays.asList(
                new TestCase("3 4\n1 2 3\n3 4 5 2", "2 3"),
                new TestCase("1 1\n0\n0", "0")
            )
        ));

        problems.add(createProblem(
            "12",
            "Plus One",
            "Given an array of digits representing a non-negative integer, add one to the integer and return the resulting digits.",
            "ARRAYS",
            Arrays.asList("arrays", "math", "basic"),
            10,
            20,
            "EASY",
            false,
            "The first line contains n. The second line contains n digits separated by spaces.",
            "Print the resulting digits separated by spaces.",
            "1 <= n <= 1000, 0 <= digits[i] <= 9",
            Arrays.asList("Handle carry propagation correctly.", "Do not convert to an integer directly for large inputs."),
            "3\n1 2 3",
            "1 2 4",
            "Adding one to 123 gives 124.",
            Arrays.asList(
                new TestCase("3\n9 9 9", "1 0 0 0", "Carry over expands the digit length."),
                new TestCase("4\n1 0 0 9", "1 0 1 0", "Carry carries through zeros." )
            ),
            Arrays.asList(
                new TestCase("1\n0", "1"),
                new TestCase("2\n1 9", "2 0")
            )
        ));

        problems.add(createProblem(
            "13",
            "Majority Element",
            "Find the element that appears more than n/2 times in an array.",
            "ARRAYS",
            Arrays.asList("arrays", "frequency", "basic"),
            10,
            20,
            "EASY",
            false,
            "The first line contains n. The second line contains n integers separated by spaces.",
            "Print the majority element. It is guaranteed that one exists.",
            "1 <= n <= 1000, -100000 <= arr[i] <= 100000",
            Arrays.asList("Use the Boyer-Moore voting algorithm or a frequency map.", "A majority element appears more than half the time."),
            "5\n3 3 4 3 2",
            "3",
            "3 appears 3 out of 5 times, making it the majority element.",
            Arrays.asList(
                new TestCase("3\n1 1 2", "1", "1 appears more than half the time."),
                new TestCase("5\n2 2 2 1 1", "2", "2 is the majority element.")
            ),
            Arrays.asList(
                new TestCase("3\n7 7 7", "7"),
                new TestCase("5\n0 0 1 0 1", "0")
            )
        ));

        problems.add(createProblem(
            "14",
            "Check Monotonic Array",
            "Determine whether an array is monotonic (either entirely non-decreasing or non-increasing).",
            "ARRAYS",
            Arrays.asList("arrays", "monotonic", "basic"),
            10,
            20,
            "EASY",
            false,
            "The first line contains n. The second line contains n integers separated by spaces.",
            "Print YES if the array is monotonic, otherwise print NO.",
            "1 <= n <= 1000, -100000 <= arr[i] <= 100000",
            Arrays.asList("Check both increasing and decreasing conditions.", "A constant sequence is monotonic."),
            "5\n1 2 2 3 4",
            "YES",
            "The sequence never decreases, so it is monotonic.",
            Arrays.asList(
                new TestCase("4\n4 3 2 1", "YES", "Strictly decreasing arrays are monotonic."),
                new TestCase("4\n1 3 2 4", "NO", "The sequence changes direction." )
            ),
            Arrays.asList(
                new TestCase("5\n2 2 2 2 2", "YES"),
                new TestCase("3\n1 0 1", "NO")
            )
        ));

        problems.add(createProblem(
            "15",
            "Count Pairs with Target Sum",
            "Count the number of unique pairs in an array whose values add up to a target.",
            "ARRAYS",
            Arrays.asList("arrays", "pairs", "basic"),
            10,
            25,
            "EASY",
            false,
            "The first line contains n and target. The second line contains n integers separated by spaces.",
            "Print the count of unique pairs that sum to the target.",
            "1 <= n <= 1000, -100000 <= arr[i], target <= 100000",
            Arrays.asList("Use a set to track complements.", "Each pair should be counted only once."),
            "5 6\n1 5 7 -1 5",
            "2",
            "The pairs (1,5) and (7,-1) both sum to 6.",
            Arrays.asList(
                new TestCase("4 4\n2 2 2 2", "1", "Only one unique pair using two different elements."),
                new TestCase("3 10\n5 5 5", "1", "Pair of identical values can count once." )
            ),
            Arrays.asList(
                new TestCase("4 5\n1 2 3 4", "2"),
                new TestCase("3 0\n-1 0 1", "1")
            )
        ));

        problems.add(createProblem(
            "16",
            "Valid Anagram",
            "Check if two strings are anagrams of each other.",
            "STRINGS",
            Arrays.asList("strings", "anagram", "basic"),
            10,
            20,
            "EASY",
            false,
            "The first line contains the first string. The second line contains the second string.",
            "Print YES if they are anagrams, otherwise print NO.",
            "1 <= string length <= 1000, lowercase letters only.",
            Arrays.asList("Sort both strings or count characters.", "Strings must contain exactly the same characters."),
            "listen\nsilent",
            "YES",
            "Both strings contain the same letters in different order.",
            Arrays.asList(
                new TestCase("abc\ncba", "YES", "Same characters rearranged."),
                new TestCase("hello\nhelol", "YES", "Same letters with duplicates." )
            ),
            Arrays.asList(
                new TestCase("abc\ndef", "NO"),
                new TestCase("aabb\nabab", "YES")
            )
        ));

        problems.add(createProblem(
            "17",
            "First Unique Character",
            "Find the first non-repeating character in a string and print its index or -1 if none exists.",
            "STRINGS",
            Arrays.asList("strings", "frequency", "basic"),
            10,
            20,
            "EASY",
            false,
            "The input contains a single string on one line.",
            "Print the zero-based index of the first unique character, or -1.",
            "1 <= length <= 1000, lowercase letters only.",
            Arrays.asList("Count occurrences of each character.", "Scan the string again to find the first unique character."),
            "leetcode",
            "0",
            "The first unique character is 'l' at index 0.",
            Arrays.asList(
                new TestCase("loveleetcode", "2", "The first non-repeating letter is 'v'."),
                new TestCase("aabb", "-1", "No unique characters exist." )
            ),
            Arrays.asList(
                new TestCase("abcabc\n", "-1"),
                new TestCase("swiss\n", "0")
            )
        ));

        problems.add(createProblem(
            "18",
            "Reverse Words in a String",
            "Reverse the order of words in a sentence. Words are separated by spaces.",
            "STRINGS",
            Arrays.asList("strings", "reverse", "basic"),
            10,
            20,
            "EASY",
            false,
            "The input contains a full sentence on one line.",
            "Print the words in reverse order separated by a single space.",
            "Sentence length <= 1000, words separated by spaces.",
            Arrays.asList("Split the sentence into words.", "Join the words back in reverse order."),
            "the sky is blue",
            "blue is sky the",
            "The word order is reversed but word contents remain the same.",
            Arrays.asList(
                new TestCase("hello world", "world hello", "Reverse two words."),
                new TestCase("a b c", "c b a", "Reverse three words." )
            ),
            Arrays.asList(
                new TestCase("single", "single"),
                new TestCase("one two three", "three two one")
            )
        ));

        problems.add(createProblem(
            "19",
            "Palindrome String",
            "Determine whether a string reads the same backward as forward.",
            "STRINGS",
            Arrays.asList("strings", "palindrome", "basic"),
            10,
            20,
            "EASY",
            false,
            "The input contains a single string on one line.",
            "Print YES if the string is a palindrome, otherwise print NO.",
            "1 <= length <= 1000, lowercase letters only.",
            Arrays.asList("Compare characters from both ends.", "Stop early when a mismatch is found."),
            "racecar",
            "YES",
            "The word reads the same forwards and backwards.",
            Arrays.asList(
                new TestCase("level", "YES", "A palindrome example."),
                new TestCase("hello", "NO", "Not a palindrome." )
            ),
            Arrays.asList(
                new TestCase("radar", "YES"),
                new TestCase("abc", "NO")
            )
        ));

        problems.add(createProblem(
            "20",
            "Count Vowels and Consonants",
            "Count vowels and consonants in a given string.",
            "STRINGS",
            Arrays.asList("strings", "count", "basic"),
            10,
            20,
            "EASY",
            false,
            "The input contains a single lowercase string on one line.",
            "Print two integers: vowel count and consonant count separated by a space.",
            "1 <= length <= 1000, lowercase letters only.",
            Arrays.asList("Recognize vowels a, e, i, o, u.", "All other letters are consonants."),
            "hello",
            "2 3",
            "The string has 2 vowels (e, o) and 3 consonants (h, l, l).",
            Arrays.asList(
                new TestCase("programming", "3 8", "Count vowels and consonants."),
                new TestCase("aeiou", "5 0", "All vowels." )
            ),
            Arrays.asList(
                new TestCase("xyz", "0 3"),
                new TestCase("apple", "2 3")
            )
        ));

        problems.add(createProblem(
            "21",
            "Longest Common Prefix",
            "Find the longest common prefix string among an array of strings.",
            "STRINGS",
            Arrays.asList("strings", "prefix", "basic"),
            10,
            25,
            "EASY",
            false,
            "The first line contains n. Each of the next n lines contains one string.",
            "Print the longest common prefix. If none exists, print an empty line.",
            "1 <= n <= 100, string length <= 100.",
            Arrays.asList("Compare characters column by column.", "Stop when any string differs."),
            "3\nflower\nflow\nflight",
            "fl",
            "The longest common prefix of all strings is 'fl'.",
            Arrays.asList(
                new TestCase("2\ndog\ncat", "", "No common prefix."),
                new TestCase("4\ninterview\nintegrate\ninside\ninstinct", "in", "Common prefix is 'in'." )
            ),
            Arrays.asList(
                new TestCase("3\napple\napple\napple", "apple"),
                new TestCase("2\nabc\n", "")
            )
        ));

        problems.add(createProblem(
            "22",
            "Replace Spaces",
            "Replace all spaces in a string with '%20'.",
            "STRINGS",
            Arrays.asList("strings", "replace", "basic"),
            10,
            20,
            "EASY",
            false,
            "The input contains a single string on one line.",
            "Print the transformed string with spaces replaced.",
            "Length <= 1000. The string may contain spaces.",
            Arrays.asList("Process each character in the string.", "Do not modify non-space characters."),
            "Mr John Smith",
            "Mr%20John%20Smith",
            "All spaces become %20 separators.",
            Arrays.asList(
                new TestCase("Hello World", "Hello%20World", "Replace one space."),
                new TestCase("a b c", "a%20b%20c", "Replace multiple spaces." )
            ),
            Arrays.asList(
                new TestCase(" No", "%20No"),
                new TestCase("space ", "space%20")
            )
        ));

        problems.add(createProblem(
            "23",
            "String Compression",
            "Compress a string by replacing consecutive repeated characters with the character followed by the count.",
            "STRINGS",
            Arrays.asList("strings", "compression", "basic"),
            10,
            25,
            "EASY",
            false,
            "The input contains a single string on one line.",
            "Print the compressed string.",
            "1 <= length <= 1000, lowercase letters only.",
            Arrays.asList("Count consecutive repeated characters.", "Append the character and count."),
            "aaabbc",
            "a3b2c1",
            "Each run of repeated letters is replaced by letter+count.",
            Arrays.asList(
                new TestCase("abc", "a1b1c1", "No repeated characters."),
                new TestCase("zzzz", "z4", "All characters are the same." )
            ),
            Arrays.asList(
                new TestCase("aabcc", "a2b1c2"),
                new TestCase("x", "x1")
            )
        ));

        problems.add(createProblem(
            "24",
            "Valid Parentheses",
            "Check whether a string containing parentheses is valid. Only parentheses characters are allowed.",
            "STRINGS",
            Arrays.asList("strings", "stack", "basic"),
            10,
            25,
            "EASY",
            false,
            "The input contains a single string of parentheses on one line.",
            "Print YES if the string is valid, otherwise print NO.",
            "1 <= length <= 1000, characters are ()[]{}.",
            Arrays.asList("Use a stack to match parentheses.", "Every closing bracket must match the last open bracket."),
            "()[]{}",
            "YES",
            "All parentheses are properly opened and closed.",
            Arrays.asList(
                new TestCase("(]", "NO", "Mismatched brackets."),
                new TestCase("([{}])", "YES", "Nested matched brackets." )
            ),
            Arrays.asList(
                new TestCase("((", "NO"),
                new TestCase("{}[]", "YES")
            )
        ));

        problems.add(createProblem(
            "25",
            "String to Integer",
            "Convert a string representing a signed integer to a number and print the result.",
            "STRINGS",
            Arrays.asList("strings", "parsing", "basic"),
            10,
            25,
            "EASY",
            false,
            "The input contains a single line with an optional sign followed by digits.",
            "Print the integer value represented by the string.",
            "The string represents a valid integer with optional + or - sign.",
            Arrays.asList("Handle optional leading + or - signs.", "Ignore any whitespace at the ends."),
            "+123",
            "123",
            "Plus sign returns the positive integer.",
            Arrays.asList(
                new TestCase("-45", "-45", "Negative integer conversion."),
                new TestCase("0", "0", "Zero remains zero." )
            ),
            Arrays.asList(
                new TestCase("+0", "0"),
                new TestCase("-999", "-999")
            )
        ));

        problems.add(createProblem(
            "26",
            "Count Substring Occurrences",
            "Count how many times a substring appears inside a larger string.",
            "STRINGS",
            Arrays.asList("strings", "substring", "basic"),
            10,
            25,
            "EASY",
            false,
            "The first line contains the main string. The second line contains the substring.",
            "Print the number of non-overlapping occurrences of the substring.",
            "Main string length <= 1000, substring length <= 100.",
            Arrays.asList("Scan the string for matching segments.", "Count each valid occurrence without reusing characters."),
            "abababa\naba",
            "2",
            "The substring 'aba' appears twice without overlap.",
            Arrays.asList(
                new TestCase("aaaa\naa", "2", "Non-overlapping occurrences count."),
                new TestCase("hello\nlo", "1", "Single match." )
            ),
            Arrays.asList(
                new TestCase("abcabc\nabc", "1"),
                new TestCase("aaa\na", "3")
            )
        ));

        problems.add(createProblem(
            "27",
            "Longest Palindromic Prefix",
            "Find the longest prefix of the string that is also a palindrome.",
            "STRINGS",
            Arrays.asList("strings", "palindrome", "basic"),
            10,
            25,
            "EASY",
            false,
            "The input contains a single string on one line.",
            "Print the longest palindromic prefix.",
            "1 <= length <= 1000, lowercase letters only.",
            Arrays.asList("Check prefixes from largest to smallest.", "A single character is always a palindrome."),
            "abacaba",
            "aba",
            "The longest prefix that is a palindrome is 'aba'.",
            Arrays.asList(
                new TestCase("racecar", "racecar", "The entire string is palindromic."),
                new TestCase("abc", "a", "Only first letter is a palindrome." )
            ),
            Arrays.asList(
                new TestCase("levelup", "level"),
                new TestCase("noonabc", "noon")
            )
        ));

        problems.add(createProblem(
            "28",
            "Check Rotation",
            "Check if one string is a rotation of another.",
            "STRINGS",
            Arrays.asList("strings", "rotation", "basic"),
            10,
            25,
            "EASY",
            false,
            "The first line contains the source string. The second line contains the target string.",
            "Print YES if the second string is a rotation of the first, otherwise print NO.",
            "1 <= length <= 1000, lowercase letters only.",
            Arrays.asList("A rotation can be detected by searching in source+source.", "Lengths must match."),
            "abcd\ndbca",
            "YES",
            "The second string is a rotation of the first.",
            Arrays.asList(
                new TestCase("abc\n cab", "NO", "Different lengths or spaces break rotation."),
                new TestCase("waterbottle\n erbottlewat", "YES", "Classic rotation example." )
            ),
            Arrays.asList(
                new TestCase("abc\ncab", "YES"),
                new TestCase("abc\nabc", "YES")
            )
        ));

        problems.add(createProblem(
            "29",
            "Remove All Adjacent Duplicates in String",
            "Remove all adjacent duplicate characters from a string repeatedly until none remain.",
            "STRINGS",
            Arrays.asList("strings", "stack", "basic"),
            10,
            25,
            "EASY",
            false,
            "The input contains a single string on one line.",
            "Print the resulting string after removing adjacent duplicates.",
            "1 <= length <= 1000, lowercase letters only.",
            Arrays.asList("Use a stack or dynamic string builder.", "When duplicates are removed, the result may expose new adjacent duplicates."),
            "abbaca",
            "ca",
            "Remove duplicate pairs until the string stabilizes.",
            Arrays.asList(
                new TestCase("azxxzy", "ay", "Repeat removals until stable."),
                new TestCase("aabbcc", "", "All characters cancel out." )
            ),
            Arrays.asList(
                new TestCase("abccba", "", "Everything cancels by repeated removal."),
                new TestCase("abc", "abc")
            )
        ));

        problems.add(createProblem(
            "30",
            "Sort Characters by Frequency",
            "Sort the characters of a string by decreasing frequency and print the result.",
            "STRINGS",
            Arrays.asList("strings", "frequency", "basic"),
            10,
            30,
            "EASY",
            false,
            "The input contains a single string on one line.",
            "Print the characters sorted by frequency in descending order. Characters with equal frequency can appear in any order.",
            "1 <= length <= 1000, lowercase letters only.",
            Arrays.asList("Count the frequency of each character.", "Sort by frequency before output."),
            "tree",
            "eetr",
            "The letter 'e' appears twice, followed by the other characters once.",
            Arrays.asList(
                new TestCase("cccaaa", "aaaccc", "Both characters appear three times."),
                new TestCase("Aabb\n", "bbaA", "Case-sensitive sorting by frequency." )
            ),
            Arrays.asList(
                new TestCase("apple", "ppale"),
                new TestCase("zzzyy", "zzzyy")
            )
        ));

        problemRepository.saveAll(problems);
        System.out.println("✅ Seeded 30 basic array and string problems successfully.");
    }

    private Problem createProblem(
        String id,
        String title,
        String statement,
        String category,
        List<String> tags,
        int points,
        int estimatedTime,
        String difficulty,
        boolean isPremium,
        String inputFormat,
        String outputFormat,
        String constraints,
        List<String> hints,
        String exampleInput,
        String exampleOutput,
        String exampleExplanation,
        List<TestCase> publicTestCases,
        List<TestCase> hiddenTestCases
    ) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setTitle(title);
        problem.setStatement(statement);
        problem.setCategory(category);
        problem.setTags(tags);
        problem.setPoints(points);
        problem.setEstimatedTime(estimatedTime);
        problem.setDifficulty(difficulty);
        problem.setPremium(isPremium);
        problem.setExecutionMode("STDIN_STDOUT");
        problem.setInputFormat(inputFormat);
        problem.setOutputFormat(outputFormat);
        problem.setConstraints(constraints);
        problem.setHints(hints);
        problem.setExampleInput(exampleInput);
        problem.setExampleOutput(exampleOutput);
        problem.setExampleExplanation(exampleExplanation);
        problem.setPublicTestCases(publicTestCases);
        problem.setHiddenTestCases(hiddenTestCases);
        problem.setCreatedBy("system");
        problem.setCreatedAt(new Date());
        return problem;
    }
}
