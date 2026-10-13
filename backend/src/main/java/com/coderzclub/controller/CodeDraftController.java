package com.coderzclub.controller;

import com.coderzclub.config.AuthenticatedUser;
import com.coderzclub.dto.CodeDraftResponse;
import com.coderzclub.dto.CodeDraftWriteRequest;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.CodeDraftService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class CodeDraftController {
    private final CodeDraftService codeDraftService;
    private final UserRepository userRepository;

    public CodeDraftController(CodeDraftService codeDraftService, UserRepository userRepository) {
        this.codeDraftService = codeDraftService;
        this.userRepository = userRepository;
    }

    @GetMapping("/api/problems/{problemId}/drafts/{languageId}")
    public CodeDraftResponse getPractice(@PathVariable String problemId, @PathVariable Integer languageId) {
        return codeDraftService.loadPractice(currentUser(), problemId, languageId);
    }

    @PutMapping("/api/problems/{problemId}/drafts/{languageId}")
    public CodeDraftResponse putPractice(
        @PathVariable String problemId,
        @PathVariable Integer languageId,
        @RequestBody CodeDraftWriteRequest body
    ) {
        CodeDraftWriteRequest request = requireBody(body);
        return codeDraftService.savePractice(
            currentUser(),
            problemId,
            languageId,
            request.getCode(),
            request.getExpectedRevision()
        );
    }

    @GetMapping("/api/contests/{contestId}/problems/{problemId}/drafts/{languageId}")
    public CodeDraftResponse getContest(
        @PathVariable String contestId,
        @PathVariable String problemId,
        @PathVariable Integer languageId
    ) {
        return codeDraftService.loadContest(currentUser(), contestId, problemId, languageId);
    }

    @PutMapping("/api/contests/{contestId}/problems/{problemId}/drafts/{languageId}")
    public CodeDraftResponse putContest(
        @PathVariable String contestId,
        @PathVariable String problemId,
        @PathVariable Integer languageId,
        @RequestBody CodeDraftWriteRequest body
    ) {
        CodeDraftWriteRequest request = requireBody(body);
        return codeDraftService.saveContest(
            currentUser(),
            contestId,
            problemId,
            languageId,
            request.getCode(),
            request.getExpectedRevision()
        );
    }

    private static CodeDraftWriteRequest requireBody(CodeDraftWriteRequest body) {
        if (body == null) {
            throw new IllegalArgumentException("Code is required");
        }
        return body;
    }

    private User currentUser() {
        return AuthenticatedUser.current(userRepository)
            .filter(actor -> !actor.isDeleted())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }
}
