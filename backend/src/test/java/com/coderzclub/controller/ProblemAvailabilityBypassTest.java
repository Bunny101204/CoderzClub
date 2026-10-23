package com.coderzclub.controller;

import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.User;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ContestAccessService;
import com.coderzclub.service.OperationalMetrics;
import com.coderzclub.service.ProblemAccessService;
import com.coderzclub.service.ProblemIdentityResolver;
import com.coderzclub.service.ProblemPublicDetailService;
import com.coderzclub.service.ProblemReadCache;
import com.coderzclub.service.ProblemRunService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProblemAvailabilityBypassTest {

    @Mock
    private ProblemRepository problemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private ProblemRunService problemRunService;
    @Mock
    private ContestAccessService contestAccessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProblemController controller = new ProblemController();
        ReflectionTestUtils.setField(controller, "problemRepository", problemRepository);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "mongoTemplate", mongoTemplate);
        ReflectionTestUtils.setField(controller, "problemRunService", problemRunService);
        ProblemAccessService access = new ProblemAccessService(contestAccessService);
        ReflectionTestUtils.setField(controller, "problemAccessService", access);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        lenient().when(redis.opsForValue()).thenReturn(values);
        ProblemReadCache cache = new ProblemReadCache(
            redis, new ObjectMapper(), mongoTemplate,
            new OperationalMetrics(new SimpleMeterRegistry()), 600, 600);
        ReflectionTestUtils.setField(controller, "problemReadCache", cache);
        ReflectionTestUtils.setField(controller, "problemPublicDetailService",
            new ProblemPublicDetailService(mongoTemplate, problemRepository, access, cache));
        ReflectionTestUtils.setField(controller, "problemIdentityResolver", new ProblemIdentityResolver(mongoTemplate));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void paginatedSearchAndDifficultyUseMongoAvailabilityFilterForCountAndPage() throws Exception {
        when(mongoTemplate.count(any(Query.class), eq(Problem.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Problem.class))).thenReturn(List.of());
        mockMvc.perform(get("/api/problems").param("search", "secret").param("difficulty", "EASY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1));
        ArgumentCaptor<Query> count = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Query> find = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(count.capture(), eq(Problem.class));
        verify(mongoTemplate).find(find.capture(), eq(Problem.class));
        String countQuery = String.valueOf(count.getValue().getQueryObject());
        String findQuery = String.valueOf(find.getValue().getQueryObject());
        assertTrue(countQuery.contains("CONTEST_ONLY"));
        assertTrue(countQuery.contains("EASY"));
        assertTrue(findQuery.contains("CONTEST_ONLY"));
        verify(contestAccessService, never()).hasAccessibleLiveOrEndedContestContaining(any(), any());
    }

    @Test
    void publicDetailStillWorks() throws Exception {
        Problem visible = new Problem();
        visible.setId("pub");
        visible.setTitle("Two Sum");
        when(mongoTemplate.findOne(any(Query.class), eq(Problem.class))).thenReturn(visible);
        when(problemRepository.findById("pub")).thenReturn(Optional.of(visible));
        mockMvc.perform(get("/api/problems/pub"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Two Sum"));
    }

    @Test
    void directIdAndNumericIdHideContestOnly() throws Exception {
        Problem secret = contestOnly("secret", 42);
        when(mongoTemplate.findOne(any(Query.class), eq(Problem.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            Object id = query.getQueryObject().get("_id");
            Object numericId = query.getQueryObject().get("numericId");
            if ("secret".equals(String.valueOf(id)) || Integer.valueOf(42).equals(numericId)) {
                return secret;
            }
            return null;
        });
        when(problemRepository.findById("secret")).thenReturn(Optional.of(secret));
        mockMvc.perform(get("/api/problems/secret")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/problems/42")).andExpect(status().isNotFound());
        verify(contestAccessService, never()).hasAccessibleLiveOrEndedContestContaining(any(), any());
        verify(problemRepository, never()).findByNumericId(any());
    }

    @Test
    void byIdsOmitsContestOnly() throws Exception {
        Problem secret = contestOnly("secret", 7);
        Problem visible = new Problem();
        visible.setId("pub");
        visible.setTitle("Visible");
        when(mongoTemplate.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(secret, visible));
        mockMvc.perform(get("/api/problems/by-ids").param("ids", "secret", "pub"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("pub"))
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void runPublicRejectsContestOnlyBeforeExecution() throws Exception {
        User student = user("u1");
        Problem secret = contestOnly("secret", 1);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(student));
        when(problemRepository.findById("secret")).thenReturn(Optional.of(secret));
        authenticate("alice");
        mockMvc.perform(post("/api/problems/secret/run-public")
                .contentType("application/json")
                .content("{\"source_code\":\"print(1)\",\"language_id\":71}"))
            .andExpect(status().isNotFound());
        verify(problemRunService, never()).runPublicCases(any(), any(), any(), any());
    }

    @Test
    void customJudge0ExecuteHasNoProblemReference() {
        for (java.lang.reflect.Field field : Judge0Controller.Judge0ExecutionRequest.class.getDeclaredFields()) {
            assertFalse(field.getName().toLowerCase().contains("problem"));
        }
    }

    private static Problem contestOnly(String id, int numericId) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setNumericId(numericId);
        problem.setTitle("Secret");
        problem.setAvailability(ProblemAvailability.CONTEST_ONLY);
        return problem;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        user.setRole("USER");
        user.setAccountStatus("ACTIVE");
        return user;
    }

    private static void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            username, "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
