package com.coderzclub.service;

import com.coderzclub.config.CorsOrigins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final CorsOrigins corsOrigins;
    private final boolean mailEnabled;
    private final String mailHost;
    private final String fromAddress;
    private final String frontendUrl;

    public EmailService(
        ObjectProvider<JavaMailSender> mailSender,
        CorsOrigins corsOrigins,
        @Value("${spring.mail.enabled:false}") boolean mailEnabled,
        @Value("${spring.mail.host:}") String mailHost,
        @Value("${app.email.from:}") String fromAddress,
        @Value("${app.frontend.url:${app.backend.url:http://localhost:8080}}") String frontendUrl
    ) {
        this.mailSender = mailSender;
        this.corsOrigins = corsOrigins;
        this.mailEnabled = mailEnabled;
        this.mailHost = mailHost == null ? "" : mailHost.trim();
        this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
        this.frontendUrl = frontendUrl;
    }

    public boolean isConfigured() {
        return mailEnabled
            && StringUtils.hasText(mailHost)
            && mailSender.getIfAvailable() != null
            && StringUtils.hasText(resolvedFromAddress());
    }

    public void sendVerificationEmail(String recipientEmail, String token) {
        sendVerificationEmail(recipientEmail, token, null, EmailBrandContext.platform());
    }

    public void sendVerificationEmail(String recipientEmail, String token, String publicFrontendUrl) {
        sendVerificationEmail(recipientEmail, token, publicFrontendUrl, EmailBrandContext.platform());
    }

    public void sendVerificationEmail(
        String recipientEmail,
        String token,
        String publicFrontendUrl,
        EmailBrandContext brand
    ) {
        EmailBrandContext safe = brand == null ? EmailBrandContext.platform() : brand;
        String subject = "Verify your " + safe.productName() + " account";
        String verificationBaseUrl = VerificationLinks.frontendBase(
            publicFrontendUrl,
            corsOrigins == null ? List.of() : corsOrigins.allowed(),
            frontendUrl
        );
        String verificationLink = String.format("%s/auth?verifyEmailToken=%s", verificationBaseUrl, token);
        String text = "Welcome to " + safe.productName() + "!\n\n"
            + "Please verify your email address by clicking the link below:\n"
            + verificationLink + "\n\n"
            + "If you did not create this account, please ignore this email.\n\n"
            + "— " + safe.fromDisplayName();
        sendEmail(recipientEmail, subject, text, safe.fromDisplayName());
    }

    public void sendPasswordResetEmail(String recipientEmail, String token) {
        sendPasswordResetEmail(recipientEmail, token, null, EmailBrandContext.platform());
    }

    public void sendPasswordResetEmail(String recipientEmail, String token, String publicFrontendUrl) {
        sendPasswordResetEmail(recipientEmail, token, publicFrontendUrl, EmailBrandContext.platform());
    }

    public void sendPasswordResetEmail(
        String recipientEmail,
        String token,
        String publicFrontendUrl,
        EmailBrandContext brand
    ) {
        EmailBrandContext safe = brand == null ? EmailBrandContext.platform() : brand;
        String subject = "Reset your " + safe.productName() + " password";
        String resetBaseUrl = VerificationLinks.frontendBase(
            publicFrontendUrl,
            corsOrigins == null ? List.of() : corsOrigins.allowed(),
            frontendUrl
        );
        String resetLink = String.format("%s/auth?resetPasswordToken=%s", resetBaseUrl, token);
        String text = "A request to reset your password was received.\n\n"
            + "Use the link below to choose a new password:\n"
            + resetLink + "\n\n"
            + "If you did not request a password reset, ignore this email.\n\n"
            + "— " + safe.fromDisplayName();
        sendEmail(recipientEmail, subject, text, safe.fromDisplayName());
    }

    String passwordResetLinkForTests(String token, String publicFrontendUrl) {
        String resetBaseUrl = VerificationLinks.frontendBase(
            publicFrontendUrl,
            corsOrigins == null ? List.of() : corsOrigins.allowed(),
            frontendUrl
        );
        return String.format("%s/auth?resetPasswordToken=%s", resetBaseUrl, token);
    }

    private String resolvedFromAddress() {
        if (StringUtils.hasText(fromAddress)) {
            return fromAddress;
        }
        return "";
    }

    private void sendEmail(String to, String subject, String text, String fromDisplayName) {
        if (!mailEnabled) {
            throw new MailDeliveryException(
                "Email delivery is disabled. Set SPRING_MAIL_ENABLED=true and SMTP settings on the API.");
        }
        if (!StringUtils.hasText(mailHost)) {
            throw new MailDeliveryException("Email delivery is not configured. Set SPRING_MAIL_HOST on the API.");
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new MailDeliveryException(
                "Email sender bean is unavailable. Configure SMTP settings on the API.");
        }
        String from = resolvedFromAddress();
        if (!StringUtils.hasText(from)) {
            throw new MailDeliveryException(
                "Email sender identity is missing. Set APP_EMAIL_FROM or SPRING_MAIL_USERNAME.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        // Plain mailbox address only — display branding stays in the body/subject.
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
        } catch (MailException ex) {
            // Never log provider bodies, credentials, or recipient tokens.
            logger.warn("Outbound email failed type={}", ex.getClass().getSimpleName());
            throw new MailDeliveryException("Failed to send email", ex);
        }
    }
}
