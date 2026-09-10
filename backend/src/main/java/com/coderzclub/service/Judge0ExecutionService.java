package com.coderzclub.service;

import com.coderzclub.config.SubmissionLimitsConfig;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.config.WorkerProperties;
import com.coderzclub.config.Judge0ProviderProperties;
import com.coderzclub.model.ExecutionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import jakarta.annotation.PostConstruct;

@Service
public class Judge0ExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(Judge0ExecutionService.class);

    private final Judge0ProviderProperties providerProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired

    private SubmissionLimitsConfig limitsConfig;

    @Autowired
    private WorkerProperties workerProperties;

    @Autowired
    private SubmissionValidator submissionValidator;

    @Autowired
    private SubmissionValidationService validationService;

    @Autowired
    private OperationalMetrics operationalMetrics;

    @Autowired
    private FunctionHarnessService harnessService;

    @Autowired
    public Judge0ExecutionService(Judge0ProviderProperties providerProperties) {
        this.providerProperties = providerProperties;
    }

    private Semaphore globalPermits = new Semaphore(8);
    private final Map<Integer, Semaphore> languagePermits = new ConcurrentHashMap<>();

    @PostConstruct
    void initializeConcurrency() {
        globalPermits = new Semaphore(Math.max(1, workerProperties.getMaxGlobalJudge0Concurrency()));
    }


    /**
     * Execute all test cases for a submission
     */
    public List<SubmissionJob.TestResult> executeTestCases(String code, Integer languageId,
                                                          List<SubmissionJob.TestCase> publicTestCases,
                                                          List<SubmissionJob.TestCase> hiddenTestCases,
                                                          ExecutionMode executionMode,
                                                          String testcaseVersion) {
        if (executionMode == ExecutionMode.FUNCTION_HARNESS_BATCH
            && harnessService.supports(languageId, code, publicTestCases, hiddenTestCases, testcaseVersion)) {
            return executeHarnessBatch(code, languageId, publicTestCases, hiddenTestCases);
        }
        List<SubmissionJob.TestResult> results = new ArrayList<>();

        // Execute public test cases
        if (publicTestCases != null && !publicTestCases.isEmpty()) {
            ExecutorService executor = Executors.newFixedThreadPool(
                Math.max(1, Math.min(workerProperties.getMaxTestcaseConcurrency(), publicTestCases.size())));
            try {
                List<Future<SubmissionJob.TestResult>> futures = new ArrayList<>();
                for (SubmissionJob.TestCase testCase : publicTestCases) {
                    futures.add(executor.submit(() -> executeSingleTest(code, languageId, testCase)));
                }
                for (Future<SubmissionJob.TestResult> future : futures) {
                    results.add(future.get());
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception executionFailure) {
                logger.warn("Public testcase execution interrupted", executionFailure);
            } finally {
                executor.shutdownNow();
            }
        }

        // Execute hidden test cases
        if (hiddenTestCases != null) {
            for (SubmissionJob.TestCase testCase : hiddenTestCases) {
                SubmissionJob.TestResult result = executeSingleTest(code, languageId, testCase);
                results.add(result);
                if (workerProperties.isStopHiddenOnFailure() && !result.isPassed()) {
                    break;
                }
            }
        }

        return results;
    }

    private List<SubmissionJob.TestResult> executeHarnessBatch(String code, Integer languageId,
                                                               List<SubmissionJob.TestCase> publicTestCases,
                                                               List<SubmissionJob.TestCase> hiddenTestCases) {
        List<SubmissionJob.TestCase> all = new ArrayList<>();
        if (publicTestCases != null) all.addAll(publicTestCases);
        if (hiddenTestCases != null) all.addAll(hiddenTestCases);
        String wrapped = harnessService.wrap(languageId, code);
        String stdin = harnessService.buildInput(all);
        Map<String, Object> response = executeProvider(wrapped, languageId, stdin, all.size());
        return harnessService.mapResults(response, all);
    }

    /**
     * Execute a single test case with output truncation and size limits
     */
    private SubmissionJob.TestResult executeSingleTest(String code, Integer languageId, SubmissionJob.TestCase testCase) {
        io.micrometer.core.instrument.Timer.Sample timer = operationalMetrics.judge0Timer();
        Semaphore global = globalPermits;
        Semaphore language = languagePermits.computeIfAbsent(languageId == null ? 0 : languageId,
            ignored -> new Semaphore(Math.max(1, workerProperties.getMaxPerLanguageJudge0Concurrency())));
        boolean globalAcquired = false;
        boolean languageAcquired = false;
        try {
            global.acquire();
            globalAcquired = true;
            language.acquire();
            languageAcquired = true;
            return executeSingleTestBounded(code, languageId, testCase);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            SubmissionJob.TestResult result = new SubmissionJob.TestResult();
            result.setPassed(false);
            result.setErrorType("Execution Cancelled");
            result.setErrorMessage("Execution was cancelled");
            return result;
        } finally {
            operationalMetrics.stopJudge0Timer(timer);
            if (languageAcquired) language.release();
            if (globalAcquired) global.release();
        }
    }

    private SubmissionJob.TestResult executeSingleTestBounded(String code, Integer languageId, SubmissionJob.TestCase testCase) {
        SubmissionJob.TestResult result = new SubmissionJob.TestResult();
        result.setInput(testCase.getInput());
        result.setExpectedOutput(testCase.getExpectedOutput());

        try {
            Map<String, Object> responseMap = executeProvider(code, languageId,
                testCase.getInput(), 1);

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
                    memory = Long.parseLong(responseMap.get("memory").toString());
                } catch (Exception e) {
                    logger.warn("Failed to parse memory: {}", responseMap.get("memory"));
                }
            }

            // Extract and truncate stdout
            String stdout = responseMap.get("stdout") != null ? responseMap.get("stdout").toString().trim() : "";
            boolean stdoutTruncated = false;
            if (stdout.length() > limitsConfig.getMaxStdoutLength()) {
                stdoutTruncated = true;
                stdout = submissionValidator.truncateOutput(stdout, limitsConfig.getMaxStdoutLength());
                logger.warn("stdout_truncated", "originalLength", stdout.length(), "truncatedAt", limitsConfig.getMaxStdoutLength());
            }

            // Extract and truncate stderr
            String stderr = responseMap.get("stderr") != null ? responseMap.get("stderr").toString().trim() : "";
            boolean stderrTruncated = false;
            if (stderr.length() > limitsConfig.getMaxStderrLength()) {
                stderrTruncated = true;
                stderr = submissionValidator.truncateOutput(stderr, limitsConfig.getMaxStderrLength());
                logger.warn("stderr_truncated", "originalLength", stderr.length(), "truncatedAt", limitsConfig.getMaxStderrLength());
            }

            // Extract and truncate compile output
            String compileOutput = responseMap.get("compile_output") != null ? responseMap.get("compile_output").toString().trim() : "";
            boolean compileOutputTruncated = false;
            if (compileOutput.length() > limitsConfig.getMaxCompileOutputLength()) {
                compileOutputTruncated = true;
                compileOutput = submissionValidator.truncateOutput(compileOutput, limitsConfig.getMaxCompileOutputLength());
                logger.warn("compile_output_truncated", "originalLength", compileOutput.length(), "truncatedAt", limitsConfig.getMaxCompileOutputLength());
            }

            // Determine actual output (prioritize stderr if present, then compile_output, then stdout)
            String actualOutput = "";
            boolean hasOutput = false;
            boolean outputTruncated = false;
            if (!stderr.isEmpty()) {
                actualOutput = stderr;
                hasOutput = true;
                outputTruncated = stderrTruncated;
            } else if (!compileOutput.isEmpty()) {
                actualOutput = compileOutput;
                hasOutput = true;
                outputTruncated = compileOutputTruncated;
            } else if (!stdout.isEmpty()) {
                actualOutput = stdout;
                hasOutput = true;
                outputTruncated = stdoutTruncated;
            } else {
                actualOutput = "No Output";
            }

            result.setActualOutput(actualOutput);
            result.setRuntime(runtime);
            result.setMemory(memory);
            result.setExecutionDetails(null);

            String actualOutputSummary = actualOutput;
            if (actualOutputSummary.length() > 200) {
                actualOutputSummary = actualOutputSummary.substring(0, 200) + "...";
            }

            // Mark as OUTPUT_LIMIT_EXCEEDED if outputs were truncated
            if (outputTruncated) {
                result.setErrorType("OUTPUT_LIMIT_EXCEEDED");
                result.setPassed(false);
                logger.warn("output_limit_exceeded_on_testcase languageId={} runtimeMs={} memoryBytes={} outputLength={}",
                    languageId, runtime, memory, actualOutput.length());
            }

            // Check for errors
            String errorType = parseErrorType(responseMap);
            if (errorType != null && !outputTruncated) {
                result.setPassed(false);
                result.setErrorType(errorType);
                result.setErrorMessage(parseErrorMessage(responseMap));

                // Observability: Log execution error
                logger.warn("judge0_execution_error languageId={} errorType={} runtimeMs={} memoryBytes={} actualOutputSummary={}",
                    languageId, errorType, runtime, memory, actualOutputSummary);
            } else if (errorType == null && !outputTruncated) {
                // Check if output matches expected
                String expected = testCase.getExpectedOutput() != null ? testCase.getExpectedOutput().trim() : "";
                boolean passed = outputsMatch(actualOutput, expected, hasOutput);
                result.setPassed(passed);

                // Observability: Log execution result
                logger.info("judge0_execution_result languageId={} passed={} runtimeMs={} memoryBytes={} expectedSummary={} actualOutputSummary={}",
                    languageId, passed, runtime, memory,
                    expected.length() > 200 ? expected.substring(0, 200) + "..." : expected,
                    actualOutputSummary);
            }

        } catch (Exception e) {
            logger.error("Failed to execute test case", e);
            result.setPassed(false);
            result.setErrorType("Execution Error");
            result.setErrorMessage("Failed to execute code: " + e.getMessage());
        }

        return result;
    }

    Map<String, Object> executeProvider(String code, Integer languageId, String stdin, int testcaseCount) {
        try {
            Map<String, Object> payload = buildPayload(code, languageId, stdin, testcaseCount, limitsConfig,
                providerProperties);
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(providerUrl()))
                .timeout(Duration.ofSeconds(Math.max(1, providerProperties.getTimeoutSeconds())))
                .header("Content-Type", "application/json");
            if (providerProperties.getAuthenticationMode() == Judge0ProviderProperties.AuthenticationMode.RAPID_API) {
                if (providerProperties.getApiKey() != null && !providerProperties.getApiKey().isBlank()) {
                    request.header("X-RapidAPI-Key", providerProperties.getApiKey());
                }
                if (providerProperties.getHostHeader() != null && !providerProperties.getHostHeader().isBlank()) {
                    request.header("X-RapidAPI-Host", providerProperties.getHostHeader());
                }
            }
            HttpResponse<String> response;
            int attempt = 0;
            while (true) {
                response = httpClient.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 429 || status == 503) operationalMetrics.judge0RateLimit(status);
                if (!isTransient(status) || attempt++ >= 5) break;
                long delay = Math.min(8000L, 250L * (1L << Math.min(5, attempt)))
                    + java.util.concurrent.ThreadLocalRandom.current().nextLong(100L, 400L);
                Thread.sleep(delay);
            }
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return Map.of("status", Map.of("id", 13, "description", "Judge0 authentication failed"));
            }
            @SuppressWarnings("unchecked") Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
            if (response.statusCode() >= 400) {
                return Map.of("status", Map.of("id", 13,
                    "description", "Judge0 rejected the execution request: "
                        + String.valueOf(parsed.getOrDefault("error", parsed.getOrDefault("message", "HTTP " + response.statusCode())))));
            }
            return parsed;
        } catch (Exception e) {
            return Map.of("status", Map.of("id", 13, "description", "Judge0 provider error: " + e.getMessage()));
        }
    }

    private boolean isTransient(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private String providerUrl() {
        String base = providerProperties.getBaseUrl();
        if (base == null || base.isBlank()) throw new IllegalStateException("Judge0 provider baseUrl is missing");
        String url = base.replaceAll("([?&]wait=)[^&]*", "$1" + providerProperties.isWait());
        if (!url.contains("base64_encoded=")) url += (url.contains("?") ? "&" : "?") + "base64_encoded=false";
        if (!url.contains("wait=")) url += (url.contains("?") ? "&" : "?") + "wait=" + providerProperties.isWait();
        return url;
    }

    static Map<String, Object> buildPayload(String code, Integer languageId, String stdin, int testcaseCount,
                                            SubmissionLimitsConfig limits, Judge0ProviderProperties provider) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("language_id", languageId);
        payload.put("source_code", code);
        payload.put("cpu_time_limit", Math.min(60, Math.max(1, limits.getMaxExecutionTimeSeconds() * Math.max(1, testcaseCount))));
        payload.put("compile_time_limit", Math.min(120, Math.max(1, provider.getCompileTimeLimitSeconds())));
        if (!"KB".equalsIgnoreCase(provider.getMemoryLimitUnit())) {
            throw new IllegalArgumentException("judge0.provider.memory-limit-unit must be KB");
        }
        payload.put("memory_limit", provider.getMemoryLimitKb());
        if (stdin != null && !stdin.isBlank()) payload.put("stdin", stdin);
        return payload;
    }

    static boolean outputsMatch(String actualOutput, String expectedOutput, boolean hasOutput) {
        String expected = expectedOutput == null ? "" : expectedOutput.trim();
        if (!hasOutput && (expected.isEmpty() || "N/A".equalsIgnoreCase(expected))) {
            return true;
        }
        return actualOutput.equals(expected);
    }

    /**
     * Parse error type from Judge0 response
     */
    private String parseErrorType(Map<String, Object> response) {
        if (response == null) return null;

        Object status = response.get("status");
        if (status instanceof Map<?, ?> statusMap) {
            Integer id = (Integer) statusMap.get("id");
            if (id != null) {
                switch (id) {
                    case 6: return "Compilation Error";
                    case 14: return "Compilation Time Limit Exceeded";
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
        if (status instanceof Map<?, ?> statusMap) {
            Object description = statusMap.get("description");
            if (description != null) {
                return description.toString();
            }
        }

        return "Unknown error";
    }
}