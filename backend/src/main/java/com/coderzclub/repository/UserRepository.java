package com.coderzclub.repository;

import com.coderzclub.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


@Repository
public interface UserRepository extends MongoRepository<User, String> {
    /**
     * Exact match so the unique {@code username} index can be used (IXSCAN).
     * Case-insensitive regex previously forced a full index/collection scan under load.
     */
    Optional<User> findByUsername(String username);

    /** Exact match on stored email (callers should normalize emails to lowercase). */
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailVerificationToken(String token);
    Optional<User> findByPasswordResetToken(String token);
    
    // Get top users by total points, efficiently sorted at database level
    @Query(value = "{}", sort = "{ 'totalPoints': -1 }")
    List<User> findTopUsersByPoints();

    Page<User> findAllByOrderByTotalPointsDesc(Pageable pageable);

    Page<User> findByAccountStatusNot(String accountStatus, Pageable pageable);

    long countByAccountStatusNot(String accountStatus);
} 