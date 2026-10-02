package com.coderzclub.controller;

import com.coderzclub.dto.BatchIdsRequest;
import com.coderzclub.dto.BatchWriteRequest;
import com.coderzclub.model.Batch;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.BatchReportService;
import com.coderzclub.service.BatchService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/batches")
@PreAuthorize("hasRole('ADMIN')")
public class BatchController {
    private final BatchService batchService;
    private final BatchReportService batchReportService;
    private final UserRepository userRepository;

    public BatchController(BatchService batchService, BatchReportService batchReportService, UserRepository userRepository) {
        this.batchService = batchService;
        this.batchReportService = batchReportService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody BatchWriteRequest body, Authentication authentication) {
        String createdBy = currentUserId(authentication);
        Batch batch = batchService.create(
            body == null ? null : body.getName(),
            body == null ? null : body.getDescription(),
            createdBy);
        return ResponseEntity.ok(batchService.detail(batch.getId()));
    }

    @GetMapping
    public ResponseEntity<?> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(batchService.list(page, size, search, active));
    }

    @GetMapping("/users")
    public ResponseEntity<?> searchUsers(
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(batchService.searchUsers(search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        return ResponseEntity.ok(batchService.detail(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody BatchWriteRequest body) {
        Boolean active = body == null ? null : body.getActive();
        return ResponseEntity.ok(batchService.detail(
            batchService.update(
                id,
                body == null ? null : body.getName(),
                body == null ? null : body.getDescription(),
                active).getId()));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<?> archive(@PathVariable String id) {
        return ResponseEntity.ok(batchService.detail(batchService.deactivate(id).getId()));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<?> addMembers(@PathVariable String id, @RequestBody BatchIdsRequest body) {
        List<String> userIds = body == null ? List.of() : body.getUserIds();
        return ResponseEntity.ok(Map.of("members", batchService.addMembers(id, userIds)));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<?> removeMember(@PathVariable String id, @PathVariable String userId) {
        batchService.removeMember(id, userId);
        return ResponseEntity.ok(Map.of("removed", true));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<?> members(
        @PathVariable String id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(batchService.listMembers(id, page, size, search));
    }

    @PostMapping("/{id}/assignments")
    public ResponseEntity<?> assign(@PathVariable String id, @RequestBody BatchIdsRequest body) {
        List<String> problemIds = body == null ? List.of() : body.getProblemIds();
        return ResponseEntity.ok(Map.of("assignments", batchService.addAssignments(id, problemIds)));
    }

    @DeleteMapping("/{id}/assignments/{problemId}")
    public ResponseEntity<?> unassign(@PathVariable String id, @PathVariable String problemId) {
        batchService.removeAssignment(id, problemId);
        return ResponseEntity.ok(Map.of("removed", true));
    }

    @GetMapping("/{id}/assignments")
    public ResponseEntity<?> assignments(
        @PathVariable String id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(batchService.listAssignments(id, page, size));
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<?> report(
        @PathVariable String id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String student
    ) {
        return ResponseEntity.ok(batchReportService.report(id, page, size, student));
    }

    @GetMapping(value = "/{id}/report.csv", produces = "text/csv")
    public ResponseEntity<byte[]> reportCsv(
        @PathVariable String id,
        @RequestParam(required = false) String student
    ) {
        String csv = batchReportService.csv(id, student);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"batch-report.csv\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private String currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).map(User::getId).orElse(authentication.getName());
    }
}
