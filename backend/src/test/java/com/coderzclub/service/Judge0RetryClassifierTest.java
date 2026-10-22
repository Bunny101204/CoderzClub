package com.coderzclub.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Judge0RetryClassifierTest {

    @Test
    void onlyAllowlistedPreSendFailuresAreSafe() {
        assertEquals(Judge0RetryClassifier.Decision.SAFE_PRE_SEND,
            Judge0RetryClassifier.classifyException(new ConnectException("connection refused")).decision());
        assertEquals(Judge0RetryClassifier.Decision.SAFE_PRE_SEND,
            Judge0RetryClassifier.classifyException(new HttpConnectTimeoutException("connect timeout")).decision());
        assertEquals(Judge0RetryClassifier.Decision.SAFE_PRE_SEND,
            Judge0RetryClassifier.classifyException(new UnknownHostException("judge0.example")).decision());
        assertEquals(Judge0RetryClassifier.Decision.SAFE_PRE_SEND,
            Judge0RetryClassifier.classifyException(new UnresolvedAddressException()).decision());
    }

    @Test
    void readTimeoutSslResetAndUnknownAreAmbiguous() {
        assertEquals(Judge0RetryClassifier.Decision.DO_NOT_RETRY,
            Judge0RetryClassifier.classifyException(new HttpTimeoutException("read")).decision());
        assertEquals(Judge0RetryClassifier.Kind.READ_TIMEOUT,
            Judge0RetryClassifier.classifyException(new HttpTimeoutException("read")).kind());
        assertEquals(Judge0RetryClassifier.Decision.DO_NOT_RETRY,
            Judge0RetryClassifier.classifyException(new SocketException("Connection reset")).decision());
        assertEquals(Judge0RetryClassifier.Decision.DO_NOT_RETRY,
            Judge0RetryClassifier.classifyException(new SSLHandshakeException("handshake")).decision());
        assertEquals(Judge0RetryClassifier.Decision.DO_NOT_RETRY,
            Judge0RetryClassifier.classifyException(new SSLException("ssl")).decision());
        assertEquals(Judge0RetryClassifier.Decision.DO_NOT_RETRY,
            Judge0RetryClassifier.classifyException(new IOException("generic")).decision());
        assertEquals(Judge0RetryClassifier.Decision.DO_NOT_RETRY,
            Judge0RetryClassifier.classifyException(new RuntimeException("unknown")).decision());
    }

    @Test
    void httpCapacityAndServerErrorsAreNotRetried() {
        assertEquals(Judge0RetryClassifier.Decision.DO_NOT_RETRY,
            Judge0RetryClassifier.classifyHttpStatus(429).decision());
        assertEquals(Judge0RetryClassifier.Kind.HTTP_429, Judge0RetryClassifier.classifyHttpStatus(429).kind());
        assertEquals(Judge0RetryClassifier.Kind.HTTP_5XX, Judge0RetryClassifier.classifyHttpStatus(503).kind());
        assertEquals(Judge0RetryClassifier.Decision.DO_NOT_RETRY,
            Judge0RetryClassifier.classifyHttpStatus(500).decision());
    }
}
