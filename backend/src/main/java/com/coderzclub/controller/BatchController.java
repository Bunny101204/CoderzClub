package com.coderzclub.controller;

import com.coderzclub.dto.BatchCoordinatorRequest;
import com.coderzclub.dto.BatchIdsRequest;
import com.coderzclub.dto.BatchWriteRequest;
import com.coderzclub.model.Batch;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.BatchAccessService;
import com.coderzclub.service.BatchCoordinatorService;
import com.coderzclub.service.BatchReportService;
import com.coderzclub.service.BatchService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Batch reads and membership changes are available to a global admin or a coordinator of that batch.
 * Creating batches, editing batch metadata, archiving, and granting coordinators stay admin-only.
 * There is no class-level admin rule: each method checks access itself.
 */
@RestController
@RequestMapping("/api/admin/batches")
public class BatchController {
    private final BatchService batchService;
    private final BatchReportService batchReportService;
    private final BatchCoordinatorService batchCoordinatorService;
    private final UserRepository userRepository;
    private final BatchAccessService batchAccessService;

    public BatchController(
        BatchService batchService,
        BatchReportService batchReportService,
        BatchCoordinatorService batchCoordinatorService,
        UserRepository userRepository,
        BatchAccessService batchAccessService
    ) {
        this.batchService = batchService;
        this.batchReportService = batchReportService;
        this.batchCoordinatorService = batchCoordinatorService;
        this.userRepository = userRepository;
        this.batchAccessService = batchAccessService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody BatchWriteRequest body, Authentication authentication) {
        batchAccessService.ensureAdmin(authentication);
        Batch batch = batchService.create(
            body == null ? null : body.getName(),
            body == null ? null : body.getDescription(),
            requireCurrentUserId(authentication));
        return ResponseEntity.ok(withViewer(batchService.detail(batch.getId()), authentication));
    }

    @GetMapping
    public ResponseEntity<?> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Boolean active,
        Authentication authentication
    ) {
        batchAccessService.ensureAdmin(authentication);
        return ResponseEntity.ok(batchService.list(page, size, search, active));
    }

    @GetMapping("/users")
    public ResponseEntity<?> searchUsers(
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String batchId,
        Authentication authentication
    ) {
        batchAccessService.ensureCanSearchUsers(authentication);
        if (!batchAccessService.isAdmin(authentication) && (search == null || search.trim().length() < 2)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter at least 2 characters");
        }
        if (batchId != null && !batchId.isBlank()) {
            batchAccessService.ensureCanReadBatch(authentication, batchId.trim());
        }
        return ResponseEntity.ok(batchService.searchUsers(search, page, size, batchId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id, Authentication authentication) {
        batchAccessService.ensureCanReadBatch(authentication, id);
        return ResponseEntity.ok(withViewer(batchService.detail(id), authentication));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody BatchWriteRequest body, Authentication authentication) {
        batchAccessService.ensureAdmin(authentication);
        Boolean active = body == null ? null : body.getActive();
        return ResponseEntity.ok(withViewer(batchService.detail(
            batchService.update(
                id,
                body == null ? null : body.getName(),
                body == null ? null : body.getDescription(),
                active).getId()), authentication));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<?> archive(@PathVariable String id, Authentication authentication) {
        batchAccessService.ensureAdmin(authentication);
        return ResponseEntity.ok(withViewer(batchService.detail(batchService.deactivate(id).getId()), authentication));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<?> reactivate(@PathVariable String id, Authentication authentication) {
        batchAccessService.ensureAdmin(authentication);
        return ResponseEntity.ok(withViewer(batchService.detail(batchService.update(id, null, null, true).getId()), authentication));
    }

    @GetMapping("/{id}/coordinators")
    public ResponseEntity<?> coordinators(@PathVariable String id, Authentication authentication) {
        batchAccessService.ensureAdmin(authentication);
        return ResponseEntity.ok(Map.of("coordinators", batchCoordinatorService.list(id)));
    }

    @PostMapping("/{id}/coordinators")
    public ResponseEntity<?> grantCoordinator(
        @PathVariable String id, @RequestBody BatchCoordinatorRequest body, Authentication authentication
    ) {
        batchAccessService.ensureAdmin(authentication);
        return ResponseEntity.ok(batchCoordinatorService.grant(
            id,
            body == null ? null : body.getUserId(),
            body == null ? null : body.getInstitution(),
            requireCurrentUserId(authentication)));
    }

    @DeleteMapping("/{id}/coordinators/{userId}")
    public ResponseEntity<?> revokeCoordinator(
        @PathVariable String id, @PathVariable String userId, Authentication authentication
    ) {
        batchAccessService.ensureAdmin(authentication);
        batchCoordinatorService.revoke(id, userId);
        return ResponseEntity.ok(Map.of("removed", true));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<?> addMembers(@PathVariable String id, @RequestBody BatchIdsRequest body, Authentication authentication) {
        batchAccessService.ensureCanReadBatch(authentication, id);
        List<String> userIds = body == null ? List.of() : body.getUserIds();
        return ResponseEntity.ok(batchService.addMembersResult(id, userIds));
    }

    @PostMapping("/{id}/members/import")
    public ResponseEntity<?> importMembers(@PathVariable String id, @RequestBody BatchIdsRequest body, Authentication authentication) {
        batchAccessService.ensureCanReadBatch(authentication, id);
        List<String> usernames = body == null ? List.of() : body.getUsernames();
        return ResponseEntity.ok(batchService.importIdentifiers(id, usernames));
    }

    @PostMapping("/{id}/members/remove")
    public ResponseEntity<?> removeMembers(@PathVariable String id, @RequestBody BatchIdsRequest body, Authentication authentication) {
        batchAccessService.ensureCanReadBatch(authentication, id);
        List<String> userIds = body == null ? List.of() : body.getUserIds();
        return ResponseEntity.ok(Map.of("removed", batchService.removeMembers(id, userIds)));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<?> removeMember(@PathVariable String id, @PathVariable String userId, Authentication authentication) {
        batchAccessService.ensureCanReadBatch(authentication, id);
        batchService.removeMember(id, userId);
        return ResponseEntity.ok(Map.of("removed", true));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<?> members(
        @PathVariable String id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search,
        Authentication authentication
    ) {
        batchAccessService.ensureCanReadBatch(authentication, id);
        return ResponseEntity.ok(batchService.listMembers(id, page, size, search));
    }

    @PostMapping("/{id}/assignments")
    public ResponseEntity<?> assign(@PathVariable String id, @RequestBody BatchIdsRequest body, Authentication authentication) {
        batchAccessService.ensureCanReadBatch(authentication, id);
        List<String> problemIds = body == null ? List.of() : body.getProblemIds();
        return ResponseEntity.ok(Map.of("assignments", batchService.addAssignments(id, problemIds)));
    }

    @PostMapping("/{id}/assignments/remove")
    public ResponseEntity<?> unassignMany(@PathVariable String id, @RequestBody BatchIdsRequest body, Authentication authentication) {
        batchAccessService.ensureCanReadBatch(authentication, id);
        List<String> problemIds = body == null ? List.of() : body.getProblemIds();
        return ResponseEntity.ok(Map.of("removed", batchService.removeAssignments(id, problemIds)));
    }

    @DeleteMapping("/{id}/assignments/{problemId}")
    public ResponseEntity<?> unassign(@PathVariable String id, @PathVariable String problemId, Authentication authentication) {
        batchAccessService.ensureCanReadBatch(authentication, id);
        batchService.removeAssignment(id, problemId);
        return ResponseEntity.ok(Map.of("removed", true));
    }

    @GetMapping("/{id}/assignments")
    public ResponseEntity<?> assignments(
        @PathVariable String id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        Authentication authentication
    ) {
        batchAccessService.ensureCanReadBatch(authentication, id);
        return ResponseEntity.ok(batchService.listAssignments(id, page, size));
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<?> report(
        @PathVariable String id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String student,
        @RequestParam(required = false) String problemId,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        Authentication authentication
    ) {
        batchAccessService.ensureCanReadBatch(authentication, id);
        Date activityFrom = parseInstant(from);
        Date activityTo = parseInstant(to);
        if (problemId == null && activityFrom == null && activityTo == null) {
            return ResponseEntity.ok(batchReportService.report(id, page, size, student));
        }
        return ResponseEntity.ok(batchReportService.report(id, page, size, student, problemId, activityFrom, activityTo));
    }

    @GetMapping(value = "/{id}/report.csv", produces = "text/csv")
    public ResponseEntity<byte[]> reportCsv(
        @PathVariable String id,
        @RequestParam(required = false) String student,
        @RequestParam(required = false) String problemId,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        Authentication authentication
    ) {
        batchAccessService.ensureCanReadBatch(authentication, id);
        Date activityFrom = parseInstant(from);
        Date activityTo = parseInstant(to);
        String csv = problemId == null && activityFrom == null && activityTo == null
            ? batchReportService.csv(id, student)
            : batchReportService.csv(id, student, problemId, activityFrom, activityTo);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"batch-report.csv\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> withViewer(Map<String, Object> detail, Authentication authentication) {
        Map<String, Object> response = new LinkedHashMap<>(detail);
        response.put("viewerRole", batchAccessService.isAdmin(authentication) ? "ADMIN" : "COORDINATOR");
        return response;
    }

    private static Date parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Date.from(Instant.parse(raw.trim()));
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dates must be ISO-8601 instants");
        }
    }

    /**
     * New batches store Mongo user id in createdBy. Historical rows may still contain a username
     * from an older fallback and are not migrated here.
     */
    private String requireCurrentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userRepository.findByUsername(authentication.getName())
            .filter(user -> !user.isDeleted())
            .map(User::getId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }
}
