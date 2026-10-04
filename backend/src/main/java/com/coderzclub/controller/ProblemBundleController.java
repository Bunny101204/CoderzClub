package com.coderzclub.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coderzclub.dto.ProblemDetailResponse;
import com.coderzclub.model.ProblemBundle;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.BundleAccessService;
import com.coderzclub.service.ProblemBundleService;

@RestController
@RequestMapping("/api/bundles")
public class ProblemBundleController {

    @Autowired
    private ProblemBundleService problemBundleService;

    @Autowired
    private BundleAccessService bundleAccessService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<ProblemBundle>> getAllBundles() {
        try {
            return ResponseEntity.ok(bundleAccessService.listAccessibleActiveBundles(currentUser().orElse(null)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProblemBundle>> getAllBundlesForAdmin() {
        try {
            return ResponseEntity.ok(problemBundleService.getAllBundles());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProblemBundle> getBundleById(@PathVariable String id) {
        ProblemBundle bundle = bundleAccessService.requireAccessibleBundle(currentUser().orElse(null), id);
        ProblemBundle normalized = problemBundleService.getBundleById(id);
        return ResponseEntity.ok(normalized != null ? normalized : bundle);
    }

    @GetMapping("/{id}/problems")
    public ResponseEntity<?> getBundleProblems(@PathVariable String id) {
        List<ProblemDetailResponse> problems = bundleAccessService.problemsForAccessibleBundle(currentUser().orElse(null), id)
            .stream()
            .map(ProblemDetailResponse::new)
            .toList();
        return ResponseEntity.ok(java.util.Map.of("problems", problems));
    }

    @GetMapping("/difficulty/{difficulty}")
    public ResponseEntity<List<ProblemBundle>> getBundlesByDifficulty(@PathVariable String difficulty) {
        List<ProblemBundle> bundles = bundleAccessService.listAccessibleActiveBundles(currentUser().orElse(null))
            .stream()
            .filter(bundle -> difficulty.equalsIgnoreCase(bundle.getDifficulty()))
            .toList();
        return ResponseEntity.ok(bundles);
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<ProblemBundle>> getBundlesByCategory(@PathVariable String category) {
        List<ProblemBundle> bundles = bundleAccessService.listAccessibleActiveBundles(currentUser().orElse(null))
            .stream()
            .filter(bundle -> category.equalsIgnoreCase(bundle.getCategory()))
            .toList();
        return ResponseEntity.ok(bundles);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createBundle(@RequestBody ProblemBundle bundle) {
        try {
            ProblemBundle createdBundle = problemBundleService.createBundle(bundle);
            return ResponseEntity.ok(createdBundle);
        } catch (Exception e) {
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

    private Optional<User> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
            || "anonymousUser".equals(auth.getName())) {
            return Optional.empty();
        }
        return userRepository.findByUsername(auth.getName());
    }
}
