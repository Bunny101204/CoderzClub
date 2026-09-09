package com.coderzclub.service;

import com.coderzclub.model.SubmissionOutboxEvent;
import com.coderzclub.queue.SubmissionQueuePublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@Profile("local")
public class LocalOutboxPublisher {
    private final SubmissionQueuePublisher publisher;
    private final com.coderzclub.repository.SubmissionOutboxRepository repository;

    public LocalOutboxPublisher(SubmissionQueuePublisher publisher,
                                com.coderzclub.repository.SubmissionOutboxRepository repository) {
        this.publisher = publisher;
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${submission.outbox.poll-ms:1000}")
    public void publishDueEvents() {
        repository.findAll().stream()
            .filter(event -> event.getStatus() != SubmissionOutboxEvent.Status.PUBLISHED)
            .filter(event -> event.getNextAttemptAt() == null || !event.getNextAttemptAt().after(new Date()))
            .forEach(event -> {
                publisher.publishJob(event.getPayload());
                event.setStatus(SubmissionOutboxEvent.Status.PUBLISHED);
                event.setPublishedAt(new Date());
                repository.save(event);
            });
    }
}