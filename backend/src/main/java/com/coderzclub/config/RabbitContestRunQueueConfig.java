package com.coderzclub.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Map;

@Configuration
@Profile("!local")
public class RabbitContestRunQueueConfig {

    @Bean
    public DirectExchange contestRunExchange(ContestRunProperties properties) {
        return new DirectExchange(properties.getQueue().getExchange(), true, false);
    }

    @Bean
    public Queue contestRunQueue(ContestRunProperties properties) {
        ContestRunProperties.Queue queue = properties.getQueue();
        return new Queue(queue.getName(), queue.isDurable(), false, false, Map.of(
            "x-dead-letter-exchange", queue.getExchange(),
            "x-dead-letter-routing-key", queue.getDeadLetterRoutingKey(),
            "x-max-length", queue.getMaxLength(),
            "x-overflow", "reject-publish"
        ));
    }

    @Bean
    public Queue contestRunRetryQueue(ContestRunProperties properties) {
        ContestRunProperties.Queue queue = properties.getQueue();
        return new Queue(queue.getRetryName(), queue.isDurable(), false, false, Map.of(
            "x-message-ttl", queue.getRetryDelayMs(),
            "x-dead-letter-exchange", queue.getExchange(),
            "x-dead-letter-routing-key", queue.getRoutingKey()
        ));
    }

    @Bean
    public Queue contestRunDeadLetterQueue(ContestRunProperties properties) {
        ContestRunProperties.Queue queue = properties.getQueue();
        return new Queue(queue.getDlqName(), queue.isDurable());
    }

    @Bean
    public Binding contestRunBinding(Queue contestRunQueue, DirectExchange contestRunExchange,
                                     ContestRunProperties properties) {
        return BindingBuilder.bind(contestRunQueue).to(contestRunExchange)
            .with(properties.getQueue().getRoutingKey());
    }

    @Bean
    public Binding contestRunRetryBinding(Queue contestRunRetryQueue, DirectExchange contestRunExchange,
                                          ContestRunProperties properties) {
        return BindingBuilder.bind(contestRunRetryQueue).to(contestRunExchange)
            .with(properties.getQueue().getRetryRoutingKey());
    }

    @Bean
    public Binding contestRunDeadLetterBinding(Queue contestRunDeadLetterQueue, DirectExchange contestRunExchange,
                                               ContestRunProperties properties) {
        return BindingBuilder.bind(contestRunDeadLetterQueue).to(contestRunExchange)
            .with(properties.getQueue().getDeadLetterRoutingKey());
    }
}
