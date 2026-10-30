package com.coderzclub.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {
    @Test
    void unexpectedErrorsReturnGenericClientMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> response = handler.handleGenericException(
            new RuntimeException("mongodb://user:pass@localhost"));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("An unexpected error occurred", response.getBody().get("error"));
    }

    @Test
    void missingRouteReturns404WithoutGeneric500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> response = handler.handleMissingRoute(
            new NoResourceFoundException(HttpMethod.GET, "/api/does-not-exist"));
        assertEquals(404, response.getStatusCode().value());
        assertEquals("Not found", response.getBody().get("error"));
    }

    @Test
    void unreadableBodyReturnsSafe400() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> response = handler.handleHttpMessageNotReadable(
            new org.springframework.http.converter.HttpMessageNotReadableException("mongodb://secret@host"));
        assertEquals(400, response.getStatusCode().value());
        assertEquals("Request body is invalid", response.getBody().get("error"));
    }
}
