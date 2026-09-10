package com.coderzclub.service;

import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubmissionJobAccessServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final SubmissionJobAccessService access = new SubmissionJobAccessService(users);

    @Test
    void jobOwnerCanSubscribe() {
        User owner = user("owner-id", "owner");
        when(users.findByUsername("owner")).thenReturn(Optional.of(owner));
        SubmissionJob job = job("owner-id");

        assertTrue(access.canView(authentication("owner", "ROLE_USER"), job));
    }

    @Test
    void anotherUserCannotSubscribe() {
        User other = user("other-id", "other");
        when(users.findByUsername("other")).thenReturn(Optional.of(other));
        SubmissionJob job = job("owner-id");

        assertFalse(access.canView(authentication("other", "ROLE_USER"), job));
    }

    @Test
    void adminMaySubscribeToAnyJobByExplicitPolicy() {
        SubmissionJob job = job("owner-id");

        assertTrue(access.canView(authentication("admin", "ROLE_ADMIN"), job));
    }

    private static SubmissionJob job(String userId) {
        SubmissionJob job = new SubmissionJob();
        job.setUserId(userId);
        return job;
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static UsernamePasswordAuthenticationToken authentication(String username, String role) {
        return new UsernamePasswordAuthenticationToken(username, null,
            List.of(new SimpleGrantedAuthority(role)));
    }
}