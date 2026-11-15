package com.coderzclub.controller;

import com.coderzclub.config.JwtUtil;
import com.coderzclub.model.Batch;
import com.coderzclub.model.Contest;
import com.coderzclub.model.ContestAudienceType;
import com.coderzclub.model.OrganizationStatus;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.User;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrganizationBatchContestIsolationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant START = Instant.parse("2026-12-01T10:00:00Z");
    private static final Instant END = Instant.parse("2026-12-01T12:00:00Z");

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> "mongodb://127.0.0.1:27017/coderzclub_org_batch_contest");
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
    void orgBatchAndContestIsolationDeniesCrossTenantAccess() throws Exception {
        User platformAdmin = saveUser("platform-admin", "ADMIN");
        User ownerA = saveUser("owner-a", "USER");
        User ownerB = saveUser("owner-b", "USER");
        User studentA = saveUser("student-a", "USER");
        User outsider = saveUser("outsider", "USER");

        String adminToken = token(platformAdmin);
        String orgAId = createOrg(adminToken, "Alpha College", "alpha-college", ownerA.getId());
        String orgBId = createOrg(adminToken, "Beta Institute", "beta-institute", ownerB.getId());
        String ownerAToken = token(ownerA);
        String ownerBToken = token(ownerB);

        mockMvc.perform(post("/api/organizations/" + orgAId + "/members")
                .header("Authorization", ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + studentA.getId() + "\",\"roles\":[\"STUDENT\"]}"))
            .andExpect(status().isOk());

        // Org A creates a tenant batch; Org B cannot list or read it.
        MvcResult batchCreated = mockMvc.perform(post("/api/admin/batches")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alpha DSA\",\"description\":\"org A only\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organizationId").value(orgAId))
            .andReturn();
        String batchAId = MAPPER.readTree(batchCreated.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/admin/batches/" + batchAId + "/members")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[\"" + studentA.getId() + "\"]}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/batches")
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgBId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));

        mockMvc.perform(get("/api/admin/batches/" + batchAId)
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgBId))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/batches/" + batchAId)
                .header("Authorization", token(outsider)))
            .andExpect(status().isNotFound());

        // Cross-tenant report and CSV exports are denied without leaking existence.
        mockMvc.perform(get("/api/admin/batches/" + batchAId + "/report")
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgBId))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/batches/" + batchAId + "/report.csv")
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgBId))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/batches/" + batchAId + "/report")
                .header("Authorization", token(outsider)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/batches/" + batchAId + "/report.csv")
                .header("Authorization", token(studentA)))
            .andExpect(status().isNotFound());

        // Org A staff can read their own report surface.
        mockMvc.perform(get("/api/admin/batches/" + batchAId + "/report")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/batches/" + batchAId + "/report.csv")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId))
            .andExpect(status().isOk());

        // Spoofed organization header is rejected for non-members.
        mockMvc.perform(get("/api/admin/batches")
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgAId))
            .andExpect(status().isForbidden());

        // Students cannot create organization batches.
        mockMvc.perform(post("/api/admin/batches")
                .header("Authorization", token(studentA))
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hijack\"}"))
            .andExpect(status().isForbidden());

        Problem problem = saveProblem("Two Sum");
        MvcResult contestCreated = mockMvc.perform(post("/api/admin/contests")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(contestBody("Alpha Contest", batchAId, problem.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organizationId").value(orgAId))
            .andReturn();
        String contestAId = MAPPER.readTree(contestCreated.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/admin/contests")
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgBId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));

        mockMvc.perform(get("/api/admin/contests/" + contestAId)
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgBId))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/contests/" + contestAId + "/publish")
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgBId))
            .andExpect(status().isNotFound());

        // Org A can publish its own contest; outsiders still cannot see it in the public list.
        mockMvc.perform(post("/api/admin/contests/" + contestAId + "/publish")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.published").value(true));

        MvcResult outsiderList = mockMvc.perform(get("/api/contests")
                .header("Authorization", token(outsider)))
            .andExpect(status().isOk())
            .andReturn();
        assertFalse(outsiderList.getResponse().getContentAsString().contains(contestAId));

        MvcResult memberList = mockMvc.perform(get("/api/contests")
                .header("Authorization", token(studentA)))
            .andExpect(status().isOk())
            .andReturn();
        assertTrue(memberList.getResponse().getContentAsString().contains(contestAId));

        // Legacy platform batches remain creatable without an organization header.
        MvcResult legacy = mockMvc.perform(post("/api/admin/batches")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Platform Legacy\"}"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode legacyBody = MAPPER.readTree(legacy.getResponse().getContentAsString());
        assertTrue(legacyBody.get("organizationId").isNull() || !legacyBody.hasNonNull("organizationId"));
        Batch storedLegacy = mongoTemplate.findById(legacyBody.get("id").asText(), Batch.class);
        assertNull(storedLegacy.getOrganizationId());

        Contest storedContest = mongoTemplate.findById(contestAId, Contest.class);
        assertEquals(orgAId, storedContest.getOrganizationId());
        assertEquals(List.of(batchAId), storedContest.getBatchIds());
    }

    @Test
    void orgBatchEnrollmentSearchAndCoordinatorRevokeStayTenantScoped() throws Exception {
        User platformAdmin = saveUser("platform-admin-enroll", "ADMIN");
        User ownerA = saveUser("owner-enroll-a", "USER");
        User ownerB = saveUser("owner-enroll-b", "USER");
        User studentA = saveUser("student-enroll-a", "USER");
        User outsider = saveUser("outsider-enroll", "USER");
        User instructor = saveUser("instructor-enroll", "USER");

        String adminToken = token(platformAdmin);
        String orgAId = createOrg(adminToken, "Enroll Alpha", "enroll-alpha", ownerA.getId());
        String orgBId = createOrg(adminToken, "Enroll Beta", "enroll-beta", ownerB.getId());
        String ownerAToken = token(ownerA);
        String ownerBToken = token(ownerB);

        mockMvc.perform(post("/api/organizations/" + orgAId + "/members")
                .header("Authorization", ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + studentA.getId() + "\",\"roles\":[\"STUDENT\"]}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/organizations/" + orgAId + "/members")
                .header("Authorization", ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + instructor.getId() + "\",\"roles\":[\"INSTRUCTOR\"]}"))
            .andExpect(status().isOk());

        MvcResult batchCreated = mockMvc.perform(post("/api/admin/batches")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Enroll Batch\"}"))
            .andExpect(status().isOk())
            .andReturn();
        String batchId = MAPPER.readTree(batchCreated.getResponse().getContentAsString()).get("id").asText();

        // Outsider (not an org member) cannot be enrolled; response stays generic.
        mockMvc.perform(post("/api/admin/batches/" + batchId + "/members")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[\"" + outsider.getId() + "\"]}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/batches/" + batchId + "/members/import")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernames\":[\"" + outsider.getUsername() + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notFound[0]").value(outsider.getUsername()))
            .andExpect(jsonPath("$.added").isEmpty());

        mockMvc.perform(post("/api/admin/batches/" + batchId + "/members")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[\"" + studentA.getId() + "\"]}"))
            .andExpect(status().isOk());

        // Org B admin search cannot discover Org A student emails.
        MvcResult searchB = mockMvc.perform(get("/api/admin/batches/users")
                .param("search", "student-enroll")
                .header("Authorization", ownerBToken)
                .header("X-Organization-Id", orgBId))
            .andExpect(status().isOk())
            .andReturn();
        assertFalse(searchB.getResponse().getContentAsString().contains(studentA.getEmail()));

        mockMvc.perform(post("/api/admin/batches/" + batchId + "/coordinators")
                .header("Authorization", ownerAToken)
                .header("X-Organization-Id", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + instructor.getId() + "\"}"))
            .andExpect(status().isOk());
        String instructorToken = token(instructor);
        mockMvc.perform(get("/api/admin/batches/" + batchId + "/report")
                .header("Authorization", instructorToken))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/organizations/" + orgAId + "/members/" + instructor.getId())
                .header("Authorization", ownerAToken))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/batches/" + batchId + "/report")
                .header("Authorization", instructorToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void instructorNeedsCoordinatorGrantForOrgBatchAccess() throws Exception {
        User platformAdmin = saveUser("platform-admin-2", "ADMIN");
        User owner = saveUser("owner-inst", "USER");
        User instructor = saveUser("instructor-a", "USER");

        String adminToken = token(platformAdmin);
        String orgId = createOrg(adminToken, "Instructor College", "instructor-college", owner.getId());
        String ownerToken = token(owner);

        mockMvc.perform(post("/api/organizations/" + orgId + "/members")
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + instructor.getId() + "\",\"roles\":[\"INSTRUCTOR\"]}"))
            .andExpect(status().isOk());

        MvcResult batchCreated = mockMvc.perform(post("/api/admin/batches")
                .header("Authorization", ownerToken)
                .header("X-Organization-Id", orgId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Assigned Section\"}"))
            .andExpect(status().isOk())
            .andReturn();
        String batchId = MAPPER.readTree(batchCreated.getResponse().getContentAsString()).get("id").asText();

        String instructorToken = token(instructor);
        mockMvc.perform(get("/api/admin/batches/" + batchId + "/report")
                .header("Authorization", instructorToken)
                .header("X-Organization-Id", orgId))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/batches/" + batchId + "/coordinators")
                .header("Authorization", ownerToken)
                .header("X-Organization-Id", orgId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + instructor.getId() + "\",\"institution\":\"Instructor College\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/batches/" + batchId + "/report")
                .header("Authorization", instructorToken)
                .header("X-Organization-Id", orgId))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/batches/" + batchId + "/report.csv")
                .header("Authorization", instructorToken)
                .header("X-Organization-Id", orgId))
            .andExpect(status().isOk());
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

    private Problem saveProblem(String title) {
        Problem problem = new Problem();
        problem.setTitle(title);
        problem.setAvailability(ProblemAvailability.PUBLIC);
        problem.setDifficulty("EASY");
        return mongoTemplate.save(problem);
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
