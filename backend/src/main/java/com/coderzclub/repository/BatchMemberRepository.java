package com.coderzclub.repository;

import com.coderzclub.model.BatchMember;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchMemberRepository extends MongoRepository<BatchMember, String> {
    boolean existsByBatchIdAndUserId(String batchId, String userId);
    long deleteByBatchIdAndUserId(String batchId, String userId);
    long countByBatchId(String batchId);
}
