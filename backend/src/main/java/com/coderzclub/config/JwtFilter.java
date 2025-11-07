package com.coderzclub.config;
import com.coderzclub.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        System.out.println("JWT Filter processing: " + requestURI);
        
        // Skip JWT processing for authentication endpoints
        if (requestURI.equals("/api/login") || requestURI.equals("/api/register") || requestURI.equals("/api/test-password") || requestURI.equals("/api/test")) {
            System.out.println("Skipping JWT processing for: " + requestURI);
            filterChain.doFilter(request, response);
            return;
        }
        
        final String authHeader = request.getHeader("Authorization");
        String username = null;
        String jwt = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            try {
                username = jwtUtil.extractUsername(jwt);
            } catch (Exception ignored) {
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            System.out.println("JWT Filter: Processing request for username: " + username);
            UserDetails userDetails = userService.loadUserByUsername(username);
            System.out.println("JWT Filter: User authorities: " + userDetails.getAuthorities());
            
            if (jwtUtil.isTokenValid(jwt, userDetails.getUsername())) {
                System.out.println("JWT Filter: Token is valid, setting authentication");
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                System.out.println("JWT Filter: Authentication set successfully");
            } else {
                System.out.println("JWT Filter: Token is invalid");
            }
        } else if (username == null) {
            System.out.println("JWT Filter: No username extracted from token");
        } else {
            System.out.println("JWT Filter: Authentication already exists");
        }
        filterChain.doFilter(request, response);
    }
}