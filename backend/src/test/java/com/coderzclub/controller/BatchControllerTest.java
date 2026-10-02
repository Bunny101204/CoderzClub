package com.coderzclub.controller;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

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
        org.mockito.Mockito.when(batchService.create(eq("  "), any(), any()))
            .thenThrow(new IllegalArgumentException("name is required"));
        authenticate("ROLE_ADMIN");
        mockMvc.perform(post("/api/admin/batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  \"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void reportEndpointUsesReportService() throws Exception {
        authenticate("ROLE_ADMIN");
        org.mockito.Mockito.when(batchReportService.report("b1", 0, 20, null)).thenReturn(java.util.Map.of("students", List.of()));
        mockMvc.perform(get("/api/admin/batches/b1/report"))
            .andExpect(status().isOk());
        verify(batchService, never()).list(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
        verify(batchReportService).report("b1", 0, 20, null);
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "admin", "n", List.of(new SimpleGrantedAuthority(role))));
    }
}
