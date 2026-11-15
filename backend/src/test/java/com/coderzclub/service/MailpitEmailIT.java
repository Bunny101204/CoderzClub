package com.coderzclub.service;

import com.coderzclub.config.CorsOrigins;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Optional live SMTP capture against local Mailpit.
 * Enable with CODERZCLUB_MAILPIT=1 after:
 *   docker compose -p coderzclub-mailpit -f docker-compose.mailpit.yml up -d
 */
class MailpitEmailIT {
    private static final boolean ENABLED = "1".equals(System.getenv("CODERZCLUB_MAILPIT"));

    @Test
    void verificationEmailAppearsInMailpit() throws Exception {
        Assumptions.assumeTrue(ENABLED, "Set CODERZCLUB_MAILPIT=1");
        Assumptions.assumeTrue(mailpitReachable(), "Mailpit UI not reachable on 127.0.0.1:8025");

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(1025);
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "false");
        props.put("mail.smtp.starttls.enable", "false");

        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(sender);

        EmailService emailService = new EmailService(
            provider,
            new CorsOrigins("http://127.0.0.1:4179"),
            true,
            "127.0.0.1",
            "noreply@coderzclub.local",
            "http://127.0.0.1:4179"
        );

        String token = "mailpit-" + UUID.randomUUID();
        String recipient = "student+" + UUID.randomUUID() + "@example.test";
        emailService.sendVerificationEmail(recipient, token, "http://127.0.0.1:4179");

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        boolean found = false;
        for (int attempt = 0; attempt < 20 && !found; attempt++) {
            Thread.sleep(250);
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8025/api/v1/messages"))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            found = response.statusCode() == 200
                && response.body().contains(recipient)
                && response.body().contains("verifyEmailToken=" + token);
        }
        org.junit.jupiter.api.Assertions.assertTrue(found, "Mailpit did not capture verification email");
    }

    private static boolean mailpitReachable() {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:8025/api/v1/info")
                .toURL()
                .openConnection();
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            connection.disconnect();
            return code >= 200 && code < 500;
        } catch (Exception ex) {
            return false;
        }
    }
}
