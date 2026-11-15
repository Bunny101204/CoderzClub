package com.coderzclub.service;

/**
 * Raised when outbound email cannot be delivered. Messages must never include
 * SMTP credentials or provider response bodies that may contain secrets.
 */
public class MailDeliveryException extends RuntimeException {
    public MailDeliveryException(String message) {
        super(message);
    }

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
