package com.coderzclub.service;

import com.coderzclub.model.SubmissionOutboxEvent;
import com.coderzclub.repository.SubmissionOutboxRepository;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class SubmissionOutboxService {
    private final SubmissionOutboxRepository repository;

    public SubmissionOutboxService(SubmissionOutboxRepository repository) {
        this.repository = repository;
    }

    public SubmissionOutboxEvent createJobEvent(String jobId) {
        SubmissionOutboxEvent event = new SubmissionOutboxEvent();
        event.setId(UUID.randomUUID().toString());
        event.setAggregateType("SubmissionJob");
        event.setAggregateId(jobId);
        event.setEventType("SubmissionJobCreated");
        event.setPayload(jobId);
        event.setStatus(SubmissionOutboxEvent.Status.PENDING);
        event.setNextAttemptAt(new Date());
        return repository.save(event);
    }

    public boolean hasPublishedOrPendingEvent(String jobId) {
        return repository.findFirstByAggregateIdAndStatusIn(jobId, List.of(
            SubmissionOutboxEvent.Status.PENDING,
            SubmissionOutboxEvent.Status.PUBLISHED
        )).isPresent();
    }
}