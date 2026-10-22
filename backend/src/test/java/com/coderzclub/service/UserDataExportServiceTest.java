package com.coderzclub.service;

import com.coderzclub.model.Submission;
import com.coderzclub.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserDataExportServiceTest {
    @Test
    void exportOmitsSecretsAndDoesNotIncludeOtherUsers() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        User user = new User();
        user.setId("u1");
        user.setUsername("alice");
        user.setEmail("a@b.c");
        user.setPasswordHash("hash");
        user.setPasswordResetToken("reset");
        user.setEmailVerificationToken("verify");
        Submission own = new Submission();
        own.setId("s1");
        own.setUserId("u1");
        own.setCode("int x = 1;");
        when(mongo.find(any(Query.class), eq(Submission.class))).thenReturn(List.of(own));
        when(mongo.find(any(Query.class), eq(com.coderzclub.model.BatchMember.class))).thenReturn(List.of());

        Map<String, Object> payload = new UserDataExportService(mongo).export(user);
        @SuppressWarnings("unchecked")
        Map<String, Object> account = (Map<String, Object>) payload.get("account");
        assertEquals("alice", account.get("username"));
        assertFalse(account.containsKey("passwordHash"));
        assertFalse(account.containsKey("passwordResetToken"));
        assertFalse(account.containsKey("emailVerificationToken"));
        assertFalse(account.containsKey("jwt"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> submissions = (List<Map<String, Object>>) payload.get("submissions");
        assertEquals(1, submissions.size());
        assertEquals("s1", submissions.get(0).get("id"));
        assertTrue(submissions.get(0).containsKey("code"));
    }
}
