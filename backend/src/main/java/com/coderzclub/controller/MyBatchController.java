package com.coderzclub.controller;

import com.coderzclub.model.User;
import com.coderzclub.service.BatchAccessService;
import com.coderzclub.service.BatchCoordinatorService;
import com.coderzclub.service.BatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/my/batches")
public class MyBatchController {
    private final BatchAccessService batchAccessService;
    private final BatchCoordinatorService batchCoordinatorService;
    private final BatchService batchService;

    public MyBatchController(
        BatchAccessService batchAccessService,
        BatchCoordinatorService batchCoordinatorService,
        BatchService batchService
    ) {
        this.batchAccessService = batchAccessService;
        this.batchCoordinatorService = batchCoordinatorService;
        this.batchService = batchService;
    }

    @GetMapping
    public ResponseEntity<?> list(
        Authentication authentication,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        User user = batchAccessService.requireUser(authentication);
        Map<String, Object> enrolled = batchService.listEnrolledForUser(user.getId(), page, size);
        if (enrolled == null) {
            enrolled = Map.of("items", List.of(), "currentPage", page, "totalPages", 0, "totalItems", 0);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("batches", batchCoordinatorService.listForUser(user.getId()));
        body.put("enrolled", enrolled);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{batchId}/practice")
    public ResponseEntity<?> practice(
        Authentication authentication,
        @PathVariable String batchId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        User user = batchAccessService.requireUser(authentication);
        return ResponseEntity.ok(batchService.enrolledPractice(user.getId(), batchId, page, size));
    }
}
