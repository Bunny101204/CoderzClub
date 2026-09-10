package com.coderzclub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import com.coderzclub.config.Judge0ProviderProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/judge0")
public class Judge0Controller {

    @Autowired
    private Judge0ProviderProperties providerProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody Judge0ExecutionRequest request) {
        // if (judge0ApiKey == null || judge0ApiKey.isBlank()) {
        //     return ResponseEntity.status(500).body(Map.of("error", "Judge0 API key is not configured."));
        // }

        if (request.getLanguageId() == null || request.getSourceCode() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "language_id and source_code are required."));
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("language_id", request.getLanguageId());
            payload.put("source_code", request.getSourceCode());
            if (request.getStdin() != null) {
                payload.put("stdin", request.getStdin());
            }
            if (request.getExpectedOutput() != null) {
                payload.put("expected_output", request.getExpectedOutput());
            }

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(providerUrl()))
                    .timeout(Duration.ofSeconds(Math.max(1, providerProperties.getTimeoutSeconds())))
                    .header("Content-Type", "application/json");
            if (providerProperties.getAuthenticationMode() == Judge0ProviderProperties.AuthenticationMode.RAPID_API) {
                if (providerProperties.getApiKey() != null && !providerProperties.getApiKey().isBlank()) {
                    requestBuilder.header("X-RapidAPI-Key", providerProperties.getApiKey());
                }
                if (providerProperties.getHostHeader() != null && !providerProperties.getHostHeader().isBlank()) {
                    requestBuilder.header("X-RapidAPI-Host", providerProperties.getHostHeader());
                }
            }

            int attempt = 0;
            HttpResponse<String> response;
            while (true) {
                response = httpClient.send(requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (!isTransientStatus(status)) {
                    break;
                }
                attempt++;
                if (attempt > 5) {
                    break;
                }
                long waitMs = Math.min(8000L, 250L * (1L << Math.min(5, attempt)))
                    + java.util.concurrent.ThreadLocalRandom.current().nextLong(100L, 400L);
                String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
                if (retryAfter != null) {
                    try {
                        waitMs = Math.max(waitMs, Long.parseLong(retryAfter) * 1000L);
                    } catch (NumberFormatException ignored) {
                    }
                }
                Thread.sleep(waitMs);
            }

            int statusCode = response.statusCode();
            String responseBody = response.body();
            Map<String, Object> responseMap;
            try {
                responseMap = objectMapper.readValue(responseBody, Map.class);
            } catch (Exception invalidJson) {
                responseMap = Map.of("error", responseBody == null || responseBody.isBlank()
                    ? "Judge0 returned an empty response"
                    : responseBody.substring(0, Math.min(500, responseBody.length())));
            }
            if (statusCode == 429) {
                return ResponseEntity.status(429).body(Map.of(
                    "error", "Judge0 rate limit exceeded. Please wait a few seconds and try again.",
                    "details", responseMap
                ));
            }
            if (statusCode >= 500) {
                return ResponseEntity.status(503).body(Map.of(
                    "error", "Judge0 is temporarily unavailable. Please try again in a few seconds.",
                    "details", responseMap
                ));
            }
            return ResponseEntity.status(statusCode).body(responseMap);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Judge0 execution failed", "details", e.getMessage()));
        }
    }

    private boolean isTransientStatus(int status) {
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

    public static class Judge0ExecutionRequest {
        @com.fasterxml.jackson.annotation.JsonProperty("language_id")
        private Integer languageId;

        @com.fasterxml.jackson.annotation.JsonProperty("source_code")
        private String sourceCode;

        private String stdin;

        @com.fasterxml.jackson.annotation.JsonProperty("expected_output")
        private String expectedOutput;

        public Integer getLanguageId() {
            return languageId;
        }

        public void setLanguageId(Integer languageId) {
            this.languageId = languageId;
        }

        public String getSourceCode() {
            return sourceCode;
        }

        public void setSourceCode(String sourceCode) {
            this.sourceCode = sourceCode;
        }

        public String getStdin() {
            return stdin;
        }

        public void setStdin(String stdin) {
            this.stdin = stdin;
        }

        public String getExpectedOutput() {
            return expectedOutput;
        }

        public void setExpectedOutput(String expectedOutput) {
            this.expectedOutput = expectedOutput;
        }
    }
}
