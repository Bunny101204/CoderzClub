package com.coderzclub.service;

import com.coderzclub.model.SubmissionJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class Judge0ExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(Judge0ExecutionService.class);

    private static final String JUDGE0_URL = "https://judge0-ce.p.rapidapi.com/submissions?base64_encoded=false&wait=true";
    private static final String JUDGE0_HOST = "judge0-ce.p.rapidapi.com";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${judge0.api.key:${JUDGE0_API_KEY:${VITE_JUDGE0_API_KEY:}}}")
    private String judge0ApiKey;

    /**
     * Execute all test cases for a submission
     */
    public List<SubmissionJob.TestResult> executeTestCases(String code, Integer languageId,
                                                          List<SubmissionJob.TestCase> publicTestCases,
                                                          List<SubmissionJob.TestCase> hiddenTestCases) {
        List<SubmissionJob.TestResult> results = new ArrayList<>();

        // Execute public test cases
        if (publicTestCases != null) {
            for (SubmissionJob.TestCase testCase : publicTestCases) {
                SubmissionJob.TestResult result = executeSingleTest(code, languageId, testCase);
                results.add(result);
            }
        }

        // Execute hidden test cases
        if (hiddenTestCases != null) {
            for (SubmissionJob.TestCase testCase : hiddenTestCases) {
                SubmissionJob.TestResult result = executeSingleTest(code, languageId, testCase);
                results.add(result);
            }
        }

        return results;
    }

    /**
     * Execute a single test case
     */
    private SubmissionJob.TestResult executeSingleTest(String code, Integer languageId, SubmissionJob.TestCase testCase) {
        SubmissionJob.TestResult result = new SubmissionJob.TestResult();
        result.setInput(testCase.getInput());
        result.setExpectedOutput(testCase.getExpectedOutput());

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("language_id", languageId);
            payload.put("source_code", code);
            if (testCase.getInput() != null && !testCase.getInput().trim().isEmpty()) {
                payload.put("stdin", testCase.getInput());
            }

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(JUDGE0_URL))
                    .timeout(Duration.ofSeconds(40))
                    .header("Content-Type", "application/json")
                    .header("X-RapidAPI-Key", judge0ApiKey)
                    .header("X-RapidAPI-Host", JUDGE0_HOST)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            int maxRetries = 3;
            int attempt = 0;
            HttpResponse<String> response;
            while (true) {
                response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status != 429 && status != 503) {
                    break;
                }
                attempt++;
                if (attempt > maxRetries) {
                    break;
                }
                Thread.sleep(500L * attempt);
            }

            Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);

            // Extract execution details
            Long runtime = null;
            Long memory = null;
            if (responseMap.get("time") != null) {
                try {
                    runtime = Math.round(Double.parseDouble(responseMap.get("time").toString()) * 1000);
                } catch (Exception e) {
                    logger.warn("Failed to parse runtime: {}", responseMap.get("time"));
                }
            }
            if (responseMap.get("memory") != null) {
                try {
                    memory = Long.parseLong(responseMap.get("memory").toString()) * 1024; // Convert KB to bytes
                } catch (Exception e) {
                    logger.warn("Failed to parse memory: {}", responseMap.get("memory"));
                }
            }

            // Determine actual output
            String actualOutput = "";
            if (responseMap.get("stdout") != null && !responseMap.get("stdout").toString().trim().isEmpty()) {
                actualOutput = responseMap.get("stdout").toString().trim();
            } else if (responseMap.get("stderr") != null && !responseMap.get("stderr").toString().trim().isEmpty()) {
                actualOutput = responseMap.get("stderr").toString().trim();
            } else if (responseMap.get("compile_output") != null && !responseMap.get("compile_output").toString().trim().isEmpty()) {
                actualOutput = responseMap.get("compile_output").toString().trim();
            } else {
                actualOutput = "No Output";
            }

            result.setActualOutput(actualOutput);
            result.setRuntime(runtime);
            result.setMemory(memory);
            result.setExecutionDetails(responseMap);

            // Check for errors
            String errorType = parseErrorType(responseMap);
            if (errorType != null) {
                result.setPassed(false);
                result.setErrorType(errorType);
                result.setErrorMessage(parseErrorMessage(responseMap));

                // Observability: Log execution error
                logger.warn("judge0_execution_error",
                    "languageId", languageId,
                    "errorType", errorType,
                    "runtimeMs", runtime,
                    "memoryBytes", memory);
            } else {
                // Check if output matches expected
                String expected = testCase.getExpectedOutput() != null ? testCase.getExpectedOutput().trim() : "";
                boolean passed = actualOutput.equals(expected);
                result.setPassed(passed);

                // Observability: Log execution result
                logger.info("judge0_execution_completed",
                    "languageId", languageId,
                    "passed", passed,
                    "runtimeMs", runtime,
                    "memoryBytes", memory,
                    "outputLength", actualOutput.length());
            }

        } catch (Exception e) {
            logger.error("Failed to execute test case", e);
            result.setPassed(false);
            result.setErrorType("Execution Error");
            result.setErrorMessage("Failed to execute code: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse error type from Judge0 response
     */
    private String parseErrorType(Map<String, Object> response) {
        if (response == null) return null;

        Object status = response.get("status");
        if (status instanceof Map) {
            Integer id = (Integer) ((Map) status).get("id");
            if (id != null) {
                switch (id) {
                    case 6: return "Compilation Error";
                    case 7:
                    case 8:
                    case 9:
                    case 10:
                    case 11:
                    case 12: return "Runtime Error";
                    case 5: return "Time Limit Exceeded";
                    case 4: return "Memory Limit Exceeded";
                }
            }
        }

        // Check for compile output as alternative indicator
        if (response.get("compile_output") != null && !response.get("compile_output").toString().trim().isEmpty()) {
            return "Compilation Error";
        }

        return null;
    }

    /**
     * Parse error message from Judge0 response
     */
    private String parseErrorMessage(Map<String, Object> response) {
        if (response == null) return null;

        if (response.get("compile_output") != null && !response.get("compile_output").toString().trim().isEmpty()) {
            return response.get("compile_output").toString();
        }

        if (response.get("stderr") != null && !response.get("stderr").toString().trim().isEmpty()) {
            return response.get("stderr").toString();
        }

        if (response.get("message") != null) {
            return response.get("message").toString();
        }

        Object status = response.get("status");
        if (status instanceof Map) {
            Object description = ((Map) status).get("description");
            if (description != null) {
                return description.toString();
            }
        }

        return "Unknown error";
    }
}