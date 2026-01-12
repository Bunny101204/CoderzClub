package com.coderzclub.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coderzclub.model.ProblemBundle;
import com.coderzclub.service.ProblemBundleService;

@RestController
@RequestMapping("/api/bundles")
public class ProblemBundleController {

    @Autowired
    private ProblemBundleService problemBundleService;

    @GetMapping
    public ResponseEntity<List<ProblemBundle>> getAllBundles() {
        try {
            List<ProblemBundle> bundles = problemBundleService.getAllActiveBundles();
            return ResponseEntity.ok(bundles);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }



        
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProblemBundle> getBundleById(@PathVariable String id) {
        try {
            ProblemBundle bundle = problemBundleService.getBundleById(id);
            if (bundle != null) {
                return ResponseEntity.ok(bundle);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/difficulty/{difficulty}")
    public ResponseEntity<List<ProblemBundle>> getBundlesByDifficulty(@PathVariable String difficulty) {
        try {
            List<ProblemBundle> bundles = problemBundleService.getBundlesByDifficulty(difficulty);
            return ResponseEntity.ok(bundles);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<ProblemBundle>> getBundlesByCategory(@PathVariable String category) {
        try {
            List<ProblemBundle> bundles = problemBundleService.getBundlesByCategory(category);
            return ResponseEntity.ok(bundles);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createBundle(@RequestBody ProblemBundle bundle) {
        System.out.println("=== BUNDLE CREATION DEBUG ===");
        System.out.println("Create bundle endpoint hit");
        
        // Get current authentication
        org.springframework.security.core.Authentication auth = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        System.out.println("Current authentication: " + auth);
        System.out.println("Authentication authorities: " + auth.getAuthorities());
        System.out.println("Authentication principal: " + auth.getPrincipal());
        
        try {
            ProblemBundle createdBundle = problemBundleService.createBundle(bundle);
            return ResponseEntity.ok(createdBundle);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Create bundle failed: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateBundle(@PathVariable String id, @RequestBody ProblemBundle bundle) {
        try {
            bundle.setId(id);
            ProblemBundle updatedBundle = problemBundleService.updateBundle(bundle);
            if (updatedBundle != null) {
                return ResponseEntity.ok(updatedBundle);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Update bundle failed: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBundle(@PathVariable String id) {
        try {
            boolean deleted = problemBundleService.deleteBundle(id);
            if (deleted) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}





