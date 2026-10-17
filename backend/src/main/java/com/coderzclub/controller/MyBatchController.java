package com.coderzclub.controller;

import com.coderzclub.model.User;
import com.coderzclub.service.BatchAccessService;
import com.coderzclub.service.BatchCoordinatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/my/batches")
public class MyBatchController {
    private final BatchAccessService batchAccessService;
    private final BatchCoordinatorService batchCoordinatorService;

    public MyBatchController(BatchAccessService batchAccessService, BatchCoordinatorService batchCoordinatorService) {
        this.batchAccessService = batchAccessService;
        this.batchCoordinatorService = batchCoordinatorService;
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        User user = batchAccessService.requireUser(authentication);
        return ResponseEntity.ok(Map.of("batches", batchCoordinatorService.listForUser(user.getId())));
    }
}
