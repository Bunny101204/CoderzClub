package com.coderzclub.service;

import com.coderzclub.model.BatchCoordinator;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BatchAccessService {
    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;

    public BatchAccessService(MongoTemplate mongoTemplate, UserRepository userRepository) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
    }

    public boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public void ensureAdmin(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is required");
        }
    }

    public void ensureCanReadBatch(Authentication authentication, String batchId) {
        if (isAdmin(authentication)) {
            return;
        }
        User user = requireUser(authentication);
        if (!coordinates(user.getId(), batchId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this batch");
        }
    }

    public void ensureCanSearchUsers(Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }
        User user = requireUser(authentication);
        boolean coordinator = mongoTemplate.exists(
            Query.query(Criteria.where("userId").is(user.getId())),
            BatchCoordinator.class);
        if (!coordinator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot search students");
        }
    }

    public User requireUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userRepository.findByUsername(authentication.getName())
            .filter(user -> !user.isDeleted())
            .filter(User::canAuthenticate)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }

    public boolean coordinates(String userId, String batchId) {
        if (userId == null || batchId == null) {
            return false;
        }
        return mongoTemplate.exists(
            Query.query(Criteria.where("userId").is(userId).and("batchId").is(batchId)),
            BatchCoordinator.class);
    }
}
