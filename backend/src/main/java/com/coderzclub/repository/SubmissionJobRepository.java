package com.coderzclub.repository;

import com.coderzclub.model.SubmissionJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionJobRepository extends MongoRepository<SubmissionJob, String> {

    // Find jobs by user
    List<SubmissionJob> findByUserIdOrderByCreatedAtDesc(String userId);

    // Find jobs by problem
    List<SubmissionJob> findByProblemIdOrderByCreatedAtDesc(String problemId);

    // Find jobs by status
    List<SubmissionJob> findByStatus(SubmissionJob.JobStatus status);

    // Find pending jobs ordered by creation time (FIFO)
    List<SubmissionJob> findByStatusOrderByCreatedAtAsc(SubmissionJob.JobStatus status);

    // Find jobs by user and problem
    List<SubmissionJob> findByUserIdAndProblemIdOrderByCreatedAtDesc(String userId, String problemId);

    // Count jobs by status
    long countByStatus(SubmissionJob.JobStatus status);

    // Count jobs by user and status
    long countByUserIdAndStatus(String userId, SubmissionJob.JobStatus status);

    // Find recent jobs for observability
    @Query(value = "{'createdAt': {$gte: ?0}}", sort = "{'createdAt': -1}")
    List<SubmissionJob> findRecentJobs(java.util.Date since);
}