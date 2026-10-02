package com.coderzclub.controller;

import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.Judge0ExecutionService;
import com.coderzclub.service.ProblemRunService;
import com.coderzclub.service.RunLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/judge0")
public class Judge0Controller {

    @Autowired
    private ProblemRunService problemRunService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody Judge0ExecutionRequest request) {
        Optional<User> userOpt = currentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        if (request.getLanguageId() == null || request.getSourceCode() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "language_id and source_code are required."));
        }
        try {
            Map<String, Object> responseMap = new HashMap<>(problemRunService.runCustomStdin(
                userOpt.get().getId(), request.getSourceCode(), request.getLanguageId(), request.getStdin()));
            String errorType = Judge0ExecutionService.parseErrorType(responseMap);
            if (errorType != null) {
                responseMap.put("errorType", errorType);
            }
            return ResponseEntity.ok(responseMap);
        } catch (RunLimitExceededException limited) {
            return RateLimitResponses.from(limited);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Judge0 execution failed", "details", e.getMessage()));
        }
    }

    private Optional<User> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
            || "anonymousUser".equals(auth.getName())) {
            return Optional.empty();
        }
        return userRepository.findByUsername(auth.getName());
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
