package com.coderzclub.service;

import com.coderzclub.dto.SubmissionJobEvent;
import com.coderzclub.model.SubmissionJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SubmissionJobEventService implements MessageListener {
    private static final Logger logger = LoggerFactory.getLogger(SubmissionJobEventService.class);
    private static final long EMITTER_TIMEOUT_MS = 0L;

    private final StringRedisTemplate redis;
    private final RedisMessageListenerContainer container;
    private final ObjectMapper objectMapper;
    private final String channel;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SubmissionJobEventService(StringRedisTemplate redis, RedisConnectionFactory connectionFactory,
                                     ObjectMapper objectMapper,
                                     @Value("${submission.events.channel:coderzclub.submission.events}") String channel) {
        this.redis = redis;
        this.container = new RedisMessageListenerContainer();
        this.container.setConnectionFactory(connectionFactory);
        this.objectMapper = objectMapper;
        this.channel = channel;
    }

    @PostConstruct
    public void startSubscriber() {
        container.addMessageListener(this, new ChannelTopic(channel));
        container.afterPropertiesSet();
        tryStartSubscriber();
    }

    @Scheduled(fixedDelayString = "${submission.events.retry-ms:5000}")
    public void retrySubscriberConnection() {
        if (!container.isRunning()) tryStartSubscriber();
    }

    private void tryStartSubscriber() {
        try {
            container.start();
        } catch (Exception ex) {
            logger.warn("Submission event subscriber unavailable; will retry", ex);
        }
    }

    @PreDestroy
    public void stopSubscriber() {
        if (container.isRunning()) container.stop();
    }

    public void publish(SubmissionJob job) {
        publish(SubmissionJobEvent.from(job));
    }

    public void publish(SubmissionJob job, SubmissionJob.JobStatus status) {
        publish(SubmissionJobEvent.from(job, status));
    }

    public void publish(SubmissionJobEvent event) {
        try {
            redis.convertAndSend(channel, objectMapper.writeValueAsString(event));
        } catch (Exception ex) {
            logger.warn("Unable to publish submission event for job {}", event.getJobId(), ex);
        }
    }

    public SseEmitter register(String jobId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.computeIfAbsent(jobId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable cleanup = () -> remove(jobId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        return emitter;
    }

    public void sendInitial(SseEmitter emitter, SubmissionJob job) throws IOException {
        send(emitter, SubmissionJobEvent.from(job));
    }

    @Scheduled(fixedDelayString = "${submission.events.heartbeat-ms:30000}")
    public void sendHeartbeats() {
        emitters.forEach((jobId, clients) -> clients.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException ex) {
                emitter.completeWithError(ex);
                remove(jobId, emitter);
            }
        }));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SubmissionJobEvent event = objectMapper.readValue(message.getBody(), SubmissionJobEvent.class);
            List<SseEmitter> clients = emitters.getOrDefault(event.getJobId(), new CopyOnWriteArrayList<>());
            for (SseEmitter emitter : clients) {
                try {
                    send(emitter, event);
                    if (isTerminal(event.getStatus())) {
                        emitter.complete();
                        remove(event.getJobId(), emitter);
                    }
                } catch (IOException | IllegalStateException ex) {
                    emitter.completeWithError(ex);
                    remove(event.getJobId(), emitter);
                }
            }
        } catch (Exception ex) {
            logger.warn("Unable to consume submission event", ex);
        }
    }

    private void send(SseEmitter emitter, SubmissionJobEvent event) throws IOException {
        emitter.send(SseEmitter.event().id(event.getEventId()).name("submission-job").data(event));
    }

    private void remove(String jobId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> clients = emitters.get(jobId);
        if (clients != null) {
            clients.remove(emitter);
            if (clients.isEmpty()) emitters.remove(jobId, clients);
        }
    }

    private boolean isTerminal(SubmissionJob.JobStatus status) {
        return status == SubmissionJob.JobStatus.COMPLETED || status == SubmissionJob.JobStatus.FAILED
            || status == SubmissionJob.JobStatus.TIMEOUT || status == SubmissionJob.JobStatus.CANCELLED;
    }
}
