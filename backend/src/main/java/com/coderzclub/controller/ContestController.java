package com.coderzclub.controller;

import com.coderzclub.config.AuthenticatedUser;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ContestLeaderboardService;
import com.coderzclub.service.ContestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/contests")
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class ContestController {
    private final ContestService contestService;
    private final ContestLeaderboardService leaderboardService;
    private final UserRepository userRepository;

    public ContestController(
        ContestService contestService,
        ContestLeaderboardService leaderboardService,
        UserRepository userRepository
    ) {
        this.contestService = contestService;
        this.leaderboardService = leaderboardService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(contestService.listVisible(requireCurrentUser(), page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        return ResponseEntity.ok(contestService.getVisible(requireCurrentUser(), id));
    }

    @GetMapping("/{contestId}/problems/{problemId}")
    public ResponseEntity<?> getProblem(@PathVariable String contestId, @PathVariable String problemId) {
        return ResponseEntity.ok(contestService.getVisibleProblem(requireCurrentUser(), contestId, problemId));
    }

    @GetMapping("/{contestId}/leaderboard")
    public ResponseEntity<?> leaderboard(
        @PathVariable String contestId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(leaderboardService.page(requireCurrentUser(), contestId, page, size));
    }

    private User requireCurrentUser() {
        return AuthenticatedUser.current(userRepository)
            .filter(user -> !user.isDeleted())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }
}
