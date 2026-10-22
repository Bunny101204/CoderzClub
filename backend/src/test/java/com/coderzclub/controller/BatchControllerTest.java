package com.coderzclub.controller;

import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchCoordinator;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.BatchAccessService;
import com.coderzclub.service.BatchCoordinatorService;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
    private BatchCoordinatorService batchCoordinatorService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MongoTemplate mongoTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BatchController(
                batchService,
                batchReportService,
                batchCoordinatorService,
                userRepository,
                new BatchAccessService(mongoTemplate, userRepository)))
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

    @Test
    void coordinatorCannotReadAnotherBatch() throws Exception {
        stubUser("coord", "coord-id");
        when(mongoTemplate.exists(any(Query.class), eq(BatchCoordinator.class))).thenReturn(false);
        mockMvc.perform(get("/api/admin/batches/batch-b").principal(userAuth("coord")))
            .andExpect(status().isForbidden());
        verify(batchService, never()).detail(any());
    }

    @Test
    void coordinatorCanReadAssignedBatch() throws Exception {
        stubUser("coord", "coord-id");
        when(mongoTemplate.exists(any(Query.class), eq(BatchCoordinator.class))).thenReturn(true);
        when(batchService.detail("batch-a")).thenReturn(Map.of("id", "batch-a"));
        mockMvc.perform(get("/api/admin/batches/batch-a").principal(userAuth("coord")))
            .andExpect(status().isOk());
        verify(batchService).detail("batch-a");
    }

    @Test
    void revokedCoordinatorCannotAddMembers() throws Exception {
        stubUser("coord", "coord-id");
        when(mongoTemplate.exists(any(Query.class), eq(BatchCoordinator.class))).thenReturn(false);
        mockMvc.perform(post("/api/admin/batches/batch-a/members")
                .principal(userAuth("coord"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[\"student\"]}"))
            .andExpect(status().isForbidden());
        verify(batchService, never()).addMembers(any(), any());
    }

    @Test
    void coordinatorCannotGrantCoordinatorAccess() throws Exception {
        mockMvc.perform(post("/api/admin/batches/batch-a/coordinators")
                .principal(userAuth("coord"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"other\"}"))
            .andExpect(status().isForbidden());
        verify(batchCoordinatorService, never()).grant(any(), any(), any(), any());
    }

    @Test
    void coordinatorCannotProbeMembershipOfAnotherBatch() throws Exception {
        stubUser("coord", "coord-id");
        when(mongoTemplate.exists(any(Query.class), eq(BatchCoordinator.class))).thenAnswer(invocation -> {
            String query = invocation.getArgument(0, Query.class).getQueryObject().toJson();
            return !query.contains("batch-b");
        });
        mockMvc.perform(get("/api/admin/batches/users")
                .param("search", "ada")
                .param("batchId", "batch-b")
                .principal(userAuth("coord")))
            .andExpect(status().isForbidden());
        verify(batchService, never()).searchUsers(any(), anyInt(), anyInt(), any());
    }

    @Test
    void studentCannotSearchRegisteredUsers() throws Exception {
        stubUser("student", "student-id");
        when(mongoTemplate.exists(any(Query.class), eq(BatchCoordinator.class))).thenReturn(false);
        mockMvc.perform(get("/api/admin/batches/users").param("search", "al").principal(userAuth("student")))
            .andExpect(status().isForbidden());
        verify(batchService, never()).searchUsers(any(), anyInt(), anyInt(), any());
    }

    private void stubAdminUser(String id) {
        User user = new User();
        user.setId(id);
        user.setUsername("admin");
        user.setAccountStatus("ACTIVE");
        org.mockito.Mockito.when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
    }

    private void stubUser(String username, String id) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setAccountStatus("ACTIVE");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
    }

    private static Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
            "admin", "n", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private static Authentication userAuth(String username) {
        return new UsernamePasswordAuthenticationToken(
            username, "n", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
