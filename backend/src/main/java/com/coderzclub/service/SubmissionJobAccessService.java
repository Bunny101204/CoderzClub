package com.coderzclub.service;

import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class SubmissionJobAccessService {
    private final UserRepository userRepository;

    public SubmissionJobAccessService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean canView(Authentication authentication, SubmissionJob job) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        boolean admin = authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                || "ADMIN".equals(authority.getAuthority()));
        if (admin) return true;
        return userRepository.findByUsername(authentication.getName())
            .map(User::getId)
            .map(job.getUserId()::equals)
            .orElse(false);
    }
}