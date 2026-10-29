package com.coderzclub.controller;

import com.coderzclub.config.CorsOrigins;
import com.coderzclub.config.GlobalExceptionHandler;
import com.coderzclub.config.JwtFilter;
import com.coderzclub.config.SecurityConfig;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.BatchAccessService;
import com.coderzclub.service.BatchCoordinatorService;
import com.coderzclub.service.BatchReportService;
import com.coderzclub.service.BatchService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = {BatchController.class, MyBatchController.class},
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = GlobalExceptionHandler.class)
)
@Import({SecurityConfig.class, CorsOrigins.class, ApiExceptionHandler.class})
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
    "app.cors.allowed-origins=http://localhost:5173"
})
class BatchSecurityFilterChainTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private BatchService batchService;
    @MockBean
    private BatchReportService batchReportService;
    @MockBean
    private BatchCoordinatorService batchCoordinatorService;
    @MockBean
    private BatchAccessService batchAccessService;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private JwtFilter jwtFilter;

    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    @Test
    void coordinatorCannotOpenGlobalAdminRoutes() throws Exception {
        mockMvc.perform(get("/api/admin/contests").with(user("coord").roles("USER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotListEveryBatch() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is required"))
            .when(batchAccessService).ensureAdmin(any());
        mockMvc.perform(get("/api/admin/batches").with(user("student").roles("USER")))
            .andExpect(status().isForbidden());
        verify(batchService, never()).list(anyInt(), anyInt(), any(), any());
    }

    @Test
    void coordinatorRequestReachesTheBatchAccessCheck() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this batch"))
            .when(batchAccessService).ensureCanReadBatch(any(), eq("batch-b"));
        mockMvc.perform(get("/api/admin/batches/batch-b").with(user("coord").roles("USER")))
            .andExpect(status().isForbidden());
        verify(batchAccessService).ensureCanReadBatch(any(), eq("batch-b"));
        verify(batchService, never()).detail(any());
    }

    @Test
    void signedInUserCanOpenTheCoordinatorBatchList() throws Exception {
        User coord = new User();
        coord.setId("coord-id");
        coord.setUsername("coord");
        when(batchAccessService.requireUser(any())).thenReturn(coord);
        when(batchCoordinatorService.listForUser("coord-id")).thenReturn(List.of());
        when(batchService.listEnrolledForUser(eq("coord-id"), eq(0), eq(20))).thenReturn(Map.of(
            "items", List.of(),
            "currentPage", 0,
            "totalPages", 0,
            "totalItems", 0
        ));
        mockMvc.perform(get("/api/my/batches").with(user("coord").roles("USER")))
            .andExpect(status().isOk());
        verify(batchCoordinatorService).listForUser("coord-id");
        verify(batchService).listEnrolledForUser("coord-id", 0, 20);
    }

    @Test
    void signedInMemberCanRequestTheirPracticePage() throws Exception {
        User student = new User();
        student.setId("student-id");
        student.setUsername("student");
        when(batchAccessService.requireUser(any())).thenReturn(student);
        when(batchService.enrolledPractice(eq("student-id"), eq("batch-a"), eq(0), eq(20)))
            .thenReturn(Map.of("batchId", "batch-a"));
        mockMvc.perform(get("/api/my/batches/batch-a/practice").with(user("student").roles("USER")))
            .andExpect(status().isOk());
        verify(batchService).enrolledPractice("student-id", "batch-a", 0, 20);
    }

    @Test
    void anonymousCoordinatorListIsRejected() throws Exception {
        mockMvc.perform(get("/api/my/batches"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status != 401 && status != 403) {
                    throw new AssertionError("expected 401 or 403 but was " + status);
                }
            });
        verify(batchCoordinatorService, never()).listForUser(any());
        verify(batchService, never()).listEnrolledForUser(any(), anyInt(), anyInt());
        verify(batchService, never()).enrolledPractice(any(), any(), anyInt(), anyInt());
    }

    @Test
    void userCannotCreateABatchThroughTheOpenedBatchRoutes() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is required"))
            .when(batchAccessService).ensureAdmin(any());
        mockMvc.perform(post("/api/admin/batches")
                .with(user("coord").roles("USER"))
                .contentType("application/json")
                .content("{\"name\":\"Other\"}"))
            .andExpect(status().isForbidden());
        verify(batchService, never()).create(any(), any(), any());
    }
}
