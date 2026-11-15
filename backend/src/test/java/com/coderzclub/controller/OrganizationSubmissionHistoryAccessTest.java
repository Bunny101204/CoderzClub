package com.coderzclub.controller;

import com.coderzclub.config.JwtUtil;
import com.coderzclub.model.Organization;
import com.coderzclub.model.OrganizationMember;
import com.coderzclub.model.OrganizationMemberStatus;
import com.coderzclub.model.OrganizationStatus;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.Submission;
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

import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrganizationSubmissionHistoryAccessTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> "mongodb://127.0.0.1:27017/coderzclub_org_submission_history");
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
    void historicalSubmissionAccessFollowsConservativeOrgPolicy() throws Exception {
        User platformAdmin = saveUser("platform-admin", "ADMIN");
        User ownerA = saveUser("owner-a", "USER");
        User ownerB = saveUser("owner-b", "USER");
        User studentA = saveUser("student-a", "USER");
        User studentB = saveUser("student-b", "USER");
        User multiOrg = saveUser("multi-org", "USER");

        String adminToken = token(platformAdmin);
        String orgAId = createOrg(adminToken, "Alpha History", "alpha-history", ownerA.getId());
        String orgBId = createOrg(adminToken, "Beta History", "beta-history", ownerB.getId());
        String ownerAToken = token(ownerA);
        String ownerBToken = token(ownerB);
        String studentAToken = token(studentA);
        String studentBToken = token(studentB);
        String multiToken = token(multiOrg);

        mockMvc.perform(post("/api/organizations/" + orgAId + "/members")
                .header("Authorization", ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + studentA.getId() + "\",\"roles\":[\"STUDENT\"]}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/organizations/" + orgAId + "/members")
                .header("Authorization", ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + multiOrg.getId() + "\",\"roles\":[\"STUDENT\"]}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/organizations/" + orgBId + "/members")
                .header("Authorization", ownerBToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + multiOrg.getId() + "\",\"roles\":[\"STUDENT\"]}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/organizations/" + orgBId + "/members")
                .header("Authorization", ownerBToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + studentB.getId() + "\",\"roles\":[\"STUDENT\"]}"))
            .andExpect(status().isOk());

        MvcResult problemA = mockMvc.perform(post("/api/problems")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalProblemJson("Org A Private")))
            .andExpect(status().isOk())
            .andReturn();
        String problemAId = MAPPER.readTree(problemA.getResponse().getContentAsString()).get("id").asText();

        MvcResult problemB = mockMvc.perform(post("/api/problems")
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgBId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalProblemJson("Org B Private")))
            .andExpect(status().isOk())
            .andReturn();
        String problemBId = MAPPER.readTree(problemB.getResponse().getContentAsString()).get("id").asText();

        Problem publicProblem = new Problem();
        publicProblem.setTitle("Public Practice");
        publicProblem.setStatement("public");
        publicProblem.setDifficulty("EASY");
        publicProblem.setAvailability(ProblemAvailability.PUBLIC);
        publicProblem.setPoints(10);
        publicProblem = mongoTemplate.save(publicProblem);

        Submission orgASubmission = saveSubmission(studentA.getId(), problemAId, "code-a");
        Submission orgAMulti = saveSubmission(multiOrg.getId(), problemAId, "code-multi-a");
        Submission orgBMulti = saveSubmission(multiOrg.getId(), problemBId, "code-multi-b");
        Submission publicSubmission = saveSubmission(studentA.getId(), publicProblem.getId(), "code-public");
        Submission otherStudent = saveSubmission(studentB.getId(), problemBId, "code-other");

        // 1. Active member viewing their own submission
        mockMvc.perform(get("/api/submissions/" + orgASubmission.getId())
                .header("Authorization", studentAToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("code-a"));
        mockMvc.perform(get("/api/submissions/my-submissions")
                .header("Authorization", studentAToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.submissions[?(@.id=='" + orgASubmission.getId() + "')]").exists());

        // 6. Existing public-platform submission history remains available
        mockMvc.perform(get("/api/submissions/" + publicSubmission.getId())
                .header("Authorization", studentAToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("code-public"));

        // 4. Student cannot read another student's submission id
        mockMvc.perform(get("/api/submissions/" + otherStudent.getId())
                .header("Authorization", studentAToken))
            .andExpect(status().isNotFound());

        // 5. Org B member cannot access Org A submissions (even with forged header)
        mockMvc.perform(get("/api/submissions/" + orgASubmission.getId())
                .header("Authorization", studentBToken)
                .header("X-Organization-Id", orgAId))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/submissions/my-submissions")
                .header("Authorization", studentBToken)
                .header("X-Organization-Id", orgAId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.submissions[?(@.id=='" + orgASubmission.getId() + "')]").doesNotExist());

        // 8. Multiple-organization memberships keep access per org
        mockMvc.perform(get("/api/submissions/" + orgAMulti.getId())
                .header("Authorization", multiToken))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/submissions/" + orgBMulti.getId())
                .header("Authorization", multiToken))
            .andExpect(status().isOk());

        // 2 + 3. Membership removal revokes org-private history and problem content
        mockMvc.perform(delete("/api/organizations/" + orgAId + "/members/" + studentA.getId())
                .header("Authorization", ownerAToken))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/submissions/" + orgASubmission.getId())
                .header("Authorization", studentAToken)
                .header("X-Organization-Id", orgAId))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/submissions/my-submissions")
                .header("Authorization", studentAToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.submissions[?(@.id=='" + orgASubmission.getId() + "')]").doesNotExist())
            .andExpect(jsonPath("$.submissions[?(@.id=='" + publicSubmission.getId() + "')]").exists());
        mockMvc.perform(get("/api/problems/" + problemAId)
                .header("Authorization", studentAToken)
                .header("X-Organization-Id", orgAId))
            .andExpect(status().isNotFound());

        // Multi-org user removed from A loses A history but keeps B
        mockMvc.perform(delete("/api/organizations/" + orgAId + "/members/" + multiOrg.getId())
                .header("Authorization", ownerAToken))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/submissions/" + orgAMulti.getId())
                .header("Authorization", multiToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/submissions/" + orgBMulti.getId())
                .header("Authorization", multiToken))
            .andExpect(status().isOk());

        // 7. Suspended organization behavior
        Organization orgB = mongoTemplate.findById(orgBId, Organization.class);
        orgB.setStatus(OrganizationStatus.SUSPENDED.name());
        mongoTemplate.save(orgB);
        mockMvc.perform(get("/api/submissions/" + orgBMulti.getId())
                .header("Authorization", multiToken)
                .header("X-Organization-Id", orgBId))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/problems/" + problemBId)
                .header("Authorization", multiToken)
                .header("X-Organization-Id", orgBId))
            .andExpect(status().isNotFound());

        // Membership row alone does not bypass suspension
        OrganizationMember membership = mongoTemplate.findOne(
            org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("organizationId").is(orgBId)
                    .and("userId").is(multiOrg.getId())),
            OrganizationMember.class);
        org.junit.jupiter.api.Assertions.assertNotNull(membership);
        org.junit.jupiter.api.Assertions.assertEquals(
            OrganizationMemberStatus.ACTIVE.name(), membership.getStatus());
    }

    private String createOrg(String adminToken, String name, String slug, String ownerUserId) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/organizations")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\",\"ownerUserId\":\"" + ownerUserId + "\",\"category\":\"college\"}"))
            .andExpect(status().isOk())
            .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString()).get("id").asText();
    }

    private String minimalProblemJson(String title) {
        return "{"
            + "\"title\":\"" + title + "\","
            + "\"statement\":\"Solve it\","
            + "\"difficulty\":\"EASY\","
            + "\"availability\":\"PUBLIC\","
            + "\"points\":50,"
            + "\"executionMode\":\"STANDARD_PER_CASE\","
            + "\"publicTestCases\":[{\"input\":\"1\",\"output\":\"1\"}],"
            + "\"hiddenTestCases\":[{\"input\":\"2\",\"output\":\"2\"}]"
            + "}";
    }

    private Submission saveSubmission(String userId, String problemId, String code) {
        Submission submission = new Submission();
        submission.setUserId(userId);
        submission.setProblemId(problemId);
        submission.setCode(code);
        submission.setLanguage("java");
        submission.setResult("ACCEPTED");
        submission.setCreatedAt(new Date());
        return mongoTemplate.save(submission);
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
