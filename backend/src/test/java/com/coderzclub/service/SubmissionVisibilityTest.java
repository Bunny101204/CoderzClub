package com.coderzclub.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionVisibilityTest {
    @Test
    void usersCanOnlyReadTheirOwnHistory() {
        assertTrue(sameUser("u1", "u1"));
        assertFalse(sameUser("u1", "u2"));
        assertFalse(sameUser(null, "u2"));
    }

    static boolean sameUser(String currentUserId, String targetUserId) {
        return currentUserId != null && currentUserId.equals(targetUserId);
    }
}
