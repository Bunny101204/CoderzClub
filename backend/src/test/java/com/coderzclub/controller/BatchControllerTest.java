package com.coderzclub.controller;

import com.coderzclub.model.Batch;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.BatchReportService;
import com.coderzclub.service.BatchService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BatchControllerTest {
    @Mock
    private BatchService batchService;
    @Mock
    private BatchReportService batchReportService;
    @Mock
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BatchController(batchService, batchReportService, userRepository))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createValidatesNameThroughService() throws Exception {
        stubAdminUser("admin-mongo-id");
        org.mockito.Mockito.when(batchService.create(eq("  "), any(), eq("admin-mongo-id")))
            .thenThrow(new IllegalArgumentException("name is required"));
        mockMvc.perform(post("/api/admin/batches")
                .principal(adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  \"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createStoresMongoUserIdAsCreatedBy() throws Exception {
        stubAdminUser("admin-mongo-id");
        Batch saved = new Batch();
        saved.setId("b1");
        org.mockito.Mockito.when(batchService.create("DSA", "desc", "admin-mongo-id")).thenReturn(saved);
        org.mockito.Mockito.when(batchService.detail("b1")).thenReturn(Map.of("id", "b1", "createdBy", "admin-mongo-id"));
        mockMvc.perform(post("/api/admin/batches")
                .principal(adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"DSA\",\"description\":\"desc\"}"))
            .andExpect(status().isOk());
        verify(batchService).create("DSA", "desc", "admin-mongo-id");
        verify(batchService, never()).create(any(), any(), eq("admin"));
    }

    @Test
    void createDoesNotFallBackToUsernameWhenUserMissing() throws Exception {
        org.mockito.Mockito.when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/admin/batches")
                .principal(adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"DSA\"}"))
            .andExpect(status().isUnauthorized());
        verify(batchService, never()).create(any(), any(), any());
    }

    @Test
    void reportEndpointUsesReportService() throws Exception {
        org.mockito.Mockito.when(batchReportService.report("b1", 0, 20, null)).thenReturn(Map.of("students", List.of()));
        mockMvc.perform(get("/api/admin/batches/b1/report").principal(adminAuth()))
            .andExpect(status().isOk());
        verify(batchService, never()).list(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
        verify(batchReportService).report("b1", 0, 20, null);
    }

    private void stubAdminUser(String id) {
        User user = new User();
        user.setId(id);
        user.setUsername("admin");
        user.setAccountStatus("ACTIVE");
        org.mockito.Mockito.when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
    }

    private static Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
            "admin", "n", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
