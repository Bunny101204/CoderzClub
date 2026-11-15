package com.coderzclub.service;

import com.coderzclub.model.OrganizationDomain;
import com.coderzclub.model.OrganizationDomainStatus;
import com.coderzclub.model.OrganizationMember;
import com.coderzclub.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationDomainServiceTest {

    private static OrganizationDomainService service(MongoTemplate mongo, OrganizationAccessService access, boolean publicResolution) {
        return new OrganizationDomainService(mongo, access, publicResolution);
    }

    @Test
    void unknownOrDisabledOrPendingHostsDoNotResolveTenant() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        OrganizationAccessService access = mock(OrganizationAccessService.class);
        OrganizationDomainService enabled = service(mongo, access, true);
        when(mongo.findOne(any(Query.class), eq(OrganizationDomain.class))).thenReturn(null);
        assertNull(enabled.resolveOrganizationIdForHostname("unknown.example.test"));

        OrganizationDomain disabled = domain("org-a", "college.example.test", OrganizationDomainStatus.DISABLED);
        when(mongo.findOne(any(Query.class), eq(OrganizationDomain.class))).thenReturn(disabled);
        assertNull(enabled.resolveOrganizationIdForHostname("college.example.test"));

        OrganizationDomain pending = domain("org-a", "college.example.test", OrganizationDomainStatus.PENDING);
        when(mongo.findOne(any(Query.class), eq(OrganizationDomain.class))).thenReturn(pending);
        assertNull(enabled.resolveOrganizationIdForHostname("college.example.test"));
    }

    @Test
    void verifiedHostDoesNotResolveUntilActiveAndFlagEnabled() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        OrganizationAccessService access = mock(OrganizationAccessService.class);
        OrganizationDomain verified = domain("org-a", "college.example.test", OrganizationDomainStatus.VERIFIED);
        when(mongo.findOne(any(Query.class), eq(OrganizationDomain.class))).thenReturn(verified);

        assertNull(service(mongo, access, true).resolveOrganizationIdForHostname("college.example.test"));
        assertNull(service(mongo, access, false).resolveOrganizationIdForHostname("college.example.test"));
    }

    @Test
    void activeHostResolvesOnlyWhenPublicResolutionEnabled() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        OrganizationAccessService access = mock(OrganizationAccessService.class);
        OrganizationDomain active = domain("org-a", "college.example.test", OrganizationDomainStatus.ACTIVE);
        when(mongo.findOne(any(Query.class), eq(OrganizationDomain.class))).thenReturn(active);

        assertNull(service(mongo, access, false).resolveOrganizationIdForHostname("https://College.Example.Test:443"));
        assertEquals("org-a", service(mongo, access, true)
            .resolveOrganizationIdForHostname("https://College.Example.Test:443"));
    }

    @Test
    void siblingHostnameDoesNotInheritActiveBinding() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        OrganizationAccessService access = mock(OrganizationAccessService.class);
        OrganizationDomainService enabled = service(mongo, access, true);
        when(mongo.findOne(any(Query.class), eq(OrganizationDomain.class))).thenReturn(null);
        assertNull(enabled.resolveOrganizationIdForHostname("evil.college.example.test"));
    }

    @Test
    void duplicateHostnameRegistrationConflicts() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        OrganizationAccessService access = mock(OrganizationAccessService.class);
        OrganizationDomainService svc = service(mongo, access, false);
        var auth = new UsernamePasswordAuthenticationToken("owner", "n/a");
        User actor = new User();
        actor.setId("u1");
        when(access.requireOrgAdministrator(auth, "org-a")).thenReturn(new OrganizationMember());
        when(access.requireUser(auth)).thenReturn(actor);
        when(mongo.exists(any(Query.class), eq(OrganizationDomain.class))).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.register(auth, "org-a", "taken.example.test"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(mongo, never()).save(any(OrganizationDomain.class));
    }

    @Test
    void reservedAndIpHostsAreRejected() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        OrganizationAccessService access = mock(OrganizationAccessService.class);
        OrganizationDomainService svc = service(mongo, access, false);
        var auth = new UsernamePasswordAuthenticationToken("owner", "n/a");
        when(access.requireOrgAdministrator(auth, "org-a")).thenReturn(new OrganizationMember());
        when(access.requireUser(auth)).thenReturn(new User());

        assertThrows(ResponseStatusException.class, () -> svc.register(auth, "org-a", "localhost"));
        assertThrows(ResponseStatusException.class, () -> svc.register(auth, "org-a", "127.0.0.1"));
        assertThrows(ResponseStatusException.class, () -> svc.register(auth, "org-a", "coderzclub.site"));
        assertThrows(ResponseStatusException.class, () -> svc.register(auth, "org-a", "foo.coderzclub.site"));
    }

    @Test
    void activateRejectsUnverifiedDomain() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        OrganizationAccessService access = mock(OrganizationAccessService.class);
        OrganizationDomainService svc = service(mongo, access, false);
        var auth = new UsernamePasswordAuthenticationToken("owner", "n/a");
        when(access.requireOrgAdministrator(auth, "org-a")).thenReturn(new OrganizationMember());
        OrganizationDomain pending = domain("org-a", "labs.example.test", OrganizationDomainStatus.PENDING);
        pending.setId("d1");
        when(mongo.findById("d1", OrganizationDomain.class)).thenReturn(pending);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.activate(auth, "org-a", "d1"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void crossOrganizationDomainMutationIsNotFound() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        OrganizationAccessService access = mock(OrganizationAccessService.class);
        OrganizationDomainService svc = service(mongo, access, false);
        var auth = new UsernamePasswordAuthenticationToken("owner", "n/a");
        when(access.requireOrgAdministrator(auth, "org-b")).thenReturn(new OrganizationMember());
        OrganizationDomain ownedByA = domain("org-a", "labs.example.test", OrganizationDomainStatus.VERIFIED);
        ownedByA.setId("d1");
        when(mongo.findById("d1", OrganizationDomain.class)).thenReturn(ownedByA);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> svc.disable(auth, "org-b", "d1"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void localVerificationActivatesBindingToVerifiedOnly() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        OrganizationAccessService access = mock(OrganizationAccessService.class);
        OrganizationDomainService service = service(mongo, access, false);
        var auth = new UsernamePasswordAuthenticationToken("owner", "n/a");
        User actor = new User();
        actor.setId("u1");
        when(access.requireOrgAdministrator(auth, "org-a")).thenReturn(new OrganizationMember());
        when(access.requireUser(auth)).thenReturn(actor);

        OrganizationDomain pending = domain("org-a", "labs.example.test", OrganizationDomainStatus.PENDING);
        pending.setId("d1");
        pending.setVerificationToken("abc123");
        pending.setVerificationExpiresAt(new Date(System.currentTimeMillis() + 60_000));
        when(mongo.findById("d1", OrganizationDomain.class)).thenReturn(pending);
        when(mongo.save(any(OrganizationDomain.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> verified = service.markVerifiedLocally(auth, "org-a", "d1", "abc123");
        assertEquals(OrganizationDomainStatus.VERIFIED.name(), verified.get("status"));
        assertTrue(service.activate(auth, "org-a", "d1").get("status").equals(OrganizationDomainStatus.ACTIVE.name()));
    }

    @Test
    void expiredOrWrongVerificationTokenIsRejected() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        OrganizationAccessService access = mock(OrganizationAccessService.class);
        OrganizationDomainService service = service(mongo, access, false);
        var auth = new UsernamePasswordAuthenticationToken("owner", "n/a");
        when(access.requireOrgAdministrator(auth, "org-a")).thenReturn(new OrganizationMember());

        OrganizationDomain pending = domain("org-a", "labs.example.test", OrganizationDomainStatus.PENDING);
        pending.setId("d1");
        pending.setVerificationToken("abc123");
        pending.setVerificationExpiresAt(new Date(System.currentTimeMillis() - 1_000));
        when(mongo.findById("d1", OrganizationDomain.class)).thenReturn(pending);

        assertThrows(ResponseStatusException.class,
            () -> service.markVerifiedLocally(auth, "org-a", "d1", "abc123"));

        pending.setVerificationExpiresAt(new Date(System.currentTimeMillis() + 60_000));
        assertThrows(ResponseStatusException.class,
            () -> service.markVerifiedLocally(auth, "org-a", "d1", "wrong"));
    }

    private static OrganizationDomain domain(String orgId, String hostname, OrganizationDomainStatus status) {
        OrganizationDomain domain = new OrganizationDomain();
        domain.setOrganizationId(orgId);
        domain.setHostname(hostname);
        domain.setStatus(status.name());
        return domain;
    }
}
