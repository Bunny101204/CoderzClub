package com.coderzclub.service;

import com.coderzclub.config.Judge0ProviderProperties;
import com.coderzclub.config.SubmissionLimitsConfig;
import com.coderzclub.config.WorkerProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Judge0ExecutionServiceRetryTest {

    private RecordingHttpService service;
    private OperationalMetrics metrics;

    @BeforeEach
    void setUp() {
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        provider.setBaseUrl("http://127.0.0.1:2358/submissions");
        provider.setWait(true);
        provider.setMaxConnectRetries(2);
        service = new RecordingHttpService(provider);
        metrics = new OperationalMetrics(new SimpleMeterRegistry());
        service.configureForTest(new WorkerProperties(), null, null, metrics, new SubmissionLimitsConfig());
    }

    @Test
    void connectFailureMayRetryUntilLimitThenInternalError() {
        service.failure = new ConnectException("connection refused");
        Map<String, Object> response = service.executeProvider("code", 71, "1", 1);
        assertEquals(3, service.posts.get());
        assertEquals(13, statusId(response));
    }

    @Test
    void connectTimeoutIsSafeConnectClass() {
        assertEquals(Judge0RetryClassifier.Decision.SAFE_PRE_SEND,
            Judge0RetryClassifier.classifyException(new HttpConnectTimeoutException("connect timeout")).decision());
    }

    @Test
    void connectTimeoutSendsBoundedConnectRetries() {
        service.failure = new HttpConnectTimeoutException("connect timeout");
        Map<String, Object> response = service.executeProvider("code", 71, "1", 1);
        assertEquals(3, service.posts.get());
        assertEquals(13, statusId(response));
    }

    @Test
    void connectionResetSendsExactlyOnePost() {
        service.failure = new java.net.SocketException("Connection reset");
        assertEquals(1, postCount());
    }

    @Test
    void sslHandshakeSendsExactlyOnePost() {
        service.failure = new javax.net.ssl.SSLHandshakeException("handshake failed");
        assertEquals(1, postCount());
    }

    @Test
    void genericIoExceptionSendsExactlyOnePost() {
        service.failure = new IOException("broken pipe");
        assertEquals(1, postCount());
    }

    @Test
    void unknownExceptionSendsExactlyOnePost() {
        service.failure = new IllegalStateException("unexpected");
        assertEquals(1, postCount());
    }

    @Test
    void readTimeoutSendsExactlyOnePost() {
        service.failure = new HttpTimeoutException("request timed out");
        Map<String, Object> response = service.executeProvider("code", 71, "1", 1);
        assertEquals(1, service.posts.get());
        assertEquals(13, statusId(response));
    }

    @Test
    void http429SendsExactlyOnePost() {
        service.status = 429;
        service.body = "{\"error\":\"rate limited\"}";
        Map<String, Object> response = service.executeProvider("code", 71, "1", 1);
        assertEquals(1, service.posts.get());
        assertEquals(13, statusId(response));
    }

    @Test
    void http5xxSendsExactlyOnePost() {
        for (int status : new int[] {500, 502, 503, 504}) {
            service.posts.set(0);
            service.status = status;
            service.body = "{\"message\":\"unavailable\"}";
            Map<String, Object> response = service.executeProvider("code", 71, "1", 1);
            assertEquals(1, service.posts.get(), "status " + status);
            assertEquals(13, statusId(response));
        }
    }

    @Test
    void malformedTerminalResponseSendsExactlyOnePost() {
        service.status = 200;
        service.body = "not-json";
        Map<String, Object> response = service.executeProvider("code", 71, "1", 1);
        assertEquals(1, service.posts.get());
        assertEquals(13, statusId(response));
    }

    @Test
    void acceptedAndWrongAnswerAreNotTransportRetried() {
        service.status = 200;
        service.body = "{\"status\":{\"id\":3,\"description\":\"Accepted\"},\"stdout\":\"1\"}";
        Map<String, Object> accepted = service.executeProvider("code", 71, "1", 1);
        assertEquals(1, service.posts.get());
        assertEquals(3, statusId(accepted));

        service.posts.set(0);
        service.body = "{\"status\":{\"id\":4,\"description\":\"Wrong Answer\"},\"stdout\":\"2\"}";
        Map<String, Object> wrong = service.executeProvider("code", 71, "1", 1);
        assertEquals(1, service.posts.get());
        assertEquals(4, statusId(wrong));
    }

    private int postCount() {
        Map<String, Object> response = service.executeProvider("code", 71, "1", 1);
        assertEquals(13, statusId(response));
        return service.posts.get();
    }

    @Test
    void semaphoreIsReleasedOnProviderException() {
        service.failure = new HttpTimeoutException("timeout");
        service.executeProviderGuarded("code", 71, "1", 1);
        assertEquals(0, metrics.providerInflight());
        assertTrue(service.globalAvailable());
    }

    @Test
    void semaphoreIsReleasedOnSuccess() {
        service.status = 200;
        service.body = "{\"status\":{\"id\":3,\"description\":\"Accepted\"},\"stdout\":\"1\"}";
        service.executeProviderGuarded("code", 71, "1", 1);
        assertEquals(0, metrics.providerInflight());
        assertTrue(service.globalAvailable());
    }

    private static int statusId(Map<String, Object> response) {
        Object status = response.get("status");
        @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) status;
        return ((Number) map.get("id")).intValue();
    }

    static final class RecordingHttpService extends Judge0ExecutionService {
        final AtomicInteger posts = new AtomicInteger();
        int status = 200;
        String body = "{}";
        Exception failure;

        RecordingHttpService(Judge0ProviderProperties provider) {
            super(provider);
        }

        boolean globalAvailable() {
            return globalPermitsAvailable() == new WorkerProperties().getMaxGlobalJudge0Concurrency();
        }

        @Override
        protected HttpResponse<String> sendProvider(HttpRequest request) throws IOException, InterruptedException {
            posts.incrementAndGet();
            if (failure instanceof InterruptedException interrupted) throw interrupted;
            if (failure instanceof IOException io) throw io;
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure != null) throw new IOException(failure);
            return new StubResponse(status, body);
        }
    }

    private static final class StubResponse implements HttpResponse<String> {
        private final int status;
        private final String body;

        StubResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override public int statusCode() { return status; }
        @Override public String body() { return body; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public java.net.URI uri() { return java.net.URI.create("http://127.0.0.1:2358/submissions"); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
