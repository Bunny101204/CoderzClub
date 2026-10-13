package com.coderzclub.service;

import com.coderzclub.dto.RegisterRequest;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicRegistrationRoleTest {
    private UserRepository users;
    private UserService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        when(encoder.encode(anyString())).thenReturn("hash");
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void publicRegistrationAlwaysStoresUser() {
        User saved = service.registerUser("ada", "ada@example.com", "secret");
        assertEquals(AccountRoles.USER, saved.getRole());
        verify(users).save(saved);
    }

    @Test
    void publicRegistrationRoleConstantCannotBeAdmin() {
        assertEquals("USER", AccountRoles.publicRegistrationRole());
    }

    @Test
    void maliciousRoleFieldIsNotBoundOntoTheRegistrationRequest() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        RegisterRequest request = mapper.readValue(
            "{\"username\":\"mallory\",\"email\":\"mallory@example.com\",\"password\":\"secret\",\"role\":\"admin\"}",
            RegisterRequest.class
        );
        assertEquals("mallory", request.getUsername());
        User saved = service.registerUser(request.getUsername(), request.getEmail(), request.getPassword());
        assertEquals(AccountRoles.USER, saved.getRole());
    }
}
