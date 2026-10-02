package com.coderzclub.repository;

import com.coderzclub.model.BatchAssignment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchAssignmentRepository extends MongoRepository<BatchAssignment, String> {
    boolean existsByBatchIdAndProblemId(String batchId, String problemId);
    long deleteByBatchIdAndProblemId(String batchId, String problemId);
    long countByBatchId(String batchId);
}
