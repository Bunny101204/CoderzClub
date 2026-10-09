package com.coderzclub.controller;

import com.coderzclub.config.AuthenticatedUser;
import com.coderzclub.model.Submission;
import com.coderzclub.model.User;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.SubmissionRepository;
import com.coderzclub.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubmissionControllerAuthorizationTest {

    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProblemRepository problemRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SubmissionController controller = new SubmissionController();
        ReflectionTestUtils.setField(controller, "submissionRepository", submissionRepository);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "problemRepository", problemRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        authenticate("alice");
        lenient().when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("u-alice", "alice")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mySubmissionsUsesCurrentUserId() throws Exception {
        Submission mine = submission("s1", "u-alice", "int main(){}", "ACCEPTED");
        when(submissionRepository.findByUserId(eq("u-alice"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(mine), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/submissions/my-submissions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.submissions[0].userId").value("u-alice"))
            .andExpect(jsonPath("$.submissions[0].code").value("int main(){}"));

        verify(submissionRepository).findByUserId(eq("u-alice"), any(Pageable.class));
        verify(submissionRepository, never()).findByUserId(eq("u-bob"), any(Pageable.class));
    }

    @Test
    void mySubmissionsReusesRequestUserWithoutRepositoryLookup() throws Exception {
        User requestUser = user("u-alice", "alice");
        when(submissionRepository.findByUserId(eq("u-alice"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mockMvc.perform(get("/api/submissions/my-submissions")
                .requestAttr(AuthenticatedUser.ATTRIBUTE, requestUser))
            .andExpect(status().isOk());
        verify(userRepository, never()).findByUsername(anyString());
        verify(submissionRepository).findByUserId(eq("u-alice"), any(Pageable.class));
    }

    @Test
    void mySubmissionsProblemFilterStaysOnCurrentUserPath() throws Exception {
        when(problemRepository.findById("p1")).thenReturn(Optional.empty());
        when(submissionRepository.findByUserIdAndProblemIdIn(eq("u-alice"), any(Collection.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/submissions/my-submissions").param("problemId", "p1"))
            .andExpect(status().isOk());

        ArgumentCaptor<Collection<String>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(submissionRepository).findByUserIdAndProblemIdIn(eq("u-alice"), ids.capture(), any(Pageable.class));
        assertTrue(ids.getValue().contains("p1"));
        verify(submissionRepository, never()).findByProblemId(any(), any());
    }

    @Test
    void problemHistoryIsScopedToAuthenticatedUser() throws Exception {
        Submission mine = submission("s1", "u-alice", "alice-code", "WRONG_ANSWER");
        when(problemRepository.findById("p1")).thenReturn(Optional.empty());
        when(submissionRepository.findByUserIdAndProblemIdIn(eq("u-alice"), any(Collection.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(mine), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/submissions/problem/p1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.submissions[0].userId").value("u-alice"))
            .andExpect(jsonPath("$.submissions[0].code").value("alice-code"));

        verify(submissionRepository).findByUserIdAndProblemIdIn(eq("u-alice"), any(Collection.class), any(Pageable.class));
        verify(submissionRepository, never()).findByProblemId(eq("p1"), any(Pageable.class));
        verify(submissionRepository, never()).findByUserIdAndProblemIdIn(eq("u-bob"), any(), any());
    }

    @Test
    void currentUserCanReadOwnUserHistory() throws Exception {
        Submission mine = submission("s1", "u-alice", "alice-only", "ACCEPTED");
        when(submissionRepository.findByUserId(eq("u-alice"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(mine), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/submissions/user/u-alice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.submissions[0].code").value("alice-only"));
    }

    @Test
    void differentUserCannotReadAnotherUsersHistoryOrCode() throws Exception {
        mockMvc.perform(get("/api/submissions/user/u-bob"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("Cannot read another user's submissions"));

        verify(submissionRepository, never()).findByUserId(eq("u-bob"), any(Pageable.class));
        verify(submissionRepository, never()).findByUserId(any(), any());
    }

    @Test
    void historyPageSizeIsClampedAndNewestFirst() throws Exception {
        when(submissionRepository.findByUserId(eq("u-alice"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        mockMvc.perform(get("/api/submissions/my-submissions")
                .param("size", "999")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(submissionRepository).findByUserId(eq("u-alice"), pageable.capture());
        assertEquals(50, pageable.getValue().getPageSize());
        Sort.Order order = pageable.getValue().getSort().getOrderFor("createdAt");
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    private static void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                username,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
            )
        );
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static Submission submission(String id, String userId, String code, String result) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setUserId(userId);
        submission.setCode(code);
        submission.setResult(result);
        return submission;
    }
}
