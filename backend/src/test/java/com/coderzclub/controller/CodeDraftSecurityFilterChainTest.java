package com.coderzclub.controller;

import com.coderzclub.config.CorsOrigins;
import com.coderzclub.config.GlobalExceptionHandler;
import com.coderzclub.config.JwtFilter;
import com.coderzclub.config.SecurityConfig;
import com.coderzclub.dto.CodeDraftResponse;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.CodeDraftService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = CodeDraftController.class,
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
class CodeDraftSecurityFilterChainTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private CodeDraftService codeDraftService;
    @MockBean private UserRepository userRepository;
    @MockBean private JwtFilter jwtFilter;

    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    @Test
    void unauthenticatedPracticeAndContestDraftsAreRejected() throws Exception {
        mockMvc.perform(put("/api/problems/p1/drafts/62")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"print(1)\",\"expectedRevision\":0}"))
            .andExpect(result -> assertUnauthorized(result.getResponse().getStatus()));
        mockMvc.perform(get("/api/contests/c1/problems/p1/drafts/62"))
            .andExpect(result -> assertUnauthorized(result.getResponse().getStatus()));
        verify(codeDraftService, never()).savePractice(any(), any(), any(), any(), any());
        verify(codeDraftService, never()).loadContest(any(), any(), any(), any());
    }

    @Test
    void ordinaryUserCanSaveAPracticeDraft() throws Exception {
        User alice = dbUser("alice-id", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(codeDraftService.savePractice(eq(alice), eq("p1"), eq(62), eq("print(1)"), eq(0L)))
            .thenReturn(new CodeDraftResponse("print(1)", 1, null));

        mockMvc.perform(put("/api/problems/p1/drafts/62")
                .with(user("alice").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"print(1)\",\"expectedRevision\":0,\"userId\":\"bob-id\"}"))
            .andExpect(status().isOk());

        verify(codeDraftService).savePractice(eq(alice), eq("p1"), eq(62), eq("print(1)"), eq(0L));
    }

    private static void assertUnauthorized(int status) {
        if (status != 401 && status != 403) {
            throw new AssertionError("expected 401 or 403 but was " + status);
        }
    }

    private static User dbUser(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole("USER");
        user.setAccountStatus("ACTIVE");
        return user;
    }
}
