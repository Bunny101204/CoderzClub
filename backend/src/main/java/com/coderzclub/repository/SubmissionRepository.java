package com.coderzclub.repository;

import com.coderzclub.model.Submission;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SubmissionRepository extends MongoRepository<Submission, String> {
    List<Submission> findByUserId(String userId);
    List<Submission> findByProblemId(String problemId);
    
    // Pagination methods
    Page<Submission> findByUserId(String userId, Pageable pageable);
    Page<Submission> findByProblemId(String problemId, Pageable pageable);
    Page<Submission> findByUserIdAndProblemId(String userId, String problemId, Pageable pageable);
    Page<Submission> findByUserIdAndResult(String userId, String result, Pageable pageable);
    Page<Submission> findByUserIdAndProblemIdAndResult(String userId, String problemId, String result, Pageable pageable);
} 