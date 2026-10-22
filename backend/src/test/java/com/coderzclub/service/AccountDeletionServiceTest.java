package com.coderzclub.service;

import com.coderzclub.model.BatchMember;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDeletionServiceTest {
    @Test
    void rejectsWrongConfirmationAndDoesNotMutate() {
        UserRepository users = mock(UserRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        User user = user("u1", "alice", "a@b.c");
        assertThrows(IllegalArgumentException.class,
            () -> new AccountDeletionService(users, mongo, encoder, mock(LeaderboardService.class)).deleteSelf(user, "please"));
        verify(users, never()).save(any());
    }

    @Test
    void anonymizesIdentityRemovesMembershipAndKeepsUserId() {
        UserRepository users = mock(UserRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hashed-disabled");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User user = user("u1", "alice", "alice@example.com");
        user.setBio("hello");
        user.setPasswordHash("old-hash");
        Map<String, Object> result = new AccountDeletionService(users, mongo, encoder, mock(LeaderboardService.class))
            .deleteSelf(user, "DELETE");
        assertEquals(true, result.get("deleted"));
        assertEquals("u1", result.get("userId"));
        assertEquals("DELETED", user.getAccountStatus());
        assertEquals("deleted-u1", user.getUsername());
        assertEquals("deleted-u1@users.invalid", user.getEmail());
        assertNotEquals("old-hash", user.getPasswordHash());
        assertNull(user.getBio());
        assertFalse(user.isEmailVerified());
        verify(mongo).remove(any(Query.class), eq(BatchMember.class));
        verify(users).save(user);
    }

    @Test
    void repeatedDeletionIsIdempotent() {
        UserRepository users = mock(UserRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        User user = user("u1", "deleted-u1", "deleted-u1@users.invalid");
        user.setAccountStatus("DELETED");
        Map<String, Object> result = new AccountDeletionService(users, mongo, encoder, mock(LeaderboardService.class)).delete(user);
        assertEquals(true, result.get("alreadyDeleted"));
        verify(users, never()).save(any());
        verify(mongo).remove(any(Query.class), eq(BatchMember.class));
    }

    @Test
    void userProfileResponseOmitsPasswordAndTokens() {
        User user = user("u1", "alice", "a@b.c");
        user.setPasswordHash("secret-hash");
        user.setPasswordResetToken("reset");
        user.setEmailVerificationToken("verify");
        Map<String, Object> profile = com.coderzclub.dto.UserProfileResponse.from(user);
        assertFalse(profile.containsKey("passwordHash"));
        assertFalse(profile.containsKey("passwordResetToken"));
        assertFalse(profile.containsKey("emailVerificationToken"));
        assertEquals("alice", profile.get("username"));
    }

    @Test
    void persistsDeletedStatusBeforeMembershipCleanup() {
        UserRepository users = mock(UserRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        LeaderboardService leaderboard = mock(LeaderboardService.class);
        when(encoder.encode(any())).thenReturn("hashed-disabled");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User user = user("u1", "alice", "alice@example.com");
        new AccountDeletionService(users, mongo, encoder, leaderboard).delete(user);
        var inOrder = org.mockito.Mockito.inOrder(users, leaderboard, mongo);
        inOrder.verify(users).save(user);
        inOrder.verify(leaderboard).removeParticipant("u1");
        inOrder.verify(mongo).remove(any(Query.class), eq(BatchMember.class));
    }

    @Test
    void missingAccountStatusIsNotTreatedAsDeleted() {
        User user = user("u1", "alice", "a@b.c");
        user.setAccountStatus(null);
        assertFalse(user.isDeleted());
    }

    private static User user(String id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setAccountStatus("ACTIVE");
        return user;
    }
}
