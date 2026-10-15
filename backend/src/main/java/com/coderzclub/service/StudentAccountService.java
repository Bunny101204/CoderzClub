package com.coderzclub.service;

import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class StudentAccountService {
    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;

    public StudentAccountService(MongoTemplate mongoTemplate, UserRepository userRepository) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
    }

    public Map<String, Object> search(String search, int page, int size) {
        int safeSize = BatchService.boundSize(size);
        int safePage = Math.max(0, page);
        Query query = new Query();
        query.addCriteria(new Criteria().orOperator(
            Criteria.where("accountStatus").exists(false),
            Criteria.where("accountStatus").ne("DELETED")
        ));
        if (search != null && !search.isBlank()) {
            String escaped = Pattern.quote(search.trim());
            query.addCriteria(new Criteria().orOperator(
                Criteria.where("username").regex(escaped, "i"),
                Criteria.where("email").regex(escaped, "i")
            ));
        }
        long total = mongoTemplate.count(query, User.class);
        query.skip((long) safePage * safeSize).limit(safeSize);
        List<Map<String, Object>> users = new ArrayList<>();
        for (User user : mongoTemplate.find(query, User.class)) {
            users.add(summary(user));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("users", users);
        body.put("currentPage", safePage);
        body.put("totalItems", total);
        body.put("totalPages", total == 0 ? 0 : (int) Math.ceil((double) total / safeSize));
        return body;
    }

    public Map<String, Object> profile(String userId) {
        User user = requireManageable(userId);
        Map<String, Object> body = summary(user);
        body.put("bio", user.getBio() == null ? "" : user.getBio());
        body.put("location", user.getLocation() == null ? "" : user.getLocation());
        body.put("website", user.getWebsite() == null ? "" : user.getWebsite());
        body.put("createdAt", user.getCreatedAt());
        body.put("batches", batchesFor(user.getId()));
        return body;
    }

    public Map<String, Object> updateProfile(String userId, String bio, String location, String website) {
        User user = requireManageable(userId);
        user.setBio(bounded(bio, 500, "Bio"));
        user.setLocation(bounded(location, 120, "Location"));
        user.setWebsite(website(website));
        userRepository.save(user);
        return profile(user.getId());
    }

    public Map<String, Object> setActive(String userId, boolean active, String actorUsername) {
        User user = requireManageable(userId);
        if (actorUsername != null && actorUsername.equalsIgnoreCase(user.getUsername()) && !active) {
            throw new IllegalArgumentException("You cannot deactivate your own account.");
        }
        String next = active ? "ACTIVE" : "INACTIVE";
        assertRetainsAdministrator(user, user.getRole(), next);
        user.setAccountStatus(next);
        userRepository.save(user);
        return profile(user.getId());
    }

    public void assertRetainsAdministrator(User target, String nextRole, String nextStatus) {
        if (target == null || !isAdmin(target) || target.isDeleted() || target.isInactive()) {
            return;
        }
        boolean staysAdmin = isAdminRole(nextRole) && "ACTIVE".equals(nextStatus);
        if (!staysAdmin && activeAdminCount() <= 1) {
            throw new IllegalArgumentException("The last administrator cannot be deactivated or demoted.");
        }
    }

    private User requireManageable(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private List<Map<String, Object>> batchesFor(String userId) {
        List<BatchMember> memberships = mongoTemplate.find(
            Query.query(Criteria.where("userId").is(userId)),
            BatchMember.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BatchMember membership : memberships) {
            if (membership.getBatchId() == null) {
                continue;
            }
            Batch batch = mongoTemplate.findById(membership.getBatchId(), Batch.class);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", membership.getBatchId());
            row.put("name", batch == null ? membership.getBatchId() : batch.getName());
            row.put("active", batch != null && batch.isActive());
            rows.add(row);
        }
        return rows;
    }

    private long activeAdminCount() {
        return mongoTemplate.count(Query.query(new Criteria().andOperator(
            Criteria.where("role").regex("^admin$", "i"),
            new Criteria().orOperator(
                Criteria.where("accountStatus").exists(false),
                Criteria.where("accountStatus").is("ACTIVE")
            )
        )), User.class);
    }

    private static boolean isAdmin(User user) {
        return user != null && isAdminRole(user.getRole());
    }

    private static boolean isAdminRole(String role) {
        return role != null && role.trim().equalsIgnoreCase("ADMIN");
    }

    private static String bounded(String value, int max, String label) {
        String text = value == null ? "" : value.trim();
        if (text.length() > max) {
            throw new IllegalArgumentException(label + " must be at most " + max + " characters.");
        }
        return text;
    }

    private static String website(String value) {
        String text = bounded(value, 200, "Website");
        if (text.isEmpty()) {
            return "";
        }
        if (!text.startsWith("https://") && !text.startsWith("http://")) {
            throw new IllegalArgumentException("Website must start with http:// or https://.");
        }
        return text;
    }

    private static Map<String, Object> summary(User user) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("username", user.getUsername());
        row.put("email", user.getEmail());
        row.put("role", user.getRole() == null ? "USER" : user.getRole());
        row.put("emailVerified", user.isEmailVerified());
        row.put("accountStatus", user.getAccountStatus() == null ? "ACTIVE" : user.getAccountStatus());
        return row;
    }
}
