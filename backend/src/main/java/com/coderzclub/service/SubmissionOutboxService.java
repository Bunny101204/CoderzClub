package com.coderzclub.service;

import com.coderzclub.model.SubmissionOutboxEvent;
import com.coderzclub.repository.SubmissionOutboxRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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

    /**
     * One queue event per job. A second call, or a unique-index race, does not insert another row.
     */
    public void ensureJobEvent(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        if (repository.existsByAggregateIdAndEventType(jobId, "SubmissionJobCreated")) {
            return;
        }
        try {
            createJobEvent(jobId);
        } catch (RuntimeException ex) {
            if (!isDuplicate(ex)) {
                throw ex;
            }
        }
    }

    public boolean hasPublishedOrPendingEvent(String jobId) {
        return repository.findFirstByAggregateIdAndStatusIn(jobId, List.of(
            SubmissionOutboxEvent.Status.PENDING,
            SubmissionOutboxEvent.Status.PUBLISHED
        )).isPresent();
    }

    private boolean isDuplicate(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof DuplicateKeyException || current instanceof DataIntegrityViolationException) {
                return true;
            }
            if (current.getClass().getName().contains("DuplicateKey")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}