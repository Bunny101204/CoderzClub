package com.coderzclub.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RequestLoggingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        logger.debug("Request: method={}, uri={}, remoteAddr={}, userAgent={}, origin={}, contentType={}",
                httpRequest.getMethod(),
                httpRequest.getRequestURI(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                httpRequest.getHeader("Origin"),
                httpRequest.getContentType());
        
        try {
            chain.doFilter(request, response);
            logger.debug("Response Status: {}", httpResponse.getStatus());
        } catch (Exception e) {
            logger.error("Exception in RequestLoggingFilter", e);
            throw e;
        }
    }
} 