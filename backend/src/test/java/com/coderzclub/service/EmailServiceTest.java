package com.coderzclub.service;

import com.coderzclub.config.CorsOrigins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {
    @Mock private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock private JavaMailSender mailSender;
    @Mock private CorsOrigins corsOrigins;

    @Test
    void refusesSendWhenMailDisabled() {
        EmailService service = service(false, "smtp.example.com", "noreply@coderzclub.site");
        MailDeliveryException ex = assertThrows(
            MailDeliveryException.class,
            () -> service.sendVerificationEmail("user@example.com", "token")
        );
        assertTrue(ex.getMessage().contains("disabled"));
        assertFalse(service.isConfigured());
    }

    @Test
    void passwordResetLinkUsesFrontendAuthPath() {
        when(corsOrigins.allowed()).thenReturn(List.of("https://coderzclub.site"));
        EmailService service = service(true, "smtp.example.com", "noreply@coderzclub.site");
        String link = service.passwordResetLinkForTests("abc", "https://coderzclub.site");
        assertEquals("https://coderzclub.site/auth?resetPasswordToken=abc", link);
        assertFalse(link.contains("/api/password-reset-confirm"));
    }

    @Test
    void sendsWhenConfiguredWithoutLeakingSecretsInMessageBody() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(corsOrigins.allowed()).thenReturn(List.of("https://coderzclub.site"));
        EmailService service = service(true, "smtp.example.com", "noreply@coderzclub.site");
        assertTrue(service.isConfigured());
        service.sendPasswordResetEmail("user@example.com", "reset-token", "https://coderzclub.site");
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("noreply@coderzclub.site", message.getFrom());
        assertTrue(message.getText().contains("/auth?resetPasswordToken=reset-token"));
        assertFalse(message.getText().contains("smtp.example.com"));
        assertFalse(message.getText().toLowerCase().contains("smtp"));
        assertFalse(message.getText().contains("SPRING_MAIL_PASSWORD"));
    }

    private EmailService service(boolean enabled, String host, String from) {
        return new EmailService(
            mailSenderProvider,
            corsOrigins,
            enabled,
            host,
            from,
            "https://coderzclub.site"
        );
    }
}
