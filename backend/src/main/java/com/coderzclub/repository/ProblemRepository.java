package com.coderzclub.repository;

import com.coderzclub.model.Problem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProblemRepository extends MongoRepository<Problem, String> {
       Optional<Problem> findByNumericId(Integer numericId);

    // Ordinary practice-list queries exclude CONTEST_ONLY. Missing availability is PUBLIC ($ne).
    @Query("{ 'difficulty': ?0, 'availability': { $ne: 'CONTEST_ONLY' } }")
    Page<Problem> findByDifficulty(String difficulty, Pageable pageable);

    @Query("{ 'difficulty': ?0, 'category': ?1, 'availability': { $ne: 'CONTEST_ONLY' } }")
    Page<Problem> findByDifficultyAndCategory(String difficulty, String category, Pageable pageable);

    @Query("{ 'tags': { $in: ?0 }, 'availability': { $ne: 'CONTEST_ONLY' } }")
    Page<Problem> findByTagsIn(List<String> tags, Pageable pageable);

    @Query("{ $and: [ { 'availability': { $ne: 'CONTEST_ONLY' } }, { $or: [ { 'title': { $regex: ?0, $options: 'i' } }, { 'statement': { $regex: ?0, $options: 'i' } } ] } ] }")
    Page<Problem> findByTitleOrStatementContaining(String search, Pageable pageable);

    @Query("{ $and: [ " +
           "{ 'availability': { $ne: 'CONTEST_ONLY' } }, " +
           "?0 == null || { 'difficulty': ?0 }, " +
           "?1 == null || { 'category': ?1 }, " +
           "?2 == null || { $or: [ { 'title': { $regex: ?2, $options: 'i' } }, { 'statement': { $regex: ?2, $options: 'i' } }, { '_id': { $regex: ?2, $options: 'i' } } ] }, " +
           "?3 == null || { 'tags': { $in: ?3 } } " +
           "] }")
    Page<Problem> findWithFilters(String difficulty, String category, String search, List<String> tags, Pageable pageable);

    @Query("{ $and: [ " +
           "{ 'availability': { $ne: 'CONTEST_ONLY' } }, " +
           "?0 == null || { 'difficulty': ?0 }, " +
           "?1 == null || { 'category': ?1 }, " +
           "?2 == null || { $or: [ { 'title': { $regex: ?2, $options: 'i' } }, { 'statement': { $regex: ?2, $options: 'i' } }, { '_id': { $regex: ?2, $options: 'i' } } ] }, " +
           "?3 == null || { 'tags': { $in: ?3 } } " +
           "] }")
    List<Problem> findAllWithFilters(String difficulty, String category, String search, List<String> tags);
}
