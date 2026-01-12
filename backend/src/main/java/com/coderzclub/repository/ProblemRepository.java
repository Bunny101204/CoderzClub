package com.coderzclub.repository;

import com.coderzclub.model.Problem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProblemRepository extends MongoRepository<Problem, String> {
    
    // Filter by difficulty
    Page<Problem> findByDifficulty(String difficulty, Pageable pageable);
    
    // Filter by difficulty and category
    Page<Problem> findByDifficultyAndCategory(String difficulty, String category, Pageable pageable);
    
    // Filter by tags (any tag in the list)
    @Query("{ 'tags': { $in: ?0 } }")
    Page<Problem> findByTagsIn(List<String> tags, Pageable pageable);
    
    // Search by title or statement
    @Query("{ $or: [ { 'title': { $regex: ?0, $options: 'i' } }, { 'statement': { $regex: ?0, $options: 'i' } } ] }")
    Page<Problem> findByTitleOrStatementContaining(String search, Pageable pageable);
    
    // Combined filter query
    @Query("{ $and: [ " +
           "?0 == null || { 'difficulty': ?0 }, " +
           "?1 == null || { 'category': ?1 }, " +
           "?2 == null || { $or: [ { 'title': { $regex: ?2, $options: 'i' } }, { 'statement': { $regex: ?2, $options: 'i' } } ] }, " +
           "?3 == null || { 'tags': { $in: ?3 } } " +
           "] }")
    Page<Problem> findWithFilters(String difficulty, String category, String search, List<String> tags, Pageable pageable);
} 