package com.coderzclub.controller;

import com.coderzclub.config.AuthenticatedUser;
import com.coderzclub.dto.ContestWriteRequest;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ContestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/contests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminContestController {
    private final ContestService contestService;
    private final UserRepository userRepository;

    public AdminContestController(ContestService contestService, UserRepository userRepository) {
        this.contestService = contestService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ContestWriteRequest body) {
        return ResponseEntity.ok(contestService.create(body, requireCurrentUserId()));
    }

    @GetMapping
    public ResponseEntity<?> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(contestService.adminList(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        return ResponseEntity.ok(contestService.adminGet(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody ContestWriteRequest body) {
        return ResponseEntity.ok(contestService.update(id, body));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<?> publish(@PathVariable String id) {
        return ResponseEntity.ok(contestService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<?> unpublish(@PathVariable String id) {
        return ResponseEntity.ok(contestService.unpublish(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        contestService.delete(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    private String requireCurrentUserId() {
        User user = AuthenticatedUser.current(userRepository)
            .filter(candidate -> !candidate.isDeleted())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        return user.getId();
    }
}
