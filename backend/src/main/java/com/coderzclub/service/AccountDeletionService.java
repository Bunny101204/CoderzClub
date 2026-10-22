package com.coderzclub.service;

import com.coderzclub.model.BatchMember;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountDeletionService {
    public static final String CONFIRMATION_PHRASE = "DELETE";

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final PasswordEncoder passwordEncoder;
    private final LeaderboardService leaderboardService;

    public AccountDeletionService(
        UserRepository userRepository,
        MongoTemplate mongoTemplate,
        PasswordEncoder passwordEncoder,
        LeaderboardService leaderboardService
    ) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.passwordEncoder = passwordEncoder;
        this.leaderboardService = leaderboardService;
    }

    public Map<String, Object> deleteSelf(User user, String confirmation) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (!CONFIRMATION_PHRASE.equals(confirmation == null ? "" : confirmation.trim())) {
            throw new IllegalArgumentException("Type DELETE to confirm account deletion");
        }
        return delete(user);
    }

    public Map<String, Object> delete(User user) {
        boolean alreadyDeleted = user.isDeleted();
        if (!alreadyDeleted) {
            String id = user.getId();
            user.setUsername("deleted-" + id);
            user.setEmail("deleted-" + id + "@users.invalid");
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setEmailVerified(false);
            user.setEmailVerificationToken(null);
            user.setEmailVerificationTokenExpiry(null);
            user.setPasswordResetToken(null);
            user.setPasswordResetTokenExpiry(null);
            user.setBio(null);
            user.setLocation(null);
            user.setWebsite(null);
            user.setProfilePicture(null);
            user.setSocialLinks(null);
            user.setSubscriptionId(null);
            user.setSubscriptionPlan(null);
            user.setSubscriptionExpiry(null);
            user.setPremium(false);
            user.setAccountStatus("DELETED");
            user.setDeletedAt(new Date());
            userRepository.save(user);
        }
        if (leaderboardService != null) {
            leaderboardService.removeParticipant(user.getId());
        }
        String id = user.getId();
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(id)), BatchMember.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(id)), "subscriptions");
        return result(user, alreadyDeleted);
    }

    private static Map<String, Object> result(User user, boolean alreadyDeleted) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deleted", true);
        body.put("alreadyDeleted", alreadyDeleted);
        body.put("userId", user.getId());
        return body;
    }
}
