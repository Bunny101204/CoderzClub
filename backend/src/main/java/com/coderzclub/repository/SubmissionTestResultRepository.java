package com.coderzclub.repository;

import com.coderzclub.model.SubmissionTestResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface SubmissionTestResultRepository extends MongoRepository<SubmissionTestResult, String> {
    List<SubmissionTestResult> findByJobIdOrderByTestcaseIndexAsc(String jobId);
    long countByJobId(String jobId);
}