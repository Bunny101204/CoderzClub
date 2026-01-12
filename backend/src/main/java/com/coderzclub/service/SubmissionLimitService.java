package com.coderzclub.service;

import com.coderzclub.repository.SubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.Calendar;

/**
 * Service to handle submission limits and rate limiting
 */
@Service
public class SubmissionLimitService {
    
    @Autowired
    private SubmissionRepository submissionRepository;
    
    // Default limits (can be made configurable via application.properties)
    private static final int DEFAULT_DAILY_SUBMISSION_LIMIT = 100;
    private static final int DEFAULT_PROBLEM_SUBMISSION_LIMIT = 50; // Per problem per day
    private static final long MIN_SUBMISSION_INTERVAL_MS = 2000; // 2 seconds between submissions
    
    /**
     * Checks if user has exceeded daily submission limit
     */
    public boolean hasExceededDailyLimit(String userId) {
        Date today = getStartOfDay(new Date());
        long count = submissionRepository.findByUserId(userId)
            .stream()
            .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().after(today))
            .count();
        return count >= DEFAULT_DAILY_SUBMISSION_LIMIT;
    }
    
    /**
     * Checks if user has exceeded submission limit for a specific problem today
     */
    public boolean hasExceededProblemLimit(String userId, String problemId) {
        Date today = getStartOfDay(new Date());
        long count = submissionRepository.findByUserId(userId)
            .stream()
            .filter(s -> problemId.equals(s.getProblemId()) 
                && s.getCreatedAt() != null 
                && s.getCreatedAt().after(today))
            .count();
        return count >= DEFAULT_PROBLEM_SUBMISSION_LIMIT;
    }
    
    /**
     * Gets the number of submissions remaining for today
     */
    public int getRemainingDailySubmissions(String userId) {
        Date today = getStartOfDay(new Date());
        long count = submissionRepository.findByUserId(userId)
            .stream()
            .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().after(today))
            .count();
        return Math.max(0, DEFAULT_DAILY_SUBMISSION_LIMIT - (int)count);
    }
    
    /**
     * Gets the number of submissions remaining for a specific problem today
     */
    public int getRemainingProblemSubmissions(String userId, String problemId) {
        Date today = getStartOfDay(new Date());
        long count = submissionRepository.findByUserId(userId)
            .stream()
            .filter(s -> problemId.equals(s.getProblemId()) 
                && s.getCreatedAt() != null 
                && s.getCreatedAt().after(today))
            .count();
        return Math.max(0, DEFAULT_PROBLEM_SUBMISSION_LIMIT - (int)count);
    }
    
    /**
     * Checks if enough time has passed since last submission (rate limiting)
     */
    public boolean canSubmitNow(String userId) {
        var submissions = submissionRepository.findByUserId(userId);
        if (submissions.isEmpty()) {
            return true;
        }
        
        // Get most recent submission
        var mostRecent = submissions.stream()
            .max((s1, s2) -> {
                if (s1.getCreatedAt() == null) return -1;
                if (s2.getCreatedAt() == null) return 1;
                return s1.getCreatedAt().compareTo(s2.getCreatedAt());
            });
        
        if (mostRecent.isEmpty() || mostRecent.get().getCreatedAt() == null) {
            return true;
        }
        
        long timeSinceLastSubmission = new Date().getTime() - mostRecent.get().getCreatedAt().getTime();
        return timeSinceLastSubmission >= MIN_SUBMISSION_INTERVAL_MS;
    }
    
    /**
     * Gets the time remaining until next submission is allowed (in seconds)
     */
    public long getCooldownSeconds(String userId) {
        var submissions = submissionRepository.findByUserId(userId);
        if (submissions.isEmpty()) {
            return 0;
        }
        
        var mostRecent = submissions.stream()
            .max((s1, s2) -> {
                if (s1.getCreatedAt() == null) return -1;
                if (s2.getCreatedAt() == null) return 1;
                return s1.getCreatedAt().compareTo(s2.getCreatedAt());
            });
        
        if (mostRecent.isEmpty() || mostRecent.get().getCreatedAt() == null) {
            return 0;
        }
        
        long timeSinceLastSubmission = new Date().getTime() - mostRecent.get().getCreatedAt().getTime();
        long remaining = MIN_SUBMISSION_INTERVAL_MS - timeSinceLastSubmission;
        return remaining > 0 ? (remaining / 1000) : 0;
    }
    
    /**
     * Gets the start of the day (00:00:00) for a given date
     */
    private Date getStartOfDay(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
}

