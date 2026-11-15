package com.coderzclub.controller;

import com.coderzclub.config.JwtUtil;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.OrganizationStatus;
import com.coderzclub.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrganizationProblemContestAccessTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant START = Instant.now().minusSeconds(3600);
    private static final Instant END = Instant.now().plusSeconds(3600);

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> "mongodb://127.0.0.1:27017/coderzclub_org_problem_access");
        registry.add("jwt.secret", () -> "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        registry.add("spring.mail.enabled", () -> "false");
        registry.add("contest.global-enabled", () -> "true");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    @AfterEach
    void dropIsolatedDatabase() {
        mongoTemplate.getDb().drop();
    }

    @Test
    void orgPrivateProblemsAndContestsDenyCrossTenantAccess() throws Exception {
        User platformAdmin = saveUser("platform-admin", "ADMIN");
        User ownerA = saveUser("owner-a", "USER");
        User ownerB = saveUser("owner-b", "USER");
        User studentA = saveUser("student-a", "USER");
        User outsider = saveUser("outsider", "USER");

        String adminToken = token(platformAdmin);
        String orgAId = createOrg(adminToken, "Alpha College", "alpha-problems", ownerA.getId());
        String orgBId = createOrg(adminToken, "Beta Institute", "beta-problems", ownerB.getId());
        String ownerAToken = token(ownerA);
        String ownerBToken = token(ownerB);
        String studentToken = token(studentA);
        String outsiderToken = token(outsider);

        mockMvc.perform(post("/api/organizations/" + orgAId + "/members")
                .header("Authorization", ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + studentA.getId() + "\",\"roles\":[\"STUDENT\"]}"))
            .andExpect(status().isOk());

        MvcResult problemCreated = mockMvc.perform(post("/api/problems")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalProblemJson("Org A Private Drill")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organizationId").value(orgAId))
            .andReturn();
        String problemAId = MAPPER.readTree(problemCreated.getResponse().getContentAsString()).get("id").asText();

        // Outsider and Org B cannot view or mutate Org A problems.
        mockMvc.perform(get("/api/problems/" + problemAId).header("Authorization", outsiderToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/problems/" + problemAId).header("Authorization", ownerBToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/problems/" + problemAId + "/admin")
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgBId))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/problems/" + problemAId + "/run-public")
                .header("Authorization", outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source_code\":\"class Main{}\",\"language_id\":62}"))
            .andExpect(status().isNotFound());

        // Org A student can view the org-private PUBLIC problem.
        mockMvc.perform(get("/api/problems/" + problemAId).header("Authorization", studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(problemAId));

        MvcResult listPublic = mockMvc.perform(get("/api/problems"))
            .andExpect(status().isOk())
            .andReturn();
        assertFalse(listPublic.getResponse().getContentAsString().contains(problemAId));

        MvcResult listStudent = mockMvc.perform(get("/api/problems").header("Authorization", studentToken))
            .andExpect(status().isOk())
            .andReturn();
        assertTrue(listStudent.getResponse().getContentAsString().contains(problemAId));

        // Spoofed org header cannot create into another tenant.
        mockMvc.perform(post("/api/problems")
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalProblemJson("Hijack")))
            .andExpect(status().isForbidden());

        // Client-supplied problem id must not overwrite an existing org-private problem.
        mockMvc.perform(post("/api/problems")
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgBId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalProblemJsonWithId(problemAId, "Overwrite attempt")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(problemAId)))
            .andExpect(jsonPath("$.organizationId").value(orgBId));
        mockMvc.perform(get("/api/problems/" + problemAId).header("Authorization", studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Org A Private Drill"));

        // Batch + contest isolation for submissions and leaderboard.
        MvcResult batchCreated = mockMvc.perform(post("/api/admin/batches")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alpha Contest Batch\"}"))
            .andExpect(status().isOk())
            .andReturn();
        String batchAId = MAPPER.readTree(batchCreated.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/admin/batches/" + batchAId + "/members")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[\"" + studentA.getId() + "\"]}"))
            .andExpect(status().isOk());

        MvcResult contestCreated = mockMvc.perform(post("/api/admin/contests")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(contestBody("Alpha Private Contest", batchAId, problemAId)))
            .andExpect(status().isOk())
            .andReturn();
        String contestAId = MAPPER.readTree(contestCreated.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/admin/contests/" + contestAId + "/publish")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/contests/" + contestAId + "/problems/" + problemAId + "/submission-jobs")
                .header("Authorization", outsiderToken)
                .header("Idempotency-Key", "outsider-job-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"class Main { public static void main(String[] a){}}\",\"languageId\":62}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/contests/" + contestAId + "/leaderboard")
                .header("Authorization", outsiderToken))
            .andExpect(status().isNotFound());

        // Org owner can monitor leaderboard without being a batch member.
        mockMvc.perform(get("/api/contests/" + contestAId + "/leaderboard")
                .header("Authorization", ownerAToken))
            .andExpect(status().isOk());

        // Practice submission / job / draft / by-ids must not leak org-private problems.
        mockMvc.perform(post("/api/submission-jobs")
                .header("Authorization", outsiderToken)
                .header("Idempotency-Key", "outsider-practice-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"problemId\":\"" + problemAId + "\",\"code\":\"class Main { public static void main(String[] a){}}\",\"languageId\":62,\"language\":\"java\"}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/submissions")
                .header("Authorization", outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"problemId\":\"" + problemAId + "\",\"code\":\"class Main{}\",\"language\":\"java\",\"languageId\":62,\"statusId\":3}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/problems/by-ids").param("ids", problemAId)
                .header("Authorization", outsiderToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/problems/" + problemAId + "/drafts/62")
                .header("Authorization", outsiderToken))
            .andExpect(status().isNotFound());

        // Cross-tenant batch assignment of Org A problem into Org B batch is rejected.
        MvcResult batchB = mockMvc.perform(post("/api/admin/batches")
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgBId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Beta Batch\"}"))
            .andExpect(status().isOk())
            .andReturn();
        String batchBId = MAPPER.readTree(batchB.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/admin/batches/" + batchBId + "/assignments")
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgBId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"problemIds\":[\"" + problemAId + "\"]}"))
            .andExpect(status().isBadRequest());

        // Public get never returns hidden tests even for authorized members.
        MvcResult memberDetail = mockMvc.perform(get("/api/problems/" + problemAId)
                .header("Authorization", studentToken))
            .andExpect(status().isOk())
            .andReturn();
        assertFalse(memberDetail.getResponse().getContentAsString().contains("hiddenTestCases"));
        assertFalse(memberDetail.getResponse().getContentAsString().contains("\"input\":\"2\""));
    }

    private String createOrg(String adminToken, String name, String slug, String ownerUserId) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/organizations")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\",\"ownerUserId\":\"" + ownerUserId + "\",\"category\":\"college\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(OrganizationStatus.ACTIVE.name()))
            .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString()).get("id").asText();
    }

    private String contestBody(String title, String batchId, String problemId) {
        return "{"
            + "\"title\":\"" + title + "\","
            + "\"description\":\"isolated\","
            + "\"startAt\":\"" + START + "\","
            + "\"endAt\":\"" + END + "\","
            + "\"audienceType\":\"BATCHES\","
            + "\"batchIds\":[\"" + batchId + "\"],"
            + "\"problemIds\":[\"" + problemId + "\"]"
            + "}";
    }

    private String minimalProblemJson(String title) {
        return "{"
            + "\"title\":\"" + title + "\","
            + "\"statement\":\"Solve it\","
            + "\"difficulty\":\"EASY\","
            + "\"availability\":\"PUBLIC\","
            + "\"executionMode\":\"STANDARD_PER_CASE\","
            + "\"publicTestCases\":[{\"input\":\"1\",\"output\":\"1\"}],"
            + "\"hiddenTestCases\":[{\"input\":\"2\",\"output\":\"2\"}]"
            + "}";
    }

    private String minimalProblemJsonWithId(String id, String title) {
        return "{"
            + "\"id\":\"" + id + "\","
            + "\"title\":\"" + title + "\","
            + "\"statement\":\"Solve it\","
            + "\"difficulty\":\"EASY\","
            + "\"availability\":\"PUBLIC\","
            + "\"executionMode\":\"STANDARD_PER_CASE\","
            + "\"publicTestCases\":[{\"input\":\"1\",\"output\":\"1\"}],"
            + "\"hiddenTestCases\":[{\"input\":\"2\",\"output\":\"2\"}]"
            + "}";
    }

    private User saveUser(String username, String role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1!"));
        user.setRole(role);
        user.setEmailVerified(true);
        user.setAccountStatus("ACTIVE");
        user.setCreatedAt(new Date());
        return mongoTemplate.save(user);
    }

    private String token(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
    }
}
