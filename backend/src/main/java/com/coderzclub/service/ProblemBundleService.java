package com.coderzclub.service;

import com.coderzclub.model.ProblemBundle;
import com.coderzclub.repository.ProblemBundleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class ProblemBundleService {

    @Autowired
    private ProblemBundleRepository problemBundleRepository;

    @Autowired
    private ProblemIdentityResolver problemIdentityResolver;

    public List<ProblemBundle> getAllActiveBundles() {
        List<ProblemBundle> bundles = new java.util.ArrayList<>(problemBundleRepository.findByIsActiveTrue());
        normalizeAll(bundles);
        return bundles;
    }

    public List<ProblemBundle> getAllBundles() {
        List<ProblemBundle> bundles = new java.util.ArrayList<>(problemBundleRepository.findAllByOrderByCreatedAtDesc());
        normalizeAll(bundles);
        return bundles;
    }

    public ProblemBundle getBundleById(String id) {
        Optional<ProblemBundle> bundle = problemBundleRepository.findById(id);
        if (bundle.isEmpty()) {
            return null;
        }
        normalizeAll(java.util.List.of(bundle.get()));
        return bundle.get();
    }

    public List<ProblemBundle> getBundlesByDifficulty(String difficulty) {
        return problemBundleRepository.findByDifficultyAndIsActiveTrue(difficulty);
    }

    public List<ProblemBundle> getBundlesByCategory(String category) {
        return problemBundleRepository.findByCategoryAndIsActiveTrue(category);
    }

    public ProblemBundle createBundle(ProblemBundle bundle) {
        bundle.setCreatedAt(new Date());
        bundle.setUpdatedAt(new Date());
        bundle.setActive(true);
        if (bundle.getVisibility() == null || bundle.getVisibility().isBlank()) {
            bundle.setVisibility("PUBLIC");
        }
        
        // Calculate totals if not provided
        normalizeAll(java.util.List.of(bundle));
        
        // Set createdBy to current user (you might want to get this from SecurityContext)
        // For now, we'll set it to a default admin user
        if (bundle.getCreatedBy() == null) {
            bundle.setCreatedBy("admin");
        }
        
        return problemBundleRepository.save(bundle);
    }

    public ProblemBundle updateBundle(ProblemBundle bundle) {
        Optional<ProblemBundle> existingBundle = problemBundleRepository.findById(bundle.getId());
        if (existingBundle.isPresent()) {
            ProblemBundle existing = existingBundle.get();
            
            // Preserve creation date
            bundle.setCreatedAt(existing.getCreatedAt());
            bundle.setUpdatedAt(new Date());
            if (bundle.getVisibility() == null || bundle.getVisibility().isBlank()) {
                bundle.setVisibility(existing.getVisibility() == null || existing.getVisibility().isBlank()
                    ? "PUBLIC"
                    : existing.getVisibility());
            }
            
            // Calculate totals if not provided
            normalizeAll(java.util.List.of(bundle));
            
            return problemBundleRepository.save(bundle);
        }
        return null;
    }

    public boolean deleteBundle(String id) {
        Optional<ProblemBundle> bundle = problemBundleRepository.findById(id);
        if (bundle.isPresent()) {
            // Soft delete - just mark as inactive
            ProblemBundle existingBundle = bundle.get();
            existingBundle.setActive(false);
            existingBundle.setUpdatedAt(new Date());
            problemBundleRepository.save(existingBundle);
            return true;
        }
        return false;
    }

    public List<ProblemBundle> getBundlesByUserSubscription(String userId, boolean isPremium) {
        if (isPremium) {
            return getAllActiveBundles();
        } else {
            return problemBundleRepository.findByIsPremiumFalseAndIsActiveTrue();
        }
    }

    private void normalizeAll(java.util.List<ProblemBundle> bundles) {
        problemIdentityResolver.applyCanonicalProblemIds(bundles);
    }
}






