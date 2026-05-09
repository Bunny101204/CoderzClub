package com.coderzclub.service;

import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService implements UserDetailsService {
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.verification.token-expiration-ms:86400000}")
    private long verificationTokenExpirationMs;

    @Value("${app.password-reset.token-expiration-ms:3600000}")
    private long resetTokenExpirationMs;

    public User registerUser(String username, String email, String password, String role) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        Optional<User> emailOwner = userRepository.findByEmail(email);
        if (emailOwner.isPresent()) {
            User existing = emailOwner.get();
            if (existing.isEmailVerified()) {
                throw new RuntimeException("An account already exists with that email address");
            }
            throw new RuntimeException("An account already exists with that email address. Please verify your email or use password reset.");
        }

        User user = new User(null, username, email, passwordEncoder.encode(password), role, new Date());
        user.setEmailVerified(false);
        user.setEmailVerificationToken(generateToken());
        user.setEmailVerificationTokenExpiry(new Date(System.currentTimeMillis() + verificationTokenExpirationMs));
        return userRepository.save(user);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByUsernameOrEmail(String identifier) {
        Optional<User> userOpt = findByUsername(identifier);
        if (userOpt.isPresent()) {
            return userOpt;
        }
        return findByEmail(identifier);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
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

    public User createPasswordResetToken(String email) {
        User user = findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        user.setPasswordResetToken(generateToken());
        user.setPasswordResetTokenExpiry(new Date(System.currentTimeMillis() + resetTokenExpirationMs));
        return userRepository.save(user);
    }

    public User resetPassword(String token, String newPassword) {
        User user = findByPasswordResetToken(token)
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
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isEmailVerified())
                .authorities("ROLE_" + user.getRole().toUpperCase())
                .accountExpired(false)
                .credentialsExpired(false)
                .accountLocked(false)
                .build();
    }

    public boolean checkPassword(String rawPassword, String encodedPassword) {
        System.out.println("=== PASSWORD CHECK DEBUG ===");
        System.out.println("Raw password: " + rawPassword);
        System.out.println("Stored encoded password: " + encodedPassword);
        
        boolean matches = passwordEncoder.matches(rawPassword, encodedPassword);
        System.out.println("Password matches: " + matches);
        return matches;
    }

    public User resendVerificationEmail(String usernameOrEmail) {
        User user = findByUsernameOrEmail(usernameOrEmail)
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
        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            return;
        }
        
        User user = userOpt.get();
        Date today = new Date();
        Date lastActive = user.getLastActiveDate();
        
        if (lastActive != null && isSameDay(today, lastActive)) {
            return;
        }
        
        if (lastActive != null && isConsecutiveDay(today, lastActive)) {
            int currentStreak = user.getCurrentStreak() > 0 ? user.getCurrentStreak() : 0;
            user.setCurrentStreak(currentStreak + 1);
        } else {
            user.setCurrentStreak(1);
        }
        
        if (user.getCurrentStreak() > user.getLongestStreak()) {
            user.setLongestStreak(user.getCurrentStreak());
        }
        
        user.setLastActiveDate(today);
        userRepository.save(user);
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