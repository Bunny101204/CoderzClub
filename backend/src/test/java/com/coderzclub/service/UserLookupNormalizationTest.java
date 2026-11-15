package com.coderzclub.service;

import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserLookupNormalizationTest {

    @Test
    void usernameLookupUsesExactTrimmedValue() {
        UserRepository users = mock(UserRepository.class);
        UserService service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", mock(PasswordEncoder.class));

        User stored = new User();
        stored.setUsername("alice");
        when(users.findByUsername("alice")).thenReturn(Optional.of(stored));

        assertEquals("alice", service.findByUsername("  alice  ").orElseThrow().getUsername());
        verify(users).findByUsername(eq("alice"));
    }

    @Test
    void emailLookupLowercasesBeforeExactMatch() {
        UserRepository users = mock(UserRepository.class);
        UserService service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", mock(PasswordEncoder.class));

        User stored = new User();
        stored.setEmail("alice@example.com");
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(stored));

        assertTrue(service.findByEmail("Alice@Example.COM").isPresent());
        verify(users).findByEmail(eq("alice@example.com"));
    }

    @Test
    void usernameOrEmailPrefersEmailPathWhenAtSignPresent() {
        UserRepository users = mock(UserRepository.class);
        UserService service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", mock(PasswordEncoder.class));

        when(users.findByEmail("bob@school.edu")).thenReturn(Optional.of(new User()));

        assertTrue(service.findByUsernameOrEmail("Bob@School.EDU").isPresent());
        verify(users).findByEmail(eq("bob@school.edu"));
        verify(users, never()).findByUsername(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void legacyMixedCaseUsernameFallsBackAfterExactMiss() {
        UserRepository users = mock(UserRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        UserService service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongo);
        ReflectionTestUtils.setField(service, "passwordEncoder", mock(PasswordEncoder.class));

        User legacy = new User();
        legacy.setUsername("Alice");
        when(users.findByUsername("alice")).thenReturn(Optional.empty());
        when(mongo.findOne(any(Query.class), eq(User.class))).thenReturn(legacy);

        assertEquals("Alice", service.findByUsername("alice").orElseThrow().getUsername());
        verify(users).findByUsername("alice");
        verify(mongo).findOne(any(Query.class), eq(User.class));
    }

    @Test
    void legacyMixedCaseEmailFallsBackAfterExactMiss() {
        UserRepository users = mock(UserRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        UserService service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongo);
        ReflectionTestUtils.setField(service, "passwordEncoder", mock(PasswordEncoder.class));

        User legacy = new User();
        legacy.setEmail("Alice@School.EDU");
        when(users.findByEmail("alice@school.edu")).thenReturn(Optional.empty());
        when(mongo.findOne(any(Query.class), eq(User.class))).thenReturn(legacy);

        assertTrue(service.findByEmail("  Alice@School.EDU  ").isPresent());
        verify(users).findByEmail("alice@school.edu");
        verify(mongo).findOne(any(Query.class), eq(User.class));
    }

    @Test
    void exactHitSkipsLegacyRegexFallback() {
        UserRepository users = mock(UserRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        UserService service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongo);
        ReflectionTestUtils.setField(service, "passwordEncoder", mock(PasswordEncoder.class));

        when(users.findByUsername("alice")).thenReturn(Optional.of(new User()));

        assertTrue(service.findByUsername("alice").isPresent());
        verify(mongo, never()).findOne(any(Query.class), eq(User.class));
    }

    @Test
    void registerRejectsCaseInsensitiveUsernameCollision() {
        UserRepository users = mock(UserRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserService service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongo);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "verificationTokenExpirationMs", 86_400_000L);

        User legacy = new User();
        legacy.setUsername("Alice");
        when(users.findByUsername("alice")).thenReturn(Optional.empty());
        when(mongo.findOne(any(Query.class), eq(User.class))).thenReturn(legacy);

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.registerUser("alice", "new@example.com", "Password1!"));
        assertTrue(ex.getMessage().toLowerCase().contains("username"));
        verify(users, never()).save(any());
    }

    @Test
    void whitespaceOnlyUsernameNormalizesToNull() {
        assertEquals(null, UserService.normalizeUsername("   "));
        assertEquals(null, UserService.normalizeEmail("\t"));
        assertEquals("Ada", UserService.normalizeUsername("  Ada  "));
        assertEquals("ada@ex.com", UserService.normalizeEmail("  Ada@Ex.COM  "));
    }
}
