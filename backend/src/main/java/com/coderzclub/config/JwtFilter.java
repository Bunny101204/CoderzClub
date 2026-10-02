package com.coderzclub.config;
import com.coderzclub.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);

    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        logger.debug("JWT Filter processing: {}", requestURI);
        
        if (requestURI.equals("/api/login") || requestURI.equals("/api/register") || requestURI.equals("/api/resend-verification") || requestURI.equals("/api/test") || requestURI.equals("/api/test-password")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        final String authHeader = request.getHeader("Authorization");
        String username = null;
        String jwt = null;

        if (authHeader != null) {
            String trimmedHeader = authHeader.trim();
            if (trimmedHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                jwt = trimmedHeader.substring(7).trim();
                try {
                    username = jwtUtil.extractUsername(jwt);
                } catch (Exception ignored) {
                }
            }
        }

        if (username != null) {
            var existingAuthentication = SecurityContextHolder.getContext().getAuthentication();
            boolean shouldReplaceAuthentication = existingAuthentication == null || existingAuthentication instanceof AnonymousAuthenticationToken;

            if (shouldReplaceAuthentication) {
                try {
                    UserDetails userDetails = userService.loadUserByUsername(username);
                    if (userDetails.isEnabled() && jwtUtil.isTokenValid(jwt, userDetails.getUsername())) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                } catch (UsernameNotFoundException ignored) {
                    logger.debug("JWT Filter: user no longer authenticable");
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
