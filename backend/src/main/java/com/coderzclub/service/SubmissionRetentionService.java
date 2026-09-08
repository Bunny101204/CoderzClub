package com.coderzclub.service;

import com.coderzclub.model.SubmissionJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Date;

@Service
public class SubmissionRetentionService {
    private final MongoTemplate mongoTemplate;
    private final Counter cleanupCounter;
    @Value("${submission.retention.cleanup-enabled:false}") private boolean enabled;
    @Value("${submission.retention.dry-run:true}") private boolean dryRun;
    @Value("${submission.retention.job-days:7}") private int jobDays;

    public SubmissionRetentionService(MongoTemplate mongoTemplate, MeterRegistry registry) {
        this.mongoTemplate = mongoTemplate;
        this.cleanupCounter = Counter.builder("submission.retention.cleanup").register(registry);
    }

    @Scheduled(cron = "${submission.retention.cron:0 15 3 * * *}")
    public void cleanup() {
        if (!enabled) return;
        Date cutoff = new Date(System.currentTimeMillis() - Math.max(1, jobDays) * 86400000L);
        Query query = Query.query(Criteria.where("status").in(SubmissionJob.JobStatus.COMPLETED,
            SubmissionJob.JobStatus.FAILED, SubmissionJob.JobStatus.CANCELLED,
            SubmissionJob.JobStatus.TIMEOUT).and("completedAt").lt(cutoff));
        long count = mongoTemplate.count(query, SubmissionJob.class);
        if (!dryRun) mongoTemplate.remove(query, SubmissionJob.class);
        cleanupCounter.increment(count);
    }
}