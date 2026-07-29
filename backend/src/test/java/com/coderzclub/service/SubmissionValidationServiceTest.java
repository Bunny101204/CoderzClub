package com.coderzclub.service;

import com.coderzclub.model.Problem;
import com.coderzclub.model.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionValidationServiceTest {

    private SubmissionValidationService validationService;

    @BeforeEach
    void setUp() throws Exception {
        validationService = new SubmissionValidationService();
        setField(validationService, "maxCodeLength", 200_000);
        setField(validationService, "maxTestcaseCount", 50);
        setField(validationService, "maxTestcaseInputLength", 200_000);
        setField(validationService, "maxTestcaseOutputLength", 200_000);
        setField(validationService, "maxStdoutLength", 64_000);
        setField(validationService, "maxStderrLength", 64_000);
        setField(validationService, "maxCompileOutputLength", 32_000);
        setField(validationService, "maxMemoryKb", 512_000);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = SubmissionValidationService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void emptyCodeRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validationService.validateSubmissionRequest("p1", "   ", 50));
        assertTrue(ex.getMessage().contains("required"));
    }

    @Test
    void oversizedCodeRejected() {
        String hugeCode = "class X {" + "void f(){}".repeat(100000);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validationService.validateSubmissionRequest("p1", hugeCode, 50));
        assertTrue(ex.getMessage().contains("maximum length"));
    }

    @Test
    void unsupportedLanguageRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validationService.validateSubmissionRequest("p1", "print(1)", 999));
        assertTrue(ex.getMessage().contains("unsupported"));
    }

    @Test
    void tooManyTestcasesRejected() {
        Problem problem = new Problem();
        problem.setPublicTestCases(new java.util.ArrayList<>(List.of(new TestCase("1", "1"))));
        problem.setHiddenTestCases(new java.util.ArrayList<>(List.of(new TestCase("1", "1"))));
        for (int i = 0; i < 60; i++) {
            problem.getPublicTestCases().add(new TestCase("1", "1"));
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validationService.validateProblemTestcases(problem));
        assertTrue(ex.getMessage().contains("maximum count"));
    }

    @Test
    void hugeTestcaseInputRejected() {
        Problem problem = new Problem();
        problem.setPublicTestCases(List.of(new TestCase("x".repeat(300000), "1")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validationService.validateProblemTestcases(problem));
        assertTrue(ex.getMessage().contains("maximum size"));
    }

    @Test
    void hugeOutputTruncated() {
        Map<String, Object> response = validationService.sanitizeJudge0Response(Map.of(
                "stdout", "x".repeat(70000)
        ));
        assertTrue(((String) response.get("stdout")).contains("[truncated]"));
    }

    @Test
    void rawJudge0ResponseIsNotStored() {
        Map<String, Object> response = validationService.sanitizeJudge0Response(Map.of(
                "status", Map.of("id", 3, "description", "Accepted"),
                "stdout", "hello",
                "stderr", "world",
                "compile_output", "done"
        ));
        assertTrue(response.containsKey("status"));
        assertTrue(response.containsKey("stdout"));
        assertTrue(response.get("stdout") instanceof String);
    }
}
