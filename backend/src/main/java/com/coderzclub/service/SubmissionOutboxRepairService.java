package com.coderzclub.service;

import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.SubmissionOutboxEvent;
import com.coderzclub.repository.SubmissionJobRepository;
import com.coderzclub.repository.SubmissionOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class SubmissionOutboxRepairService {
    private static final Logger logger = LoggerFactory.getLogger(SubmissionOutboxRepairService.class);
    private final SubmissionJobRepository jobRepository;
    private final SubmissionOutboxRepository outboxRepository;

    public SubmissionOutboxRepairService(SubmissionJobRepository jobRepository,
                                         SubmissionOutboxRepository outboxRepository) {
        this.jobRepository = jobRepository;
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(fixedDelayString = "${submission.outbox.repair-ms:30000}")
    public void repairOrphanedJobs() {
        jobRepository.findByStatusOrderByCreatedAtAsc(SubmissionJob.JobStatus.QUEUED).stream()
            .limit(100)
            .filter(job -> !outboxRepository.existsByAggregateIdAndEventType(job.getId(), "SubmissionJobCreated"))
            .forEach(job -> {
                SubmissionOutboxEvent event = new SubmissionOutboxEvent();
                event.setId(UUID.randomUUID().toString());
                event.setAggregateType("SubmissionJob");
                event.setAggregateId(job.getId());
                event.setEventType("SubmissionJobCreated");
                event.setPayload(job.getId());
                event.setStatus(SubmissionOutboxEvent.Status.PENDING);
                event.setCreatedAt(new Date());
                event.setNextAttemptAt(new Date());
                try {
                    outboxRepository.save(event);
                    logger.warn("submission_outbox_orphan_repaired jobId={} eventId={}", job.getId(), event.getId());
                } catch (org.springframework.dao.DuplicateKeyException alreadyRepaired) {
                    logger.debug("Submission outbox orphan was repaired concurrently for jobId={}", job.getId());
                }
            });
    }
}