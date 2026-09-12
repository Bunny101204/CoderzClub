package com.coderzclub.service;

import com.coderzclub.model.ProblemBundle;
import com.coderzclub.model.Problem;
import com.coderzclub.repository.ProblemRepository;
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
    private ProblemRepository problemRepository;

    public List<ProblemBundle> getAllActiveBundles() {
        return problemBundleRepository.findByIsActiveTrue().stream().map(this::normalize).toList();
    }

    public List<ProblemBundle> getAllBundles() {
        return problemBundleRepository.findAllByOrderByCreatedAtDesc().stream().map(this::normalize).toList();
    }

    public ProblemBundle getBundleById(String id) {
        Optional<ProblemBundle> bundle = problemBundleRepository.findById(id);
        return bundle.map(this::normalize).orElse(null);
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
        
        // Calculate totals if not provided
        normalize(bundle);
        
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
            
            // Calculate totals if not provided
            normalize(bundle);
            
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

    private ProblemBundle normalize(ProblemBundle bundle) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        if (bundle.getProblemIds() != null) {
            for (String rawId : bundle.getProblemIds()) {
                if (rawId == null || rawId.isBlank()) continue;
                Problem problem = problemRepository.findById(rawId).orElse(null);
                if (problem == null) {
                    try {
                        problem = problemRepository.findByNumericId(Integer.valueOf(rawId)).orElse(null);
                    } catch (NumberFormatException ignored) { }
                }
                if (problem != null && !ids.contains(problem.getId())) ids.add(problem.getId());
            }
        }
        bundle.setProblemIds(ids);
        bundle.setTotalProblems(ids.size());
        return bundle;
    }
}






