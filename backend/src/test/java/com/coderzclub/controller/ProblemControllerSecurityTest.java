package com.coderzclub.controller;

import com.coderzclub.model.Problem;
import com.coderzclub.model.TestCase;
import com.coderzclub.repository.ProblemRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProblemControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProblemRepository problemRepository;

    @MockBean
    private com.coderzclub.service.ProblemNumericIdAllocator numericIdAllocator;

    @SpyBean
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        Problem problem = new Problem();
        problem.setId("p1");
        problem.setTitle("Two Sum");
        problem.setStatement("Solve it");
        problem.setPublicTestCases(List.of(new TestCase("1", "2", "public")));
        problem.setHiddenTestCases(List.of(new TestCase("9", "10", "secret")));

        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
    }

    @Test
    @WithMockUser(roles = "USER")
    void publicUserCanReadProblemsWithoutHiddenCases() throws Exception {
        mockMvc.perform(get("/api/problems/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenTestCases").doesNotExist())
                .andExpect(jsonPath("$.publicTestCases[0].input").value("1"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void publicUserCannotCreateProblem() throws Exception {
        mockMvc.perform(post("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void problemListUsesDatabaseFilteringSortingAndPagination() throws Exception {
        Problem first = new Problem();
        first.setId("20");
        first.setNumericId(20);
        first.setTitle("Second");
        first.setPublicTestCases(List.of(new TestCase("1", "2", "public")));
        first.setHiddenTestCases(List.of(new TestCase("secret", "secret", "hidden")));
        Problem second = new Problem();
        second.setId("30");
        second.setNumericId(30);
        second.setTitle("Third");

        doReturn(5L).when(mongoTemplate).count(any(Query.class), eq(Problem.class));
        doReturn(List.of(first, second)).when(mongoTemplate).find(any(Query.class), eq(Problem.class));

        mockMvc.perform(get("/api/problems?page=1&size=2&difficulty=EASY&category=ALGORITHMS&tags=array&search=Sec"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.currentPage").value(1))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.hasPrevious").value(true))
            .andExpect(jsonPath("$.problems", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$.problems[0].id").value("20"))
            .andExpect(jsonPath("$.problems[0].hiddenTestCases").doesNotExist());

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(any(Query.class), eq(Problem.class));
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Problem.class));
        Query pageQuery = queryCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(2L, pageQuery.getSkip());
        org.junit.jupiter.api.Assertions.assertEquals(2, pageQuery.getLimit());
        org.junit.jupiter.api.Assertions.assertEquals(1, pageQuery.getSortObject().get("numericId"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void cursorPaginationUsesBoundaryWithoutSkip() throws Exception {
        Problem first = new Problem();
        first.setId("30");
        first.setNumericId(30);
        first.setTitle("Third");
        Problem second = new Problem();
        second.setId("40");
        second.setNumericId(40);
        second.setTitle("Fourth");
        Problem extra = new Problem();
        extra.setId("50");
        extra.setNumericId(50);

        doReturn(List.of(first, second, extra)).when(mongoTemplate).find(any(Query.class), eq(Problem.class));

        mockMvc.perform(get("/api/problems?cursor=MjB8MjA&limit=2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.problems", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$.nextCursor").value("NDB8NDA"))
            .andExpect(jsonPath("$.hasNext").value(true));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Problem.class));
        Query cursorQuery = queryCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(0L, cursorQuery.getSkip());
        org.junit.jupiter.api.Assertions.assertEquals(3, cursorQuery.getLimit());
        org.junit.jupiter.api.Assertions.assertTrue(cursorQuery.getQueryObject().containsKey("$or"));
    }
}
