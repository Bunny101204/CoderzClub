package com.coderzclub.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountRolesTest {
    @Test
    void adminAssignmentAcceptsOnlyUserAndAdmin() {
        assertEquals("USER", AccountRoles.assignableRole("user"));
        assertEquals("ADMIN", AccountRoles.assignableRole(" admin "));
    }

    @Test
    void unknownRoleIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AccountRoles.assignableRole("superadmin"));
        assertThrows(IllegalArgumentException.class, () -> AccountRoles.assignableRole(null));
        assertThrows(IllegalArgumentException.class, () -> AccountRoles.assignableRole("  "));
    }
}
