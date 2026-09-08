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
    @Query("{ 'username': { $regex: '^?0$', $options: 'i' } }")
    Optional<User> findByUsername(String username);

    @Query("{ 'email': { $regex: '^?0$', $options: 'i' } }")
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailVerificationToken(String token);
    Optional<User> findByPasswordResetToken(String token);
    
    // Get top users by total points, efficiently sorted at database level
    @Query(value = "{}", sort = "{ 'totalPoints': -1 }")
    List<User> findTopUsersByPoints();

    Page<User> findAllByOrderByTotalPointsDesc(Pageable pageable);
} 