package com.coderzclub.service;

import java.util.Date;
import java.util.Calendar;

import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService implements UserDetailsService {
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    public User registerUser(String username, String email, String password, String role) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists");
        }
        User user = new User(null, username, email, passwordEncoder.encode(password), role, new Date());
        return userRepository.save(user);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getRole().toUpperCase())
                .build();
    }

    public boolean checkPassword(String rawPassword, String encodedPassword) {
        System.out.println("=== PASSWORD CHECK DEBUG ===");
        System.out.println("Raw password: " + rawPassword);
        System.out.println("Stored encoded password: " + encodedPassword);
        
        // BCrypt.matches() is the correct way to compare raw password with hash
        boolean matches = passwordEncoder.matches(rawPassword, encodedPassword);
        System.out.println("Password matches: " + matches);
        return matches;
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
        
        // Check if user was already active today
        if (lastActive != null && isSameDay(today, lastActive)) {
            return; // Already updated today, no need to update again
        }
        
        // Check if consecutive day
        if (lastActive != null && isConsecutiveDay(today, lastActive)) {
            // Increment streak
            int currentStreak = user.getCurrentStreak() > 0 ? user.getCurrentStreak() : 0;
            user.setCurrentStreak(currentStreak + 1);
        } else {
            // Reset streak if not consecutive (or first time)
            user.setCurrentStreak(1);
        }
        
        // Update longest streak if current streak is longer
        if (user.getCurrentStreak() > user.getLongestStreak()) {
            user.setLongestStreak(user.getCurrentStreak());
        }
        
        // Update last active date
        user.setLastActiveDate(today);
        userRepository.save(user);
    }
    
    /**
     * Checks if two dates are on the same day
     */
    private boolean isSameDay(Date date1, Date date2) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(date1);
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
    
    /**
     * Checks if today is the day after lastActive (consecutive day)
     */
    private boolean isConsecutiveDay(Date today, Date lastActive) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(today);
        cal2.setTime(lastActive);
        
        // Subtract one day from today
        cal1.add(Calendar.DAY_OF_YEAR, -1);
        
        // Check if yesterday equals lastActive
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
} 