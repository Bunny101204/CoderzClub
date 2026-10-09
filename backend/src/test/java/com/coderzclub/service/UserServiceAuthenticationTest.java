package com.coderzclub.service;

import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceAuthenticationTest {
    private UserRepository users;
    private UserService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", users);
    }

    @Test
    void uidLookupUsesFindByIdOnly() {
        User user = user("u1", "alice", "USER", "ACTIVE", true);
        when(users.findById("u1")).thenReturn(Optional.of(user));
        User loaded = service.loadUserForAuthentication("u1", "alice");
        assertEquals("u1", loaded.getId());
        verify(users, times(1)).findById("u1");
        verify(users, never()).findByUsername(anyString());
    }

    @Test
    void legacyLookupUsesFindByUsername() {
        User user = user("u1", "alice", "USER", "ACTIVE", true);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        User loaded = service.loadUserForAuthentication(null, "alice");
        assertEquals("alice", loaded.getUsername());
        verify(users).findByUsername("alice");
        verify(users, never()).findById(anyString());
    }

    @Test
    void deletedUserCannotAuthenticate() {
        User user = user("u1", "alice", "USER", "DELETED", true);
        when(users.findById("u1")).thenReturn(Optional.of(user));
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserForAuthentication("u1", "alice"));
    }

    @Test
    void missingUidUserCannotAuthenticate() {
        when(users.findById("missing")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserForAuthentication("missing", "alice"));
    }

    @Test
    void uidMustMatchJwtUsername() {
        User user = user("u1", "alice", "USER", "ACTIVE", true);
        when(users.findById("u1")).thenReturn(Optional.of(user));
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserForAuthentication("u1", "bob"));
    }

    @Test
    void databaseRoleDeterminesAuthorityAndUnverifiedIsDisabled() {
        User admin = user("u1", "alice", "admin", "ACTIVE", false);
        UserDetails details = service.toUserDetails(admin);
        assertTrue(details.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        assertEquals(false, details.isEnabled());
    }

    private static User user(String id, String username, String role, String status, boolean verified) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setAccountStatus(status);
        user.setEmailVerified(verified);
        return user;
    }
}
