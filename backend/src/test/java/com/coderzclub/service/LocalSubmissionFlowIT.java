package com.coderzclub.service;

import com.coderzclub.config.JwtUtil;
import com.coderzclub.config.SubmissionQueueProperties;
import com.coderzclub.model.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real HTTP, Mongo transactions, Redis limits, Rabbit delivery, worker and rewards.
 * Only external code execution is stubbed. All infrastructure addresses are local.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("worker")
@DirtiesContext
@EnabledIfEnvironmentVariable(named = "CODERZCLUB_LOCAL_FLOW", matches = "1")
class LocalSubmissionFlowIT {
    private static final String RUN = UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void localInfrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.data.mongodb.uri", () ->
            "mongodb://127.0.0.1:27017/coderzclub_flow_" + RUN + "?replicaSet=rstest");
        properties.add("spring.data.mongodb.auto-index-creation", () -> "true");
        properties.add("spring.data.redis.host", () -> "127.0.0.1");
        properties.add("spring.data.redis.port", () -> "6379");
        properties.add("spring.data.redis.password", () -> "");
        properties.add("spring.data.redis.database", () -> "14");
        properties.add("spring.rabbitmq.host", () -> "127.0.0.1");
        properties.add("spring.rabbitmq.port", () -> "5672");
        properties.add("spring.rabbitmq.username", () -> "guest");
        properties.add("spring.rabbitmq.password", () -> "guest");
        properties.add("spring.rabbitmq.virtual-host", () -> "/");
        properties.add("spring.rabbitmq.publisher-confirm-type", () -> "correlated");
        properties.add("spring.rabbitmq.publisher-returns", () -> "true");
        properties.add("judge0.provider.base-url", () -> "http://127.0.0.1:2358/submissions");
        properties.add("jwt.secret", () -> "local-validation-only-key-".repeat(4));
        for (String property : List.of("name", "exchange", "retry-name", "dlq-name")) {
            properties.add("submission.queue." + property, () -> "validation." + RUN + "." + property);
            properties.add("contest.run.queue." + property, () -> "validation.contest." + RUN + "." + property);
        }
    }

    @Autowired TestRestTemplate http;
    @Autowired MongoTemplate mongo;
    @Autowired JwtUtil jwt;
    @Autowired SubmissionLimitService limits;
    @Autowired RabbitTemplate rabbit;
    @Autowired org.springframework.amqp.rabbit.core.RabbitAdmin rabbitAdmin;
    @Autowired SubmissionQueueProperties queue;
    @Autowired MeterRegistry metrics;
    @MockBean Judge0ExecutionService execution;

    @Test
    void admissionReplayDeliveryAndRedeliveryKeepOneJobSubmissionAndReward() {
        User user = new User();
        user.setId("flow-user-" + RUN);
        user.setUsername("flow-" + RUN);
        user.setEmail("flow-" + RUN + "@example.test");
        user.setPasswordHash("unused-local-password-hash");
        user.setRole("USER");
        user.setEmailVerified(true);
        user.setProfileComplete(true);
        mongo.insert(user);
        Problem problem = new Problem();
        problem.setId("flow-problem-" + RUN);
        problem.setTitle("Local validation only");
        problem.setDifficulty("EASY");
        problem.setPoints(25);
        problem.setAvailability(ProblemAvailability.PUBLIC);
        problem.setPublicTestCases(List.of(new TestCase("1", "1")));
        problem.setHiddenTestCases(List.of());
        mongo.insert(problem);

        SubmissionJob.TestResult result = new SubmissionJob.TestResult();
        result.setPassed(true);
        result.setInput("1");
        result.setExpectedOutput("1");
        result.setActualOutput("1");
        result.setRuntime(10L);
        result.setMemory(1024L);
        ExecutionOutcome outcome = new ExecutionOutcome();
        outcome.setResults(List.of(result));
        outcome.setConfiguredMode(ExecutionMode.STANDARD_PER_CASE);
        outcome.setExecutionModeUsed(ExecutionMode.STANDARD_PER_CASE);
        outcome.setLogicalTestcases(1);
        outcome.setProviderExecutions(1);
        when(execution.executeTestCases(anyString(), eq(62), anyList(), anyList(),
            eq(ExecutionMode.STANDARD_PER_CASE), anyString())).thenReturn(outcome);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.generateToken(user.getId(), user.getUsername(), "USER"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "flow-attempt-" + RUN);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of(
            "problemId", problem.getId(), "code", "class Main { public static void main(String[] a) { System.out.println(1); } }",
            "languageId", 62, "language", "Java"), headers);
        ResponseEntity<Map> accepted = http.postForEntity("/api/submission-jobs", request, Map.class);
        assertEquals(HttpStatus.ACCEPTED, accepted.getStatusCode(), String.valueOf(accepted.getBody()));
        String jobId = (String) accepted.getBody().get("jobId");
        assertNotNull(jobId);
        ResponseEntity<Map> replay = http.postForEntity("/api/submission-jobs", request, Map.class);
        assertEquals(HttpStatus.ACCEPTED, replay.getStatusCode(), String.valueOf(replay.getBody()));
        assertEquals(jobId, replay.getBody().get("jobId"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            SubmissionJob job = mongo.findById(jobId, SubmissionJob.class);
            assertEquals(SubmissionJob.JobStatus.COMPLETED, job.getStatus(), job.getLastError());
            assertEquals("ACCEPTED", job.getFinalResult());
            assertEquals(1, mongo.count(Query.query(Criteria.where("submissionJobId").is(jobId)), Submission.class));
            assertEquals(25, mongo.findById(user.getId(), User.class).getTotalPoints());
            assertEquals(1, mongo.count(Query.query(Criteria.where("userId").is(user.getId())), UserSolvedProblem.class));
            assertEquals(1, mongo.count(Query.query(Criteria.where("aggregateId").is(jobId)
                .and("status").is(SubmissionOutboxEvent.Status.PUBLISHED)), SubmissionOutboxEvent.class));
        });
        ResponseEntity<Map> status = http.exchange("/api/submission-jobs/" + jobId, HttpMethod.GET,
            new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, status.getStatusCode());
        assertEquals("COMPLETED", status.getBody().get("status"));
        assertEquals(99, limits.getRemainingDailySubmissions(user.getId()));
        assertEquals(49, limits.getRemainingProblemSubmissions(user.getId(), problem.getId()));

        double duplicatesBefore = duplicateDeliveries();
        rabbit.convertAndSend(queue.getExchange(), queue.getRoutingKey(), jobId);
        rabbit.convertAndSend(queue.getExchange(), queue.getRoutingKey(), jobId);
        await().atMost(Duration.ofSeconds(10)).until(() -> duplicateDeliveries() >= duplicatesBefore + 2);
        assertEquals(1, mongo.count(Query.query(Criteria.where("userId").is(user.getId())), SubmissionJob.class));
        assertEquals(1, mongo.count(Query.query(Criteria.where("submissionJobId").is(jobId)), Submission.class));
        assertEquals(25, mongo.findById(user.getId(), User.class).getTotalPoints());
        assertEquals(1, mongo.findById(user.getId(), User.class).getProblemsSolved());
        verify(execution, times(1)).executeTestCases(anyString(), eq(62), anyList(), anyList(),
            eq(ExecutionMode.STANDARD_PER_CASE), anyString());

        // A different key is a legitimate new attempt, but solving the same
        // problem again must not award points a second time.
        HttpHeaders nextHeaders = new HttpHeaders();
        nextHeaders.putAll(headers);
        nextHeaders.set("Idempotency-Key", "flow-second-" + RUN);
        var nextRequest = new HttpEntity<>(request.getBody(), nextHeaders);
        var nextJob = new java.util.concurrent.atomic.AtomicReference<String>();
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<Map> next = http.postForEntity("/api/submission-jobs", nextRequest, Map.class);
            assertEquals(HttpStatus.ACCEPTED, next.getStatusCode(), String.valueOf(next.getBody()));
            nextJob.set((String) next.getBody().get("jobId"));
        });
        assertNotEquals(jobId, nextJob.get());
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertEquals(SubmissionJob.JobStatus.COMPLETED, mongo.findById(nextJob.get(), SubmissionJob.class).getStatus()));
        assertEquals(2, mongo.count(Query.query(Criteria.where("userId").is(user.getId())), Submission.class));
        assertEquals(25, mongo.findById(user.getId(), User.class).getTotalPoints());
        assertEquals(1, mongo.count(Query.query(Criteria.where("userId").is(user.getId())), UserSolvedProblem.class));
        assertEquals(98, limits.getRemainingDailySubmissions(user.getId()));
        assertEquals(48, limits.getRemainingProblemSubmissions(user.getId(), problem.getId()));
        verify(execution, times(2)).executeTestCases(anyString(), eq(62), anyList(), anyList(),
            eq(ExecutionMode.STANDARD_PER_CASE), anyString());
    }

    @Test
    void realBrokerReturnRetriesAfterRoutingIsRestored() {
        // Separate database/exchange keep the scheduled application publisher
        // and worker from racing this deliberately unroutable fixture.
        try (var client = com.mongodb.client.MongoClients.create("mongodb://127.0.0.1:27017/?replicaSet=rstest")) {
            MongoTemplate isolated = new MongoTemplate(client, "coderzclub_outbox_" + RUN);
            var properties = new SubmissionQueueProperties();
            properties.setExchange("validation.unroutable." + RUN);
            properties.setName("validation.restored." + RUN);
            var exchange = new org.springframework.amqp.core.DirectExchange(properties.getExchange());
            rabbitAdmin.declareExchange(exchange);
            SubmissionOutboxEvent event = new SubmissionOutboxEvent();
            event.setId("unroutable");
            event.setAggregateId("routing-probe-job");
            event.setPayload("routing-probe-job");
            isolated.insert(event);
            OutboxPublisher publisher = new OutboxPublisher(isolated, null, rabbit, properties, metrics);
            publisher.publishDueEvents();
            SubmissionOutboxEvent failed = isolated.findById(event.getId(), SubmissionOutboxEvent.class);
            assertEquals(SubmissionOutboxEvent.Status.FAILED, failed.getStatus());
            assertNull(failed.getPublishedAt());
            assertNotNull(failed.getNextAttemptAt());

            var restored = new org.springframework.amqp.core.Queue(properties.getName());
            rabbitAdmin.declareQueue(restored);
            rabbitAdmin.declareBinding(org.springframework.amqp.core.BindingBuilder.bind(restored)
                .to(exchange).with(properties.getRoutingKey()));
            isolated.updateFirst(Query.query(Criteria.where("_id").is(event.getId())),
                new org.springframework.data.mongodb.core.query.Update().set("nextAttemptAt", new java.util.Date(0)),
                SubmissionOutboxEvent.class);
            publisher.publishDueEvents();
            assertEquals("routing-probe-job", rabbit.receiveAndConvert(restored.getName(), 5000));
            SubmissionOutboxEvent published = isolated.findById(event.getId(), SubmissionOutboxEvent.class);
            assertEquals(SubmissionOutboxEvent.Status.PUBLISHED, published.getStatus());
            assertEquals(2, published.getAttemptCount());
            assertNull(rabbit.receiveAndConvert(restored.getName()));
        }
    }

    private double duplicateDeliveries() {
        var counter = metrics.find("submission.queue.consumer").tag("outcome", "duplicate_or_unclaimable").counter();
        return counter == null ? 0 : counter.count();
    }
}
