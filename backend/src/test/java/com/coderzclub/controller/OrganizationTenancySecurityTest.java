package com.coderzclub.controller;

import com.coderzclub.config.JwtUtil;
import com.coderzclub.model.Organization;
import com.coderzclub.model.OrganizationMember;
import com.coderzclub.model.OrganizationMemberStatus;
import com.coderzclub.model.OrganizationRole;
import com.coderzclub.model.OrganizationStatus;
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

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrganizationTenancySecurityTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> "mongodb://127.0.0.1:27017/coderzclub_org_tenancy");
        registry.add("jwt.secret", () -> "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        registry.add("spring.mail.enabled", () -> "false");
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
    void platformAdminCreatesOrgAndCrossTenantAccessIsDenied() throws Exception {
        User platformAdmin = saveUser("platform-admin", "ADMIN");
        User orgAOwner = saveUser("owner-a", "USER");
        User orgBOwner = saveUser("owner-b", "USER");
        User studentA = saveUser("student-a", "USER");
        User outsider = saveUser("outsider", "USER");

        String adminToken = token(platformAdmin);
        String orgAId = createOrg(adminToken, "Alpha College", "alpha-college", orgAOwner.getId());
        String orgBId = createOrg(adminToken, "Beta Institute", "beta-institute", orgBOwner.getId());

        String ownerAToken = token(orgAOwner);
        mockMvc.perform(post("/api/organizations/" + orgAId + "/members")
                .header("Authorization", ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + studentA.getId() + "\",\"roles\":[\"STUDENT\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("STUDENT"));

        // Org A admin cannot read Org B members.
        mockMvc.perform(get("/api/organizations/" + orgBId + "/members")
                .header("Authorization", ownerAToken))
            .andExpect(status().isForbidden());

        // Org A admin cannot mutate Org B membership.
        mockMvc.perform(post("/api/organizations/" + orgBId + "/members")
                .header("Authorization", ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + studentA.getId() + "\",\"roles\":[\"ORG_ADMIN\"]}"))
            .andExpect(status().isForbidden());

        // Student cannot grant themselves administrator roles.
        String studentToken = token(studentA);
        mockMvc.perform(put("/api/organizations/" + orgAId + "/members/" + studentA.getId() + "/roles")
                .header("Authorization", studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"ORG_ADMIN\"]}"))
            .andExpect(status().isForbidden());

        // Organization roles cannot escalate to platform ADMIN.
        mockMvc.perform(post("/api/organizations/" + orgAId + "/members")
                .header("Authorization", ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + studentA.getId() + "\",\"roles\":[\"ADMIN\"]}"))
            .andExpect(status().isForbidden());

        // Spoofed organization context is rejected for non-members.
        mockMvc.perform(get("/api/organizations/active-context")
                .header("Authorization", token(outsider))
                .header("X-Organization-Id", orgAId))
            .andExpect(status().isForbidden());

        // Multi-org user only sees authorized memberships.
        mockMvc.perform(post("/api/organizations/" + orgBId + "/members")
                .header("Authorization", token(orgBOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + studentA.getId() + "\",\"roles\":[\"INSTRUCTOR\"]}"))
            .andExpect(status().isOk());
        MvcResult mine = mockMvc.perform(get("/api/organizations")
                .header("Authorization", studentToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode organizations = MAPPER.readTree(mine.getResponse().getContentAsString()).get("organizations");
        assertEquals(2, organizations.size());
        assertTrue(organizations.toString().contains(orgAId));
        assertTrue(organizations.toString().contains(orgBId));

        // Membership removal revokes access.
        mockMvc.perform(delete("/api/organizations/" + orgAId + "/members/" + studentA.getId())
                .header("Authorization", ownerAToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REMOVED"));
        mockMvc.perform(get("/api/organizations/" + orgAId)
                .header("Authorization", studentToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/organizations/active-context")
                .header("Authorization", studentToken)
                .header("X-Organization-Id", orgAId))
            .andExpect(status().isForbidden());

        // Platform admin retains intended privileges.
        mockMvc.perform(get("/api/organizations/" + orgBId + "/members")
                .header("Authorization", adminToken))
            .andExpect(status().isOk());

        // Account role on the student remains USER after org admin attempts.
        User refreshed = mongoTemplate.findById(studentA.getId(), User.class);
        assertEquals("USER", refreshed.getRole());
        assertFalse("ADMIN".equalsIgnoreCase(refreshed.getRole()));
    }

    @Test
    void orgOwnerCanUpdateSafeBrandingAndCrossTenantUpdateIsDenied() throws Exception {
        User platformAdmin = saveUser("brand-admin", "ADMIN");
        User ownerA = saveUser("brand-owner-a", "USER");
        User ownerB = saveUser("brand-owner-b", "USER");
        String adminToken = token(platformAdmin);
        String orgAId = createOrg(adminToken, "Brand Alpha", "brand-alpha", ownerA.getId());
        String orgBId = createOrg(adminToken, "Brand Beta", "brand-beta", ownerB.getId());
        String ownerAToken = token(ownerA);
        String ownerBToken = token(ownerB);

        mockMvc.perform(patch("/api/organizations/" + orgAId)
                .header("Authorization", ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alpha Renamed\",\"branding\":{\"displayName\":\"Alpha Labs\",\"logoUrl\":\"https://cdn.example.com/a.png\",\"primaryColor\":\"#123456\",\"faviconUrl\":\"javascript:alert(1)\"}}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/organizations/" + orgAId)
                .header("Authorization", ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alpha Renamed\",\"branding\":{\"displayName\":\"Alpha Labs\",\"logoUrl\":\"https://cdn.example.com/a.png\",\"primaryColor\":\"#123456\",\"faviconUrl\":\"https://cdn.example.com/a.ico\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Alpha Renamed"))
            .andExpect(jsonPath("$.branding.displayName").value("Alpha Labs"))
            .andExpect(jsonPath("$.branding.logoUrl").value("https://cdn.example.com/a.png"))
            .andExpect(jsonPath("$.branding.primaryColor").value("#123456"));

        mockMvc.perform(patch("/api/organizations/" + orgAId)
                .header("Authorization", ownerBToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hijacked\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/organizations/" + orgBId)
                .header("Authorization", ownerBToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Brand Beta"));
    }

    @Test
    void legacyNullOrganizationResourcesRemainCompatible() {
        OrganizationResourceProbe probe = new OrganizationResourceProbe();
        assertTrue(probe.allowsLegacy(null));
        assertTrue(probe.allowsLegacy(""));
        assertFalse(probe.sameTenant(null, "org-1"));
    }

    private String createOrg(String adminToken, String name, String slug, String ownerUserId) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/organizations")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\",\"ownerUserId\":\"" + ownerUserId + "\",\"category\":\"college\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value(slug))
            .andExpect(jsonPath("$.status").value(OrganizationStatus.ACTIVE.name()))
            .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString()).get("id").asText();
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

    /**
     * Local stand-in proving compatibility helpers treat null tenant ids as legacy platform scope.
     */
    private static final class OrganizationResourceProbe {
        private final com.coderzclub.service.OrganizationResourceCompatibility compatibility =
            new com.coderzclub.service.OrganizationResourceCompatibility();

        boolean allowsLegacy(String organizationId) {
            return compatibility.isLegacyPlatformScoped(organizationId);
        }

        boolean sameTenant(String left, String right) {
            return compatibility.sameTenant(left, right);
        }
    }
}
