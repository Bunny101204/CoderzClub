package com.coderzclub.queue;

import com.coderzclub.config.SubmissionQueueProperties;
import com.coderzclub.repository.SubmissionOutboxRepository;
import com.coderzclub.service.LocalOutboxPublisher;
import com.coderzclub.service.OperationalMetrics;
import com.coderzclub.service.OutboxPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class QueueTransportProfileTest {

    private ApplicationContextRunner runner(String... profiles) {
        return new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profiles))
            .withBean(SubmissionOutboxRepository.class, () -> mock(SubmissionOutboxRepository.class))
            .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class))
            .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
            .withBean(RabbitAdmin.class, () -> mock(RabbitAdmin.class))
            .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
            .withBean(SubmissionQueueProperties.class, SubmissionQueueProperties::new)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(OperationalMetrics.class, () -> new OperationalMetrics(new SimpleMeterRegistry()))
            .withUserConfiguration(
                LocalOutboxPublisher.class,
                OutboxPublisher.class,
                InMemorySubmissionQueuePublisher.class,
                InMemorySubmissionQueueConsumer.class,
                RabbitSubmissionQueuePublisher.class,
                RabbitSubmissionQueueConsumer.class
            );
    }

    @Test
    void localPlusWorkerUsesInMemoryOutboxNotRabbitPublisher() {
        runner("local", "worker").run(ctx -> {
            assertTrue(ctx.containsBean("localOutboxPublisher"));
            assertTrue(ctx.containsBean("inMemorySubmissionQueuePublisher"));
            assertTrue(ctx.containsBean("inMemorySubmissionQueueConsumer"));
            assertFalse(ctx.containsBean("outboxPublisher"));
            assertFalse(ctx.containsBean("rabbitSubmissionQueuePublisher"));
            assertFalse(ctx.containsBean("rabbitSubmissionQueueConsumer"));
        });
    }

    @Test
    void localWithoutWorkerCannotConsumeSharedOutboxEvents() {
        runner("local").run(ctx -> {
            assertFalse(ctx.containsBean("localOutboxPublisher"));
            assertTrue(ctx.containsBean("inMemorySubmissionQueuePublisher"));
            assertFalse(ctx.containsBean("outboxPublisher"));
        });
    }

    @Test
    void nonLocalKeepsRabbitOutboxPublisher() {
        runner("api").run(ctx -> {
            assertFalse(ctx.containsBean("localOutboxPublisher"));
            assertTrue(ctx.containsBean("outboxPublisher"));
            assertTrue(ctx.containsBean("rabbitSubmissionQueuePublisher"));
        });
    }

    @Test
    void workerWithoutLocalUsesRabbitConsumer() {
        runner("worker").run(ctx -> {
            assertFalse(ctx.containsBean("localOutboxPublisher"));
            assertTrue(ctx.containsBean("outboxPublisher"));
            assertTrue(ctx.containsBean("rabbitSubmissionQueueConsumer"));
        });
    }
}
