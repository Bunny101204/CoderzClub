package com.coderzclub.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        System.out.println("=== REQUEST LOGGING ===");
        System.out.println("Method: " + httpRequest.getMethod());
        System.out.println("URI: " + httpRequest.getRequestURI());
        System.out.println("Remote Address: " + httpRequest.getRemoteAddr());
        System.out.println("User Agent: " + httpRequest.getHeader("User-Agent"));
        System.out.println("Origin: " + httpRequest.getHeader("Origin"));
        System.out.println("Content-Type: " + httpRequest.getContentType());
        
        try {
            chain.doFilter(request, response);
            System.out.println("Response Status: " + httpResponse.getStatus());
        } catch (Exception e) {
            System.out.println("Exception in filter: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
} 