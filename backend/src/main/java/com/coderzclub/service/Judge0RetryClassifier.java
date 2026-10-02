package com.coderzclub.service;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;

/**
 * Classifies Judge0 POST failures for retry safety.
 * <p>
 * A wait=true execution POST is retried only for an explicit pre-send allowlist.
 * Unknown transport failures default to AMBIGUOUS (exactly one POST).
 * <p>
 * Safe-retry allowlist (Java {@code HttpClient} semantics):
 * <ul>
 *   <li>{@link HttpConnectTimeoutException} — connection establishment timed out;
 *       the HTTP request is not sent until the connection exists.</li>
 *   <li>{@link ConnectException} — TCP connect failed (typical: connection refused).
 *       Not {@link java.net.SocketException} in general, and not connection reset.</li>
 *   <li>{@link UnknownHostException} — DNS lookup failed before any TCP connect
 *       ({@code InetAddress} resolution).</li>
 *   <li>{@link UnresolvedAddressException} — NIO equivalent of unresolved host,
 *       also before connect.</li>
 * </ul>
 * Not allowlisted (AMBIGUOUS): {@code SSLException}/{@code SSLHandshakeException}
 * (TCP may already be up), {@code SocketException} connection reset (request may
 * have been written), generic {@code IOException}, and unrecognized types.
 */
public final class Judge0RetryClassifier {

    public enum Kind {
        CONNECT_FAILURE,
        DNS_FAILURE,
        READ_TIMEOUT,
        CONNECTION_RESET,
        HTTP_429,
        HTTP_5XX,
        MALFORMED_RESPONSE,
        AUTH_FAILURE,
        CANCELLED,
        PROVIDER_ERROR
    }

    public enum Decision {
        /** Proven pre-send; a limited connect retry is allowed. */
        SAFE_PRE_SEND,
        /** Provider/proxy may have accepted the POST; never retry the same execution. */
        DO_NOT_RETRY
    }

    public record Classification(Kind kind, Decision decision, String reason) {}

    private Judge0RetryClassifier() {}

    public static Classification classifyException(Throwable error) {
        if (error instanceof InterruptedException) {
            return new Classification(Kind.CANCELLED, Decision.DO_NOT_RETRY, "interrupted");
        }
        for (Throwable current = error; current != null; current = current.getCause()) {
            Class<?> type = current.getClass();
            if (type == HttpConnectTimeoutException.class) {
                return new Classification(Kind.CONNECT_FAILURE, Decision.SAFE_PRE_SEND, "HttpConnectTimeoutException");
            }
            if (type == ConnectException.class) {
                return new Classification(Kind.CONNECT_FAILURE, Decision.SAFE_PRE_SEND, "ConnectException");
            }
            if (type == UnknownHostException.class) {
                return new Classification(Kind.DNS_FAILURE, Decision.SAFE_PRE_SEND, "UnknownHostException");
            }
            if (type == UnresolvedAddressException.class) {
                return new Classification(Kind.DNS_FAILURE, Decision.SAFE_PRE_SEND, "UnresolvedAddressException");
            }
        }
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof HttpTimeoutException) {
                return new Classification(Kind.READ_TIMEOUT, Decision.DO_NOT_RETRY, "http_request_timeout");
            }
            if (current instanceof JsonProcessingException) {
                return new Classification(Kind.MALFORMED_RESPONSE, Decision.DO_NOT_RETRY, "malformed_json");
            }
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (current instanceof java.net.SocketException && message.contains("reset")) {
                return new Classification(Kind.CONNECTION_RESET, Decision.DO_NOT_RETRY, "connection_reset");
            }
        }
        return new Classification(Kind.PROVIDER_ERROR, Decision.DO_NOT_RETRY,
            error == null ? "unknown" : error.getClass().getSimpleName());
    }

    public static Classification classifyHttpStatus(int status) {
        if (status == 429) {
            return new Classification(Kind.HTTP_429, Decision.DO_NOT_RETRY, "http_429");
        }
        if (status == 401 || status == 403) {
            return new Classification(Kind.AUTH_FAILURE, Decision.DO_NOT_RETRY, "http_" + status);
        }
        if (status >= 500) {
            return new Classification(Kind.HTTP_5XX, Decision.DO_NOT_RETRY, "http_" + status);
        }
        if (status >= 400) {
            return new Classification(Kind.PROVIDER_ERROR, Decision.DO_NOT_RETRY, "http_" + status);
        }
        return null;
    }
}
