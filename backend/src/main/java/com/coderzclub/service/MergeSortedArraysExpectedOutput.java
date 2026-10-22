package com.coderzclub.service;

import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;

/**
 * Pure helper for Merge Sorted Arrays expected-output checks in tests.
 * Not invoked at application startup.
 */
public final class MergeSortedArraysExpectedOutput {
    private MergeSortedArraysExpectedOutput() {}

    public static boolean isMergeSortedArraysProblem(String title, Integer numericId) {
        if (numericId != null && numericId == 8) {
            return true;
        }
        return title != null && title.trim().equalsIgnoreCase("Merge Sorted Arrays");
    }

    public static Optional<String> expectedIfValidSortedInput(String stdin) {
        Parsed parsed = parse(stdin);
        if (parsed == null || !parsed.bothSorted) {
            return Optional.empty();
        }
        return Optional.of(format(merge(parsed.left, parsed.right)));
    }

    public static String twoPointerMergeOutput(int[] left, int[] right) {
        return format(merge(left, right));
    }

    static Parsed parse(String stdin) {
        if (stdin == null) {
            return null;
        }
        try (Scanner scanner = new Scanner(stdin)) {
            scanner.useLocale(Locale.US);
            if (!scanner.hasNextInt()) {
                return null;
            }
            int n = scanner.nextInt();
            if (!scanner.hasNextInt()) {
                return null;
            }
            int m = scanner.nextInt();
            if (n < 0 || m < 0 || n > 1000 || m > 1000) {
                return null;
            }
            int[] left = new int[n];
            for (int i = 0; i < n; i++) {
                if (!scanner.hasNextInt()) {
                    return null;
                }
                left[i] = scanner.nextInt();
            }
            int[] right = new int[m];
            for (int i = 0; i < m; i++) {
                if (!scanner.hasNextInt()) {
                    return null;
                }
                right[i] = scanner.nextInt();
            }
            Parsed parsed = new Parsed();
            parsed.left = left;
            parsed.right = right;
            parsed.bothSorted = isNonDecreasing(left) && isNonDecreasing(right);
            return parsed;
        }
    }

    static boolean isNonDecreasing(int[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[i - 1]) {
                return false;
            }
        }
        return true;
    }

    static int[] merge(int[] left, int[] right) {
        int[] result = new int[left.length + right.length];
        int i = 0;
        int j = 0;
        int k = 0;
        while (i < left.length && j < right.length) {
            if (left[i] <= right[j]) {
                result[k++] = left[i++];
            } else {
                result[k++] = right[j++];
            }
        }
        while (i < left.length) {
            result[k++] = left[i++];
        }
        while (j < right.length) {
            result[k++] = right[j++];
        }
        return result;
    }

    static String format(int[] values) {
        if (values.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(' ');
            }
            builder.append(values[index]);
        }
        return builder.toString();
    }

    static final class Parsed {
        int[] left;
        int[] right;
        boolean bothSorted;
    }
}
