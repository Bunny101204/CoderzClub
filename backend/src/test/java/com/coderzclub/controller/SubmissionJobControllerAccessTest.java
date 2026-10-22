package com.coderzclub.controller;

import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.SubmissionJobAccessService;
import com.coderzclub.service.SubmissionJobService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubmissionJobControllerAccessTest {

    @Mock
    private SubmissionJobService jobService;
    @Mock
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SubmissionJobController controller = new SubmissionJobController();
        ReflectionTestUtils.setField(controller, "jobService", jobService);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "jobAccessService", new SubmissionJobAccessService(userRepository));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerCanReadOwnJob() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("user-a", "alice")));
        when(jobService.getJob("job-1")).thenReturn(Optional.of(job("job-1", "user-a")));

        mockMvc.perform(get("/api/submission-jobs/job-1").principal(auth("alice", "ROLE_USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobId").value("job-1"))
            .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void otherUserReceivesForbidden() throws Exception {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user("user-b", "bob")));
        when(jobService.getJob("job-1")).thenReturn(Optional.of(job("job-1", "user-a")));

        mockMvc.perform(get("/api/submission-jobs/job-1").principal(auth("bob", "ROLE_USER")))
            .andExpect(status().isForbidden());

        verify(jobService, never()).getResults(org.mockito.ArgumentMatchers.any(SubmissionJob.class));
    }

    @Test
    void adminMayReadAnyJobPerAccessServiceContract() throws Exception {
        when(jobService.getJob("job-1")).thenReturn(Optional.of(job("job-1", "user-a")));

        mockMvc.perform(get("/api/submission-jobs/job-1").principal(auth("admin", "ROLE_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobId").value("job-1"));
    }

    @Test
    void missingJobRemainsNotFound() throws Exception {
        when(jobService.getJob("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/submission-jobs/missing").principal(auth("alice", "ROLE_USER")))
            .andExpect(status().isNotFound());
    }

    private static SubmissionJob job(String id, String userId) {
        SubmissionJob job = new SubmissionJob();
        job.setId(id);
        job.setUserId(userId);
        job.setStatus(SubmissionJob.JobStatus.QUEUED);
        job.setCompletedTests(0);
        job.setTotalTests(2);
        return job;
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static UsernamePasswordAuthenticationToken auth(String username, String role) {
        return new UsernamePasswordAuthenticationToken(username, "n/a",
            List.of(new SimpleGrantedAuthority(role)));
    }
}
