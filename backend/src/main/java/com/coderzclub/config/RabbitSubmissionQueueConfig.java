package com.coderzclub.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Map;

@Configuration
@Profile("!local")
public class RabbitSubmissionQueueConfig {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public DirectExchange submissionExchange(SubmissionQueueProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue submissionQueue(SubmissionQueueProperties properties) {
        return new Queue(properties.getName(), properties.isDurable(), false, false, Map.of(
            "x-dead-letter-exchange", properties.getExchange(),
            "x-dead-letter-routing-key", properties.getDeadLetterRoutingKey(),
            "x-max-length", properties.getMaxLength(),
            "x-overflow", "reject-publish"
        ));
    }

    @Bean
    public Queue submissionRetryQueue(SubmissionQueueProperties properties) {
        return new Queue(properties.getRetryName(), properties.isDurable(), false, false, Map.of(
            "x-message-ttl", properties.getRetryDelayMs(),
            "x-dead-letter-exchange", properties.getExchange(),
            "x-dead-letter-routing-key", properties.getRoutingKey()
        ));
    }

    @Bean
    public Queue submissionDeadLetterQueue(SubmissionQueueProperties properties) {
        return new Queue(properties.getDlqName(), properties.isDurable());
    }

    @Bean
    public Binding submissionBinding(Queue submissionQueue, DirectExchange submissionExchange,
                                     SubmissionQueueProperties properties) {
        return BindingBuilder.bind(submissionQueue).to(submissionExchange).with(properties.getRoutingKey());
    }

    @Bean
    public Binding submissionRetryBinding(Queue submissionRetryQueue, DirectExchange submissionExchange,
                                          SubmissionQueueProperties properties) {
        return BindingBuilder.bind(submissionRetryQueue).to(submissionExchange).with(properties.getRetryRoutingKey());
    }

    @Bean
    public Binding submissionDeadLetterBinding(Queue submissionDeadLetterQueue, DirectExchange submissionExchange,
                                              SubmissionQueueProperties properties) {
        return BindingBuilder.bind(submissionDeadLetterQueue).to(submissionExchange)
            .with(properties.getDeadLetterRoutingKey());
    }
}