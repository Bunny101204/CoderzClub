package com.coderzclub.controller;

import com.coderzclub.dto.ContestAdminResponse;
import com.coderzclub.dto.ContestWriteRequest;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ContestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminContestControllerTest {
    @Mock
    private ContestService contestService;
    @Mock
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminContestController(contestService, userRepository))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
        SecurityContextHolder.getContext().setAuthentication(adminAuth());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUsesAuthenticatedMongoUserId() throws Exception {
        stubAdmin("admin-mongo-id");
        ContestAdminResponse created = new ContestAdminResponse();
        created.setId("c1");
        created.setCreatedBy("admin-mongo-id");
        when(contestService.create(any(ContestWriteRequest.class), eq("admin-mongo-id"))).thenReturn(created);
        mockMvc.perform(post("/api/admin/contests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Weekly\",\"startAt\":\"2026-06-01T13:00:00Z\",\"endAt\":\"2026-06-01T15:00:00Z\",\"audienceType\":\"BATCHES\",\"batchIds\":[\"b1\"],\"problemIds\":[\"p1\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("c1"));
        verify(contestService).create(any(ContestWriteRequest.class), eq("admin-mongo-id"));
        verify(contestService, never()).create(any(), eq("admin"));
    }

    @Test
    void validationErrorsStayBadRequest() throws Exception {
        stubAdmin("admin-mongo-id");
        when(contestService.create(any(ContestWriteRequest.class), eq("admin-mongo-id")))
            .thenThrow(new IllegalArgumentException("endAt must be after startAt"));
        mockMvc.perform(post("/api/admin/contests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Weekly\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("endAt must be after startAt"));
    }

    @Test
    void liveMutationConflictIsBadRequest() throws Exception {
        when(contestService.update(eq("c1"), any(ContestWriteRequest.class)))
            .thenThrow(new IllegalArgumentException("Live contests cannot change problems"));
        mockMvc.perform(patch("/api/admin/contests/c1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"problemIds\":[\"p2\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Live contests cannot change problems"));
    }

    @Test
    void listDelegatesToService() throws Exception {
        when(contestService.adminList(0, 20)).thenReturn(Map.of("contests", List.of(), "currentPage", 0, "totalItems", 0L));
        mockMvc.perform(get("/api/admin/contests"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void listForwardsPageAndRequestedSize() throws Exception {
        when(contestService.adminList(3, 999)).thenReturn(Map.of("contests", List.of(), "currentPage", 3, "totalItems", 0L));
        mockMvc.perform(get("/api/admin/contests").param("page", "3").param("size", "999"))
            .andExpect(status().isOk());
        verify(contestService).adminList(3, 999);
    }

    @Test
    void deleteDelegatesToService() throws Exception {
        mockMvc.perform(delete("/api/admin/contests/c1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true));
        verify(contestService).delete("c1");
    }

    private void stubAdmin(String id) {
        User user = new User();
        user.setId(id);
        user.setUsername("admin");
        user.setRole("ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
    }

    private static Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
            "admin", "n", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
