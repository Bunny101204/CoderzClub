package com.coderzclub.service;

import com.coderzclub.config.ExecutionTimeoutPolicy;
import com.coderzclub.config.SubmissionLimitsConfig;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.config.WorkerProperties;
import com.coderzclub.config.Judge0ProviderProperties;
import com.coderzclub.model.ExecutionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
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

    private HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(ExecutionTimeoutPolicy.DEFAULT_CONNECT_TIMEOUT_SECONDS))
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
    private ExecutionCompatibilityService compatibilityService;

    @Autowired
    public Judge0ExecutionService(Judge0ProviderProperties providerProperties) {
        this.providerProperties = providerProperties;
    }

    private Semaphore globalPermits = new Semaphore(8);
    private final Map<Integer, Semaphore> languagePermits = new ConcurrentHashMap<>();

    @PostConstruct
    void initializeConcurrency() {
        globalPermits = new Semaphore(Math.max(1, workerProperties.getMaxGlobalJudge0Concurrency()));
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(Math.max(1, providerProperties.getConnectTimeoutSeconds())))
            .build();
    }


    /**
     * Execute all test cases for a submission
     */
    public ExecutionOutcome executeTestCases(String code, Integer languageId,
                                                          List<SubmissionJob.TestCase> publicTestCases,
                                                          List<SubmissionJob.TestCase> hiddenTestCases,
                                                          ExecutionMode executionMode,
                                                          String testcaseVersion) {
        ExecutionMode configured = compatibilityService.configuredMode(executionMode);
        compatibilityService.validate(executionMode, languageId, code, publicTestCases, hiddenTestCases, testcaseVersion);
        ExecutionOutcome outcome = new ExecutionOutcome();
        outcome.setConfiguredMode(configured);
        outcome.setExecutionModeUsed(configured);
        outcome.setFallbackReason(compatibilityService.fallbackReason(executionMode));
        int logical = size(publicTestCases) + size(hiddenTestCases);
        outcome.setLogicalTestcases(logical);

        if (configured == ExecutionMode.FUNCTION_HARNESS_BATCH) {
            List<SubmissionJob.TestResult> results = executeHarnessBatch(code, languageId, publicTestCases, hiddenTestCases);
            outcome.setResults(results);
            outcome.setProviderExecutions(1);
            logStrategy(outcome);
            return outcome;
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

        outcome.setResults(results);
        outcome.setProviderExecutions(results.size());
        logStrategy(outcome);
        return outcome;
    }

    public Map<String, Object> executeCustomStdin(String code, Integer languageId, String stdin) {
        return executeProviderGuarded(code, languageId, stdin, 1);
    }

    private List<SubmissionJob.TestResult> executeHarnessBatch(String code, Integer languageId,
                                                               List<SubmissionJob.TestCase> publicTestCases,
                                                               List<SubmissionJob.TestCase> hiddenTestCases) {
        List<SubmissionJob.TestCase> all = new ArrayList<>();
        if (publicTestCases != null) all.addAll(publicTestCases);
        if (hiddenTestCases != null) all.addAll(hiddenTestCases);
        String wrapped = harnessService.wrap(languageId, code);
        String stdin = harnessService.buildInput(all);
        Map<String, Object> response = executeProviderGuarded(wrapped, languageId, stdin, all.size());
        List<SubmissionJob.TestResult> mapped = harnessService.mapResults(response, all);
        applySharedResources(mapped, response);
        return mapped;
    }

    private void applySharedResources(List<SubmissionJob.TestResult> results, Map<String, Object> response) {
        Long runtime = null;
        Long memory = null;
        if (response.get("time") != null) {
            try {
                runtime = Math.round(Double.parseDouble(response.get("time").toString()) * 1000);
            } catch (NumberFormatException ignored) {
            }
        }
        if (response.get("memory") != null) {
            try {
                memory = Long.parseLong(response.get("memory").toString());
            } catch (NumberFormatException ignored) {
            }
        }
        for (SubmissionJob.TestResult result : results) {
            if (result.getRuntime() == null) result.setRuntime(runtime);
            if (result.getMemory() == null) result.setMemory(memory);
        }
    }

    Map<String, Object> executeProviderGuarded(String code, Integer languageId, String stdin, int testcaseCount) {
        return withProviderPermits(languageId, () -> executeProvider(code, languageId, stdin, testcaseCount),
            Map.of("status", Map.of("id", 13, "description", "Execution was cancelled")));
    }

    private void logStrategy(ExecutionOutcome outcome) {
        logger.info("execution_strategy configured={} used={} logicalTestcases={} providerExecutions={} fallbackReason={}",
            outcome.getConfiguredMode(), outcome.getExecutionModeUsed(), outcome.getLogicalTestcases(),
            outcome.getProviderExecutions(), outcome.getFallbackReason());
        operationalMetrics.executionStrategy(
            String.valueOf(outcome.getConfiguredMode()),
            String.valueOf(outcome.getExecutionModeUsed()),
            outcome.getFallbackReason() == null ? "none" : outcome.getFallbackReason());
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    int globalPermitsAvailable() {
        return globalPermits.availablePermits();
    }

    void configureForTest(WorkerProperties workers, FunctionHarnessService harness,
                          ExecutionCompatibilityService compatibility, OperationalMetrics metrics,
                          SubmissionLimitsConfig limits) {
        this.workerProperties = workers;
        this.harnessService = harness;
        this.compatibilityService = compatibility;
        this.operationalMetrics = metrics;
        this.limitsConfig = limits;
        initializeConcurrency();
    }

    /**
     * Execute a single test case with output truncation and size limits
     */
    private SubmissionJob.TestResult executeSingleTest(String code, Integer languageId, SubmissionJob.TestCase testCase) {
        SubmissionJob.TestResult cancelled = new SubmissionJob.TestResult();
        cancelled.setPassed(false);
        cancelled.setErrorType("Execution Cancelled");
        cancelled.setErrorMessage("Execution was cancelled");
        return withProviderPermits(languageId, () -> executeSingleTestBounded(code, languageId, testCase), cancelled);
    }

    private <T> T withProviderPermits(Integer languageId, ProviderCall<T> call, T cancelledValue) {
        io.micrometer.core.instrument.Timer.Sample timer = operationalMetrics.judge0Timer();
        Semaphore global = globalPermits;
        Semaphore language = languagePermits.computeIfAbsent(languageId == null ? 0 : languageId,
            ignored -> new Semaphore(Math.max(1, workerProperties.getMaxPerLanguageJudge0Concurrency())));
        boolean globalAcquired = false;
        boolean languageAcquired = false;
        boolean inflight = false;
        try {
            io.micrometer.core.instrument.Timer.Sample waitSample = operationalMetrics.judge0SemaphoreWait();
            global.acquire();
            globalAcquired = true;
            language.acquire();
            languageAcquired = true;
            operationalMetrics.stopJudge0SemaphoreWait(waitSample);
            operationalMetrics.providerInflightIncrement();
            inflight = true;
            return call.run();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return cancelledValue;
        } finally {
            operationalMetrics.stopJudge0Timer(timer);
            if (inflight) operationalMetrics.providerInflightDecrement();
            if (languageAcquired) language.release();
            if (globalAcquired) global.release();
        }
    }

    @FunctionalInterface
    private interface ProviderCall<T> {
        T run() throws InterruptedException;
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

            String errorType = parseErrorType(responseMap);
            if (ExecutionVerdictMapper.isInfrastructureFailure(errorType)) {
                result.setPassed(false);
                result.setErrorType(ExecutionVerdictMapper.INTERNAL_ERROR);
                result.setErrorMessage(parseErrorMessage(responseMap));
                logger.warn("judge0_execution_error languageId={} errorType={} runtimeMs={} memoryBytes={} actualOutputSummary={}",
                    languageId, ExecutionVerdictMapper.INTERNAL_ERROR, runtime, memory, actualOutputSummary);
            } else if (outputTruncated) {
                result.setErrorType("OUTPUT_LIMIT_EXCEEDED");
                result.setPassed(false);
                logger.warn("output_limit_exceeded_on_testcase languageId={} runtimeMs={} memoryBytes={} outputLength={}",
                    languageId, runtime, memory, actualOutput.length());
            } else if (errorType != null) {
                result.setPassed(false);
                result.setErrorType(errorType);
                result.setErrorMessage(parseErrorMessage(responseMap));
                logger.warn("judge0_execution_error languageId={} errorType={} runtimeMs={} memoryBytes={} actualOutputSummary={}",
                    languageId, errorType, runtime, memory, actualOutputSummary);
            } else {
                String expected = testCase.getExpectedOutput() != null ? testCase.getExpectedOutput().trim() : "";
                boolean passed = outputsMatch(actualOutput, expected, hasOutput);
                result.setPassed(passed);
                logger.info("judge0_execution_result languageId={} passed={} runtimeMs={} memoryBytes={} expectedSummary={} actualOutputSummary={}",
                    languageId, passed, runtime, memory,
                    expected.length() > 200 ? expected.substring(0, 200) + "..." : expected,
                    actualOutputSummary);
            }

        } catch (Exception e) {
            logger.error("Failed to execute test case", e);
            result.setPassed(false);
            result.setErrorType(ExecutionVerdictMapper.INTERNAL_ERROR);
            result.setErrorMessage("Failed to execute code: " + e.getMessage());
        }

        return result;
    }

    protected Map<String, Object> executeProvider(String code, Integer languageId, String stdin, int testcaseCount) {
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
            HttpRequest posted = request.POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = sendWithSafeConnectRetry(posted);
            int status = response.statusCode();
            Judge0RetryClassifier.Classification httpClass = Judge0RetryClassifier.classifyHttpStatus(status);
            if (status == 429) {
                operationalMetrics.judge0RateLimit(status);
                operationalMetrics.provider429();
            }
            if (status >= 500) {
                operationalMetrics.judge0HttpError(status);
                operationalMetrics.provider5xx();
            }
            if (httpClass != null && httpClass.decision() == Judge0RetryClassifier.Decision.DO_NOT_RETRY) {
                recordAmbiguousIfNeeded(httpClass);
                logger.warn("judge0_provider_http_failure status={} class={} reason={} posts=1 retry=false",
                    status, httpClass.kind(), httpClass.reason());
            }
            if (status == 401 || status == 403) {
                return Map.of("status", Map.of("id", 13, "description", "Judge0 authentication failed"));
            }
            if (status >= 400) {
                return parseErrorBody(response.body(), status);
            }
            return parseTerminalBody(response.body());
        } catch (Exception e) {
            Judge0RetryClassifier.Classification classification = Judge0RetryClassifier.classifyException(e);
            recordTransportFailure(classification);
            logger.warn("judge0_provider_transport_failure class={} reason={} retry=false",
                classification.kind(), classification.reason());
            return Map.of("status", Map.of("id", 13, "description", "Judge0 provider error: " + e.getMessage()));
        }
    }

    private HttpResponse<String> sendWithSafeConnectRetry(HttpRequest request) throws IOException, InterruptedException {
        int connectAttempts = 0;
        int maxConnectRetries = Math.max(0, providerProperties.getMaxConnectRetries());
        while (true) {
            try {
                return sendProvider(request);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            } catch (Exception error) {
                Judge0RetryClassifier.Classification classification = Judge0RetryClassifier.classifyException(error);
                boolean canRetryConnect = classification.decision() == Judge0RetryClassifier.Decision.SAFE_PRE_SEND
                    && connectAttempts < maxConnectRetries;
                if (!canRetryConnect) {
                    if (error instanceof IOException io) throw io;
                    if (error instanceof RuntimeException runtime) throw runtime;
                    throw new IOException(error);
                }
                connectAttempts++;
                logger.warn("judge0_connect_retry attempt={} class={} reason={}",
                    connectAttempts, classification.kind(), classification.reason());
                long delay = Math.min(8000L, 250L * (1L << Math.min(5, connectAttempts)))
                    + java.util.concurrent.ThreadLocalRandom.current().nextLong(50L, 200L);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
        }
    }

    protected HttpResponse<String> sendProvider(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> parseTerminalBody(String body) {
        try {
            @SuppressWarnings("unchecked") Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
            return parsed;
        } catch (Exception malformed) {
            Judge0RetryClassifier.Classification classification = new Judge0RetryClassifier.Classification(
                Judge0RetryClassifier.Kind.MALFORMED_RESPONSE, Judge0RetryClassifier.Decision.DO_NOT_RETRY,
                "malformed_terminal_response");
            recordAmbiguousIfNeeded(classification);
            logger.warn("judge0_malformed_terminal_response retry=false");
            return Map.of("status", Map.of("id", 13, "description", "Judge0 returned a malformed terminal response"));
        }
    }

    private Map<String, Object> parseErrorBody(String body, int status) {
        String detail = "HTTP " + status;
        try {
            @SuppressWarnings("unchecked") Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
            detail = String.valueOf(parsed.getOrDefault("error", parsed.getOrDefault("message", detail)));
        } catch (Exception ignored) {
            Judge0RetryClassifier.Classification classification = new Judge0RetryClassifier.Classification(
                Judge0RetryClassifier.Kind.MALFORMED_RESPONSE, Judge0RetryClassifier.Decision.DO_NOT_RETRY,
                "malformed_error_body");
            recordAmbiguousIfNeeded(classification);
        }
        return Map.of("status", Map.of("id", 13,
            "description", "Judge0 rejected the execution request: " + detail));
    }

    private void recordTransportFailure(Judge0RetryClassifier.Classification classification) {
        if (classification.kind() == Judge0RetryClassifier.Kind.READ_TIMEOUT) {
            operationalMetrics.providerTimeout();
        }
        recordAmbiguousIfNeeded(classification);
    }

    private void recordAmbiguousIfNeeded(Judge0RetryClassifier.Classification classification) {
        if (classification != null && classification.decision() == Judge0RetryClassifier.Decision.DO_NOT_RETRY
            && classification.kind() != Judge0RetryClassifier.Kind.AUTH_FAILURE
            && classification.kind() != Judge0RetryClassifier.Kind.CANCELLED) {
            operationalMetrics.ambiguousExecutionFailure(classification.reason());
        }
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
        payload.put("cpu_time_limit", ExecutionTimeoutPolicy.cpuTimeLimitSeconds(
            limits.getMaxExecutionTimeSeconds(), testcaseCount, provider.getMaxCpuTimeLimitSeconds()));
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
    public static String parseErrorType(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return ExecutionVerdictMapper.INTERNAL_ERROR;
        }

        Object status = response.get("status");
        if (status instanceof Map<?, ?> statusMap) {
            Integer id = statusId(statusMap.get("id"));
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
                    case 13: return ExecutionVerdictMapper.INTERNAL_ERROR;
                }
            } else if (statusMap.containsKey("id")) {
                return ExecutionVerdictMapper.INTERNAL_ERROR;
            }
        } else if (status != null) {
            return ExecutionVerdictMapper.INTERNAL_ERROR;
        }

        if (response.get("compile_output") != null && !response.get("compile_output").toString().trim().isEmpty()) {
            return "Compilation Error";
        }

        return null;
    }

    private static Integer statusId(Object rawId) {
        if (rawId instanceof Number number) {
            return number.intValue();
        }
        if (rawId == null) {
            return null;
        }
        try {
            return Integer.parseInt(rawId.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
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