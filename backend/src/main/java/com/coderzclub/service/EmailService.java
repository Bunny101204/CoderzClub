package com.coderzclub.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.email.from:no-reply@coderzclub.com}")
    private String fromAddress;

    @Value("${app.backend.url:http://localhost:8080}")
    private String backendUrl;

    public void sendVerificationEmail(String recipientEmail, String token) {
        String subject = "Verify your CoderzClub account";
        String verificationLink = String.format("%s/api/confirm-email?token=%s", backendUrl, token);
        String text = "Welcome to CoderzClub!\n\n" +
                "Please verify your email address by clicking the link below:\n" +
                verificationLink + "\n\n" +
                "If you did not create this account, please ignore this email.";
        sendEmail(recipientEmail, subject, text);
    }

    public void sendPasswordResetEmail(String recipientEmail, String token) {
        String subject = "Reset your CoderzClub password";
        String resetLink = String.format("%s/api/password-reset-confirm?token=%s", backendUrl, token);
        String text = "A request to reset your password was received.\n\n" +
                "Use the link below to reset your password:\n" +
                resetLink + "\n\n" +
                "If you did not request a password reset, ignore this email.";
        sendEmail(recipientEmail, subject, text);
    }

    private void sendEmail(String to, String subject, String text) {
        if (mailSender == null) {
            throw new IllegalStateException("Email sender is not configured. Set SMTP properties in application.properties or environment variables.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new RuntimeException("Failed to send email: " + ex.getMessage(), ex);
        }
    }
}
