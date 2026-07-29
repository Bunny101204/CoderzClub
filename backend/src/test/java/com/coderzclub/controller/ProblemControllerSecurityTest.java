package com.coderzclub.controller;

import com.coderzclub.model.Problem;
import com.coderzclub.model.TestCase;
import com.coderzclub.repository.ProblemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
}
