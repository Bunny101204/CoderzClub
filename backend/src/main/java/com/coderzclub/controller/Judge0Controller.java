package com.coderzclub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
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

    private static final String JUDGE0_URL = "https://judge0-ce.p.rapidapi.com/submissions?base64_encoded=false&wait=true";
    private static final String JUDGE0_HOST = "judge0-ce.p.rapidapi.com";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${judge0.api.key:${JUDGE0_API_KEY:${VITE_JUDGE0_API_KEY:}}}")
    private String judge0ApiKey;

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody Judge0ExecutionRequest request) {
        if (judge0ApiKey == null || judge0ApiKey.isBlank()) {
            return ResponseEntity.status(500).body(Map.of("error", "Judge0 API key is not configured."));
        }

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

            int statusCode = response.statusCode();
            String responseBody = response.body();
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            return ResponseEntity.status(statusCode).body(responseMap);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Judge0 execution failed", "details", e.getMessage()));
        }
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
