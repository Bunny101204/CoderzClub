package com.coderzclub.controller;

import com.coderzclub.dto.CodeDraftResponse;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.CodeDraftService;
import com.coderzclub.service.DraftConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CodeDraftControllerTest {
    @Mock private CodeDraftService codeDraftService;
    @Mock private UserRepository userRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new CodeDraftController(codeDraftService, userRepository))
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "alice", "n", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedDraftEndpointsAreRejected() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/problems/p1/drafts/62"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/problems/p1/drafts/62")
                .contentType("application/json")
                .content("{\"code\":\"print(1)\",\"expectedRevision\":0}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/contests/c1/problems/p1/drafts/62"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/contests/c1/problems/p1/drafts/62")
                .contentType("application/json")
                .content("{\"code\":\"print(1)\",\"expectedRevision\":0,\"userId\":\"bob\"}"))
            .andExpect(status().isUnauthorized());
        verify(codeDraftService, never()).loadPractice(any(), any(), any());
        verify(codeDraftService, never()).savePractice(any(), any(), any(), any(), any());
        verify(codeDraftService, never()).loadContest(any(), any(), any(), any());
        verify(codeDraftService, never()).saveContest(any(), any(), any(), any(), any(), any());
    }

    @Test
    void bodyCannotSpoofUserId() throws Exception {
        User alice = user("alice-id", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(codeDraftService.savePractice(eq(alice), eq("p1"), eq(62), eq("alice-code"), eq(2L)))
            .thenReturn(new CodeDraftResponse("alice-code", 3, Instant.parse("2026-10-12T09:00:00Z")));

        mockMvc.perform(put("/api/problems/p1/drafts/62")
                .contentType("application/json")
                .content("{\"code\":\"alice-code\",\"expectedRevision\":2,\"userId\":\"bob-id\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revision").value(3));

        verify(codeDraftService).savePractice(eq(alice), eq("p1"), eq(62), eq("alice-code"), eq(2L));
        verify(codeDraftService, never()).savePractice(eq(user("bob-id", "bob")), any(), any(), any(), any());
    }

    @Test
    void conflictReturnsCurrentCloudDraft() throws Exception {
        User alice = user("alice-id", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(codeDraftService.saveContest(eq(alice), eq("c1"), eq("p1"), eq(71), eq("local"), isNull()))
            .thenThrow(new DraftConflictException("cloud", 4, Instant.parse("2026-10-12T09:00:00Z")));

        mockMvc.perform(put("/api/contests/c1/problems/p1/drafts/71")
                .contentType("application/json")
                .content("{\"code\":\"local\",\"userId\":\"bob-id\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("DRAFT_CONFLICT"))
            .andExpect(jsonPath("$.code").value("cloud"))
            .andExpect(jsonPath("$.revision").value(4));
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole("USER");
        user.setAccountStatus("ACTIVE");
        return user;
    }
}
