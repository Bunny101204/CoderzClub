package com.coderzclub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwtFilter, CorsOrigins corsOrigins) throws Exception {
        http
            .cors(cors -> cors.configurationSource(request -> {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(corsOrigins.allowed());
                configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
                configuration.setAllowedHeaders(java.util.List.of("*"));
                configuration.setExposedHeaders(java.util.List.of("Authorization"));
                configuration.setAllowCredentials(true);
                configuration.setMaxAge(3600L);
                return configuration;
            }))
            .headers(headers -> headers
                .contentTypeOptions(content -> {})
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/test").permitAll()
                .requestMatchers("/api/register").permitAll()
                .requestMatchers("/api/login").permitAll()
                .requestMatchers("/api/password-reset-request").permitAll()
                .requestMatchers("/api/password-reset-confirm").permitAll()
                .requestMatchers("/api/confirm-email").permitAll()
                .requestMatchers("/api/resend-verification").permitAll()
                .requestMatchers("/api/test-password").permitAll()
                .requestMatchers("/api/validate-token").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/languages").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/problems").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/problems/{id}/admin").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/problems/{id}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/problems/{problemId}/drafts/{languageId}").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/problems/{problemId}/drafts/{languageId}").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/problems/test").permitAll()
                // Problem mutations: platform ADMIN without org header; org admins with
                // validated X-Organization-Id are authorized in ProblemController.
                .requestMatchers(HttpMethod.POST, "/api/problems").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/problems/{id}/run-public").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/problems/{id}").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/problems/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/problems/{id}/execution").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/problems/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/judge0/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/submission-jobs/*/events/ticket").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/submission-jobs/*/events").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // The management port is internal-only; application/admin APIs remain authenticated.
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/api/bundles/difficulty/**").permitAll() // GET requests for filtering
                .requestMatchers("/api/bundles/category/**").permitAll() // GET requests for filtering
                .requestMatchers("/api/bundles/{id}/problems").permitAll()
                .requestMatchers("/api/bundles/{id}").permitAll() // GET requests for individual bundles
                .requestMatchers("/api/bundles").permitAll() // GET requests for viewing all bundles
                .requestMatchers("/api/users/leaderboard").permitAll()
                // Host→tenant hint only; public resolution stays feature-flagged off by default.
                .requestMatchers(HttpMethod.GET, "/api/public/tenant").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/submissions/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/users/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/contests", "/api/contests/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/contest-runs", "/api/contest-runs/**").hasAnyRole("USER", "ADMIN")
                // Batch routes enforce admin-or-coordinator checks in the controller.
                // Creating a batch and listing every batch stay admin-only there as well.
                .requestMatchers("/api/admin/batches/**").hasAnyRole("USER", "ADMIN")
                // Contest admin routes allow USER so organization admins can act with
                // X-Organization-Id; platform-only paths still require ROLE_ADMIN in-controller.
                .requestMatchers("/api/admin/contests", "/api/admin/contests/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Organization routes enforce platform-admin or membership checks in the service layer.
                .requestMatchers("/api/organizations", "/api/organizations/**").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
            
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
} 