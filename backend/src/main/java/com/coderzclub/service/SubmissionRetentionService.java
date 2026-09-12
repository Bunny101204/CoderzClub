package com.coderzclub.service;

import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.SubmissionJobSummary;
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
    @Value("${submission.retention.summary-days:365}") private int summaryDays;

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
        if (dryRun) {
            cleanupCounter.increment(count);
            return;
        }

        Date summaryExpiry = new Date(System.currentTimeMillis()
            + Math.max(1, summaryDays) * 86400000L);
        java.util.List<String> archivedIds = new java.util.ArrayList<>();
        for (SubmissionJob job : mongoTemplate.find(query, SubmissionJob.class)) {
            SubmissionJobSummary summary = new SubmissionJobSummary();
            summary.setJobId(job.getId());
            summary.setUserId(job.getUserId());
            summary.setProblemId(job.getProblemId());
            summary.setStatus(job.getStatus());
            summary.setFinalResult(job.getFinalResult());
            summary.setTotalRuntime(job.getTotalRuntime());
            summary.setTotalMemory(job.getTotalMemory());
            summary.setCompletedTests(job.getCompletedTests());
            summary.setTotalTests(job.getTotalTests());
            summary.setAttemptCount(job.getAttemptCount());
            summary.setCreatedAt(job.getCreatedAt());
            summary.setCompletedAt(job.getCompletedAt());
            summary.setExpireAt(summaryExpiry);
            mongoTemplate.save(summary);
            archivedIds.add(job.getId());
        }
        if (!archivedIds.isEmpty()) {
            mongoTemplate.remove(Query.query(Criteria.where("_id").in(archivedIds)), SubmissionJob.class);
        }
        cleanupCounter.increment(archivedIds.size());
    }
}