package com.coderzclub.controller;

import com.coderzclub.config.JwtUtil;
import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchAssignment;
import com.coderzclub.model.BatchCoordinator;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
import com.coderzclub.model.User;
import com.coderzclub.service.BatchCoordinatorService;
import com.coderzclub.service.BatchReportService;
import com.coderzclub.service.BatchService;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BatchManagementValidationTest {

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> "mongodb://127.0.0.1:27017/coderzclub_batch_validation");
        registry.add("jwt.secret", () -> "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private BatchService batchService;
    @Autowired private BatchCoordinatorService coordinators;

    @AfterEach
    void dropIsolatedDatabase() {
        mongoTemplate.getDb().drop();
    }

    @Test
    void adminWorkflowCoversMembershipAssignmentsReportsAndArchive() throws Exception {
        User admin = saveUser("validate-admin", "ADMIN");
        User student = saveUser("validate-student", "USER");
        User other = saveUser("validate-other", "USER");
        String adminToken = token(admin);

        MvcResult created = mockMvc.perform(post("/api/admin/batches")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Validation Batch\",\"description\":\"first\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Validation Batch"))
            .andExpect(jsonPath("$.viewerRole").value("ADMIN"))
            .andReturn();
        String batchId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/admin/batches/" + batchId)
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Validation Batch\",\"description\":\"updated\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("updated"));

        mockMvc.perform(get("/api/admin/batches/" + batchId + "/report")
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.studentCount").value(0))
            .andExpect(jsonPath("$.summary.assignedProblemCount").value(0));

        mockMvc.perform(post("/api/admin/batches/" + batchId + "/members")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[\"" + student.getId() + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members.length()").value(1))
            .andExpect(jsonPath("$.alreadyMemberIds.length()").value(0));

        mockMvc.perform(post("/api/admin/batches/" + batchId + "/members")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[\"" + student.getId() + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alreadyMemberIds[0]").value(student.getId()));
        assertEquals(1L, mongoTemplate.count(Query.query(Criteria.where("batchId").is(batchId)), BatchMember.class));

        mockMvc.perform(get("/api/admin/batches/users")
                .header("Authorization", adminToken)
                .param("search", "validate-stu")
                .param("batchId", batchId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.users[0].username").value("validate-student"))
            .andExpect(jsonPath("$.users[0].alreadyMember").value(true));

        mockMvc.perform(get("/api/admin/batches/" + batchId + "/members")
                .header("Authorization", adminToken)
                .param("search", "validate-stu"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members.length()").value(1))
            .andExpect(jsonPath("$.members[0].username").value("validate-student"));

        mockMvc.perform(post("/api/admin/batches/" + batchId + "/members/import")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernames\":[\"validate-other\",\"validate-student\",\"missing-person\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.added.length()").value(1))
            .andExpect(jsonPath("$.alreadyMembers.length()").value(1))
            .andExpect(jsonPath("$.notFound[0]").value("missing-person"));
        assertEquals(2L, mongoTemplate.count(Query.query(Criteria.where("batchId").is(batchId)), BatchMember.class));

        mockMvc.perform(delete("/api/admin/batches/" + batchId + "/members/" + other.getId())
                .header("Authorization", adminToken))
            .andExpect(status().isOk());
        assertNotNull(mongoTemplate.findById(other.getId(), User.class));
        assertEquals(0L, mongoTemplate.count(
            Query.query(Criteria.where("batchId").is(batchId).and("userId").is(other.getId())),
            BatchMember.class));

        Problem problem = new Problem();
        problem.setTitle("Validation problem");
        problem.setNumericId(91001);
        problem.setDifficulty("EASY");
        problem = mongoTemplate.save(problem);
        mockMvc.perform(post("/api/admin/batches/" + batchId + "/assignments")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"problemIds\":[\"" + problem.getId() + "\"]}"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/batches/" + batchId + "/assignments")
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignments[0].title").value("Validation problem"));

        Submission accepted = new Submission();
        accepted.setUserId(student.getId());
        accepted.setProblemId(problem.getId());
        accepted.setResult("ACCEPTED");
        mongoTemplate.save(accepted);

        mockMvc.perform(get("/api/admin/batches/" + batchId + "/report")
                .header("Authorization", adminToken)
                .param("student", "validate-student")
                .param("problemId", problem.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.solvedCells").value(1))
            .andExpect(jsonPath("$.students[0].username").value("validate-student"));

        MvcResult csv = mockMvc.perform(get("/api/admin/batches/" + batchId + "/report.csv")
                .header("Authorization", adminToken)
                .param("student", "validate-student")
                .param("problemId", String.valueOf(problem.getNumericId())))
            .andExpect(status().isOk())
            .andReturn();
        String body = csv.getResponse().getContentAsString();
        assertTrue(body.contains("validate-student"));
        assertTrue(body.contains("SOLVED"));

        mockMvc.perform(delete("/api/admin/batches/" + batchId + "/assignments/" + problem.getId())
                .header("Authorization", adminToken))
            .andExpect(status().isOk());
        assertEquals(0L, mongoTemplate.count(Query.query(Criteria.where("batchId").is(batchId)), BatchAssignment.class));
        assertNotNull(mongoTemplate.findById(problem.getId(), Problem.class));

        mockMvc.perform(post("/api/admin/batches/" + batchId + "/archive")
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/admin/batches/" + batchId + "/report")
                .header("Authorization", adminToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/batches/" + batchId + "/members")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[\"" + other.getId() + "\"]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void coordinatorAccessIsIsolatedAndRevocationIsImmediate() throws Exception {
        User admin = saveUser("isolation-admin", "ADMIN");
        User coordA = saveUser("coord-a", "USER");
        User coordB = saveUser("coord-b", "USER");
        User student = saveUser("isolation-student", "USER");
        Batch batchA = batchService.create("Batch A", "a", admin.getId());
        Batch batchB = batchService.create("Batch B", "b", admin.getId());
        coordinators.grant(batchA.getId(), coordA.getId(), "North Institute", admin.getId());
        coordinators.grant(batchB.getId(), coordB.getId(), "South Institute", admin.getId());
        String tokenA = token(coordA);
        String tokenB = token(coordB);
        String studentToken = token(student);

        mockMvc.perform(get("/api/my/batches").header("Authorization", tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.batches.length()").value(1))
            .andExpect(jsonPath("$.batches[0].batchId").value(batchA.getId()));

        mockMvc.perform(get("/api/admin/batches/" + batchA.getId()).header("Authorization", tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.viewerRole").value("COORDINATOR"));
        mockMvc.perform(get("/api/admin/batches/" + batchB.getId()).header("Authorization", tokenA))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/batches/" + batchB.getId() + "/report").header("Authorization", tokenA))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/batches/" + batchB.getId() + "/report.csv").header("Authorization", tokenA))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/batches/" + batchB.getId() + "/members")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[\"" + student.getId() + "\"]}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/batches").header("Authorization", tokenA))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/contests").header("Authorization", tokenA))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/batches/" + batchA.getId() + "/coordinators")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + student.getId() + "\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/batches/" + batchA.getId() + "/coordinators/" + coordA.getId())
                .header("Authorization", tokenA))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/batches/users")
                .header("Authorization", tokenA)
                .param("search", "iso")
                .param("batchId", batchB.getId()))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/batches/" + batchA.getId()).header("Authorization", studentToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/batches/" + batchA.getId() + "/report").header("Authorization", studentToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/batches/" + batchA.getId() + "/report.csv").header("Authorization", studentToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/my/batches").header("Authorization", studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.batches.length()").value(0));

        mockMvc.perform(get("/api/admin/batches/" + batchB.getId()).header("Authorization", tokenB))
            .andExpect(status().isOk());

        coordinators.revoke(batchA.getId(), coordA.getId());
        mockMvc.perform(get("/api/admin/batches/" + batchA.getId()).header("Authorization", tokenA))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/batches/" + batchA.getId() + "/report.csv").header("Authorization", tokenA))
            .andExpect(status().isForbidden());
        assertEquals(0L, mongoTemplate.count(
            Query.query(Criteria.where("batchId").is(batchA.getId()).and("userId").is(coordA.getId())),
            BatchCoordinator.class));
    }

    @Test
    void coordinatorIndexExistsAndReportsStayBounded() throws Exception {
        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(
            mongoTemplate.getConverter().getMappingContext());
        for (IndexDefinition index : resolver.resolveIndexFor(BatchCoordinator.class)) {
            mongoTemplate.indexOps(BatchCoordinator.class).ensureIndex(index);
        }
        List<IndexInfo> indexes = mongoTemplate.indexOps(BatchCoordinator.class).getIndexInfo();
        boolean uniqueMembership = indexes.stream().anyMatch(index -> {
            if (!index.isUnique()) {
                return false;
            }
            boolean batch = false;
            boolean user = false;
            for (var field : index.getIndexFields()) {
                if ("batchId".equals(field.getKey())) batch = true;
                if ("userId".equals(field.getKey())) user = true;
            }
            return batch && user;
        });
        assertTrue(uniqueMembership, indexes.toString());

        User admin = saveUser("scale-admin", "ADMIN");
        for (int size : List.of(0, 100, 500, 2000)) {
            Batch batch = batchService.create("Scale " + size, "synthetic", admin.getId());
            if (size > 0) {
                List<User> users = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    User user = new User();
                    user.setUsername("scale-" + size + "-" + i);
                    user.setEmail("scale-" + size + "-" + i + "@validation.local");
                    user.setRole("USER");
                    user.setEmailVerified(true);
                    user.setAccountStatus("ACTIVE");
                    users.add(user);
                }
                mongoTemplate.insert(users, User.class);
                List<BatchMember> members = new ArrayList<>();
                for (User user : users) {
                    BatchMember member = new BatchMember();
                    member.setBatchId(batch.getId());
                    member.setUserId(user.getId());
                    members.add(member);
                }
                mongoTemplate.insert(members, BatchMember.class);
            }
            long started = System.nanoTime();
            var report = new BatchReportService(mongoTemplate, batchService).report(batch.getId(), 0, 20, null);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            assertEquals(size, ((Number) ((java.util.Map<?, ?>) report.get("summary")).get("studentCount")).intValue());
            assertEquals(size == 0 ? 0 : 20, ((List<?>) report.get("students")).size());
            assertTrue(elapsedMs < 20_000, "report for " + size + " members took " + elapsedMs + "ms");
            var page = batchService.listMembers(batch.getId(), 0, 20, null);
            assertEquals(size, ((Number) page.get("totalItems")).intValue());
        }

        Document explained = mongoTemplate.executeCommand(new Document("explain",
            new Document("find", "batch_coordinators")
                .append("filter", new Document("batchId", "missing").append("userId", "missing")))
            .append("verbosity", "queryPlanner"));
        assertTrue(explained.toJson().contains("IXSCAN"), explained.toJson());
    }

    private User saveUser(String username, String role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@validation.local");
        user.setRole(role);
        user.setEmailVerified(true);
        user.setAccountStatus("ACTIVE");
        return mongoTemplate.save(user);
    }

    private String token(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
    }
}
