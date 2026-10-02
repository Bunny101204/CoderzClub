package com.coderzclub.service;

import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServicePasswordResetTest {
    private UserRepository users;
    private UserService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "resetTokenExpirationMs", 3_600_000L);
    }

    @Test
    void issuesTokenForEligibleAccount() {
        User user = user("u1", "a@b.c", "ACTIVE");
        when(users.findByEmail("a@b.c")).thenReturn(Optional.of(user));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Optional<User> result = service.createPasswordResetToken("a@b.c");
        assertTrue(result.isPresent());
        assertEquals("a@b.c", result.get().getEmail());
        assertTrue(result.get().getPasswordResetToken() != null && !result.get().getPasswordResetToken().isBlank());
        verify(users).save(user);
    }

    @Test
    void unknownEmailCreatesNoToken() {
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertTrue(service.createPasswordResetToken("missing@example.com").isEmpty());
        verify(users, never()).save(any());
    }

    @Test
    void deletedAccountCreatesNoToken() {
        User user = user("u1", "gone@example.com", "DELETED");
        when(users.findByEmail("gone@example.com")).thenReturn(Optional.of(user));
        assertTrue(service.createPasswordResetToken("gone@example.com").isEmpty());
        verify(users, never()).save(any());
    }

    private static User user(String id, String email, String status) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setAccountStatus(status);
        return user;
    }
}
