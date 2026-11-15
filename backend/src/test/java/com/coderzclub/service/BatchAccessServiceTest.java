package com.coderzclub.service;

import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchCoordinator;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchAccessServiceTest {
    @Test
    void adminCanReadAnyBatchWithoutACoordinatorRow() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById("batch-b", Batch.class)).thenReturn(legacyBatch("batch-b"));
        BatchAccessService access = new BatchAccessService(mongo, mock(UserRepository.class), mock(OrganizationAccessService.class));
        assertDoesNotThrow(() -> access.ensureCanReadBatch(auth("admin", "ROLE_ADMIN"), "batch-b"));
        verify(mongo, never()).exists(any(Query.class), eq(BatchCoordinator.class));
    }

    @Test
    void coordinatorOfAnotherBatchIsRejected() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        UserRepository users = mock(UserRepository.class);
        when(mongo.findById("batch-b", Batch.class)).thenReturn(legacyBatch("batch-b"));
        when(users.findByUsername("coord")).thenReturn(Optional.of(user("coord-id", "coord")));
        when(mongo.exists(any(Query.class), eq(BatchCoordinator.class))).thenReturn(false);
        BatchAccessService access = new BatchAccessService(mongo, users, mock(OrganizationAccessService.class));
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> access.ensureCanReadBatch(auth("coord", "ROLE_USER"), "batch-b"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void assignedCoordinatorCanReadThatBatch() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        UserRepository users = mock(UserRepository.class);
        when(mongo.findById("batch-a", Batch.class)).thenReturn(legacyBatch("batch-a"));
        when(users.findByUsername("coord")).thenReturn(Optional.of(user("coord-id", "coord")));
        when(mongo.exists(any(Query.class), eq(BatchCoordinator.class))).thenReturn(true);
        BatchAccessService access = new BatchAccessService(mongo, users, mock(OrganizationAccessService.class));
        assertDoesNotThrow(() -> access.ensureCanReadBatch(auth("coord", "ROLE_USER"), "batch-a"));
    }

    @Test
    void revokedCoordinatorCannotSearchStudents() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsername("coord")).thenReturn(Optional.of(user("coord-id", "coord")));
        when(mongo.exists(any(Query.class), eq(BatchCoordinator.class))).thenReturn(false);
        BatchAccessService access = new BatchAccessService(mongo, users, mock(OrganizationAccessService.class));
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> access.ensureCanSearchUsers(auth("coord", "ROLE_USER")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void coordinatorCannotPassTheAdminCheck() {
        BatchAccessService access = new BatchAccessService(
            mock(MongoTemplate.class), mock(UserRepository.class), mock(OrganizationAccessService.class));
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> access.ensureAdmin(auth("coord", "ROLE_USER")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void inactiveCoordinatorCannotRead() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        UserRepository users = mock(UserRepository.class);
        when(mongo.findById("batch-a", Batch.class)).thenReturn(legacyBatch("batch-a"));
        User inactive = user("coord-id", "coord");
        inactive.setAccountStatus("INACTIVE");
        when(users.findByUsername("coord")).thenReturn(Optional.of(inactive));
        BatchAccessService access = new BatchAccessService(mongo, users, mock(OrganizationAccessService.class));
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> access.ensureCanReadBatch(auth("coord", "ROLE_USER"), "batch-a"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(mongo, never()).exists(any(Query.class), eq(BatchCoordinator.class));
    }

    @Test
    void crossTenantOrgBatchIsHiddenFromUnauthorizedUser() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        UserRepository users = mock(UserRepository.class);
        OrganizationAccessService organizationAccess = mock(OrganizationAccessService.class);
        Batch batch = legacyBatch("batch-a");
        batch.setOrganizationId("org-a");
        when(mongo.findById("batch-a", Batch.class)).thenReturn(batch);
        when(users.findByUsername("outsider")).thenReturn(Optional.of(user("outsider-id", "outsider")));
        when(organizationAccess.canAccessOrganizationResource(any(), eq("org-a"))).thenReturn(false);
        when(mongo.exists(any(Query.class), eq(BatchCoordinator.class))).thenReturn(false);
        BatchAccessService access = new BatchAccessService(mongo, users, organizationAccess);
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> access.ensureCanReadBatch(auth("outsider", "ROLE_USER"), "batch-a"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    private static Batch legacyBatch(String id) {
        Batch batch = new Batch();
        batch.setId(id);
        return batch;
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setAccountStatus("ACTIVE");
        return user;
    }

    private static Authentication auth(String username, String role) {
        return new UsernamePasswordAuthenticationToken(username, "n", List.of(new SimpleGrantedAuthority(role)));
    }
}
