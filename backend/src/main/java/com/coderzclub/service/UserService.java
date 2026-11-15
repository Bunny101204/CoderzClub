package com.coderzclub.service;

import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import com.mongodb.client.result.UpdateResult;
import com.coderzclub.model.Submission;

@Service
public class UserService implements UserDetailsService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Value("${app.verification.token-expiration-ms:86400000}")
    private long verificationTokenExpirationMs;

    @Value("${app.password-reset.token-expiration-ms:3600000}")
    private long resetTokenExpirationMs;

    public User registerUser(String username, String email, String password) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedEmail = normalizeEmail(email);
        if (normalizedUsername == null || normalizedEmail == null) {
            throw new RuntimeException("Username and email are required");
        }
        // Exact + rare legacy case-insensitive collision check (unique indexes remain the race backstop).
        if (findByUsername(normalizedUsername).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        Optional<User> emailOwner = findByEmail(normalizedEmail);
        if (emailOwner.isPresent()) {
            User existing = emailOwner.get();
            if (existing.isEmailVerified()) {
                throw new RuntimeException("An account already exists with that email address");
            }
            throw new RuntimeException("An account already exists with that email address. Please verify your email or use password reset.");
        }

        User user = new User(null, normalizedUsername, normalizedEmail, passwordEncoder.encode(password), AccountRoles.publicRegistrationRole(), new Date());
        user.setEmailVerified(false);
        user.setAccountStatus("ACTIVE");
        user.setEmailVerificationToken(generateToken());
        user.setEmailVerificationTokenExpiry(new Date(System.currentTimeMillis() + verificationTokenExpirationMs));
        return userRepository.save(user);
    }

    /**
     * Prefer exact indexed equality. Only on miss, fall back to a rare case-insensitive
     * lookup for legacy mixed-case stored usernames (avoids regex on the hot path).
     */
    public Optional<User> findByUsername(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return Optional.empty();
        }
        Optional<User> exact = userRepository.findByUsername(normalized);
        if (exact.isPresent()) {
            return exact;
        }
        return findUsernameCaseInsensitiveLegacy(normalized);
    }

    public Optional<User> findByUsernameOrEmail(String identifier) {
        String trimmed = identifier == null ? null : identifier.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (trimmed.indexOf('@') >= 0) {
            return findByEmail(trimmed);
        }
        Optional<User> userOpt = findByUsername(trimmed);
        if (userOpt.isPresent()) {
            return userOpt;
        }
        // Legacy clients may still send an email-shaped value without '@' checks above.
        return findByEmail(trimmed);
    }

    /**
     * Emails are normalized to lowercase for new writes. Exact match first; rare
     * case-insensitive fallback covers legacy mixed-case stored emails.
     */
    public Optional<User> findByEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null) {
            return Optional.empty();
        }
        Optional<User> exact = userRepository.findByEmail(normalized);
        if (exact.isPresent()) {
            return exact;
        }
        return findEmailCaseInsensitiveLegacy(normalized);
    }

    static String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private Optional<User> findUsernameCaseInsensitiveLegacy(String username) {
        if (mongoTemplate == null || username == null || username.isEmpty()) {
            return Optional.empty();
        }
        Query query = new Query(Criteria.where("username").regex("^" + Pattern.quote(username) + "$", "i"));
        return Optional.ofNullable(mongoTemplate.findOne(query, User.class));
    }

    private Optional<User> findEmailCaseInsensitiveLegacy(String email) {
        if (mongoTemplate == null || email == null || email.isEmpty()) {
            return Optional.empty();
        }
        Query query = new Query(Criteria.where("email").regex("^" + Pattern.quote(email) + "$", "i"));
        return Optional.ofNullable(mongoTemplate.findOne(query, User.class));
    }

    public Optional<User> findByEmailVerificationToken(String token) {
        return userRepository.findByEmailVerificationToken(token);
    }

    public Optional<User> findByPasswordResetToken(String token) {
        return userRepository.findByPasswordResetToken(token);
    }

    public User verifyEmailToken(String token) {
        User user = findByEmailVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));
        if (user.getEmailVerificationTokenExpiry() == null || user.getEmailVerificationTokenExpiry().before(new Date())) {
            throw new RuntimeException("Verification token has expired");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        return userRepository.save(user);
    }

    public Optional<User> createPasswordResetToken(String email) {
        Optional<User> userOpt = findByEmail(email).filter(candidate -> !candidate.isDeleted());
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        User user = userOpt.get();
        user.setPasswordResetToken(generateToken());
        user.setPasswordResetTokenExpiry(new Date(System.currentTimeMillis() + resetTokenExpirationMs));
        return Optional.of(userRepository.save(user));
    }

    public User resetPassword(String token, String newPassword) {
        User user = findByPasswordResetToken(token)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new RuntimeException("Invalid reset token"));
        if (user.getPasswordResetTokenExpiry() == null || user.getPasswordResetTokenExpiry().before(new Date())) {
            throw new RuntimeException("Password reset token has expired");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        return userRepository.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return toUserDetails(loadUserForAuthentication(null, username));
    }

    public User loadUserForAuthentication(String userId, String username) throws UsernameNotFoundException {
        User user;
        if (userId != null && !userId.isBlank()) {
            user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
            if (username != null && user.getUsername() != null
                && !user.getUsername().equalsIgnoreCase(username)) {
                throw new UsernameNotFoundException("User not found");
            }
        } else {
            user = findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        }
        if (!user.canAuthenticate()) {
            throw new UsernameNotFoundException("User not found");
        }
        return user;
    }

    public UserDetails toUserDetails(User user) {
        String normalizedRole = (user.getRole() == null ? "USER" : user.getRole().trim().toUpperCase());
        String roleAuthority = "ROLE_" + normalizedRole;
        String password = user.getPasswordHash() == null ? "" : user.getPasswordHash();
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(password)
                .disabled(!user.isEmailVerified() || !user.canAuthenticate())
                .authorities(roleAuthority)
                .accountExpired(false)
                .credentialsExpired(false)
                .accountLocked(false)
                .build();
    }

    public boolean checkPassword(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public User resendVerificationEmail(String usernameOrEmail) {
        User user = findByUsernameOrEmail(usernameOrEmail)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Email is already verified");
        }

        if (user.getEmailVerificationToken() == null || user.getEmailVerificationTokenExpiry() == null ||
                user.getEmailVerificationTokenExpiry().before(new Date())) {
            user.setEmailVerificationToken(generateToken());
            user.setEmailVerificationTokenExpiry(new Date(System.currentTimeMillis() + verificationTokenExpirationMs));
            user = userRepository.save(user);
        }

        return user;
    }
    
    public PasswordEncoder getPasswordEncoder() {
        return passwordEncoder;
    }
    
    /**
     * Updates the user's streak based on their activity.
     * Should be called when a user makes a submission or solves a problem.
     */
    public void updateUserStreak(String userId) {
        for (int attempt = 0; attempt < 5; attempt++) {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) return;

            User user = userOpt.get();
            Date today = new Date();
            Date lastActive = user.getLastActiveDate();
            if (lastActive != null && isSameDay(today, lastActive)) return;

            int nextStreak = lastActive != null && isConsecutiveDay(today, lastActive)
                ? Math.max(0, user.getCurrentStreak()) + 1 : 1;
            Query query = Query.query(Criteria.where("_id").is(userId));
            if (lastActive == null) {
                query.addCriteria(Criteria.where("lastActiveDate").is(null));
            } else {
                query.addCriteria(Criteria.where("lastActiveDate").is(lastActive));
            }
            Update update = new Update()
                .set("currentStreak", nextStreak)
                .set("lastActiveDate", today);
            if (nextStreak > user.getLongestStreak()) {
                update.set("longestStreak", nextStreak);
            }
            UpdateResult result = mongoTemplate.updateFirst(query, update, User.class);
            if (result.getModifiedCount() > 0) return;
        }
        logger.warn("Could not update streak due to concurrent changes for user {}", userId);
    }

    public void recordFinalSubmission(Submission submission) {
        Query claim = Query.query(Criteria.where("_id").is(submission.getId()).and("statsCounted").ne(true));
        Update claimUpdate = new Update().set("statsCounted", true);
        if (mongoTemplate.updateFirst(claim, claimUpdate, Submission.class).getModifiedCount() == 0) return;

        Query statsQuery = Query.query(Criteria.where("_id").is(submission.getUserId()));
        Update statsUpdate = new Update()
            .inc("totalSubmissions", 1)
            .set("lastSubmissionAt", submission.getCreatedAt())
            .set("updatedAt", new Date())
            .setOnInsert("userId", submission.getUserId());
        if ("ACCEPTED".equals(submission.getResult())) statsUpdate.inc("acceptedSubmissions", 1);
        else statsUpdate.inc("rejectedSubmissions", 1);
        try {
            mongoTemplate.upsert(statsQuery, statsUpdate, com.coderzclub.model.UserStats.class);
            syncStatsProfile(submission.getUserId());
        } catch (RuntimeException failure) {
            mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(submission.getId()).and("statsCounted").is(true)),
                new Update().set("statsCounted", false), Submission.class);
            throw failure;
        }
    }

    private void syncStatsProfile(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            Query query = Query.query(Criteria.where("_id").is(userId));
            Update update = new Update()
                .set("totalPoints", user.getTotalPoints())
                .set("problemsSolved", user.getProblemsSolved())
                .set("currentStreak", user.getCurrentStreak())
                .set("longestStreak", user.getLongestStreak())
                .set("updatedAt", new Date());
            mongoTemplate.upsert(query, update, com.coderzclub.model.UserStats.class);
        });
    }
    
    private boolean isSameDay(Date date1, Date date2) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(date1);
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
    
    private boolean isConsecutiveDay(Date today, Date lastActive) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(today);
        cal2.setTime(lastActive);
        cal1.add(Calendar.DAY_OF_YEAR, -1);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }
} 