package com.coderzclub.repository;

import com.coderzclub.model.SubmissionOutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SubmissionOutboxRepository extends MongoRepository<SubmissionOutboxEvent, String> {
    boolean existsByAggregateIdAndEventType(String aggregateId, String eventType);

    Optional<SubmissionOutboxEvent> findFirstByAggregateIdAndStatusIn(String aggregateId,
                                                                        java.util.List<SubmissionOutboxEvent.Status> statuses);
}