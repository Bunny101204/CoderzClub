package com.coderzclub.service;

import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentAccountServiceTest {
    private MongoTemplate mongo;
    private UserRepository users;
    private StudentAccountService service;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        users = mock(UserRepository.class);
        service = new StudentAccountService(mongo, users);
    }

    @Test
    void lastAdministratorCannotBeDeactivated() {
        User admin = account("a1", "root", "ADMIN", "ACTIVE");
        when(users.findById("a1")).thenReturn(Optional.of(admin));
        when(mongo.count(any(Query.class), eq(User.class))).thenReturn(1L);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.setActive("a1", false, "other"));
        assertEquals("The last administrator cannot be deactivated or demoted.", error.getMessage());
    }

    @Test
    void deactivationDoesNotChangeRoleOrRewards() {
        User student = account("s1", "ada", "USER", "ACTIVE");
        student.setProblemsSolved(4);
        student.setTotalPoints(10);
        when(users.findById("s1")).thenReturn(Optional.of(student));
        when(users.save(student)).thenReturn(student);
        when(mongo.find(any(Query.class), eq(com.coderzclub.model.BatchMember.class))).thenReturn(java.util.List.of());
        service.setActive("s1", false, "root");
        assertEquals("INACTIVE", student.getAccountStatus());
        assertEquals("USER", student.getRole());
        assertEquals(4, student.getProblemsSolved());
        assertEquals(10, student.getTotalPoints());
        verify(users).save(student);
    }

    @Test
    void profileUpdateRejectsSecretsAndKeepsCredentials() {
        User student = account("s1", "ada", "USER", "ACTIVE");
        student.setPasswordHash("hash");
        student.setBio("old");
        when(users.findById("s1")).thenReturn(Optional.of(student));
        when(users.save(student)).thenReturn(student);
        when(mongo.find(any(Query.class), eq(com.coderzclub.model.BatchMember.class))).thenReturn(java.util.List.of());
        service.updateProfile("s1", "likes graphs", "Pune", "https://example.com");
        assertEquals("likes graphs", student.getBio());
        assertEquals("hash", student.getPasswordHash());
        assertEquals("USER", student.getRole());
    }

    private static User account(String id, String username, String role, String status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setRole(role);
        user.setAccountStatus(status);
        user.setEmailVerified(true);
        return user;
    }
}
