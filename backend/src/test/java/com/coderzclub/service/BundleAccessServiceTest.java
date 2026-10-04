package com.coderzclub.service;

import com.coderzclub.controller.AdminBundleAccessController;
import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.BundleAccessGrant;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemBundle;
import com.coderzclub.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BundleAccessServiceTest {

    @Test
    void legacyBundleWithoutVisibilityIsPublic() {
        ProblemBundle bundle = activeBundle("b1", null);
        assertEquals("PUBLIC", BundleAccessService.visibilityOf(bundle));
        assertTrue(new BundleAccessService(mock(MongoTemplate.class)).canAccess(user("u1"), bundle));
    }

    @Test
    void publicBundleIsAccessibleWithoutGrant() {
        ProblemBundle bundle = activeBundle("b1", "PUBLIC");
        assertTrue(new BundleAccessService(mock(MongoTemplate.class)).canAccess(user("u1"), bundle));
    }

    @Test
    void restrictedBundleHiddenFromUnrelatedUser() {
        MongoTemplate mongo = mongoForUserWithoutGrants("u1");
        ProblemBundle bundle = activeBundle("secret", "RESTRICTED");
        when(mongo.findById("secret", ProblemBundle.class)).thenReturn(bundle);
        assertFalse(new BundleAccessService(mongo).canAccess(user("u1"), bundle));
        assertThrows(ResponseStatusException.class,
            () -> new BundleAccessService(mongo).requireAccessibleBundle(user("u1"), "secret"));
    }

    @Test
    void directUserGrantAllowsAccessAndRevokeRemovesIt() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ProblemBundle bundle = activeBundle("b1", "RESTRICTED");
        when(mongo.findById("b1", ProblemBundle.class)).thenReturn(bundle);
        User granted = user("u1");
        stubMemberships(mongo, "u1", List.of());
        BundleAccessGrant grant = userGrant("g1", "b1", "u1");
        when(mongo.find(any(Query.class), eq(BundleAccessGrant.class))).thenReturn(List.of(grant));
        BundleAccessService service = new BundleAccessService(mongo);
        assertTrue(service.canAccess(granted, bundle));

        when(mongo.find(any(Query.class), eq(BundleAccessGrant.class))).thenReturn(List.of());
        assertFalse(service.canAccess(granted, bundle));
    }

    @Test
    void batchGrantAllowsActiveMemberAndNotNonMember() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ProblemBundle bundle = activeBundle("b1", "RESTRICTED");
        BatchMember member = new BatchMember();
        member.setBatchId("batch1");
        member.setUserId("u1");
        Batch batch = new Batch();
        batch.setId("batch1");
        batch.setActive(true);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenAnswer(inv -> {
            Query q = inv.getArgument(0);
            return q.getQueryObject().toJson().contains("u1") ? List.of(member) : List.of();
        });
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of(batch));
        when(mongo.find(any(Query.class), eq(BundleAccessGrant.class))).thenAnswer(inv -> {
            Query q = inv.getArgument(0);
            String json = q.getQueryObject().toJson();
            if (json.contains("batch1") || json.contains("BATCH")) {
                return List.of(batchGrant("g2", "b1", "batch1"));
            }
            return List.of();
        });
        BundleAccessService service = new BundleAccessService(mongo);
        assertTrue(service.canAccess(user("u1"), bundle));
        assertFalse(service.canAccess(user("u2"), bundle));
    }

    @Test
    void removingMemberRevokesBatchDerivedAccess() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ProblemBundle bundle = activeBundle("b1", "RESTRICTED");
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of());
        when(mongo.find(any(Query.class), eq(BundleAccessGrant.class))).thenReturn(List.of());
        assertFalse(new BundleAccessService(mongo).canAccess(user("u1"), bundle));
    }

    @Test
    void archivedBatchDoesNotGrantCurrentAccess() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ProblemBundle bundle = activeBundle("b1", "RESTRICTED");
        BatchMember member = new BatchMember();
        member.setBatchId("batch1");
        member.setUserId("u1");
        Batch archived = new Batch();
        archived.setId("batch1");
        archived.setActive(false);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member));
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of());
        when(mongo.find(any(Query.class), eq(BundleAccessGrant.class))).thenReturn(List.of());
        assertFalse(new BundleAccessService(mongo).canAccess(user("u1"), bundle));
        verify(mongo, times(1)).find(any(Query.class), eq(Batch.class));
    }

    @Test
    void reactivatedBatchRestoresAccessIfGrantRemains() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ProblemBundle bundle = activeBundle("b1", "RESTRICTED");
        BatchMember member = new BatchMember();
        member.setBatchId("batch1");
        member.setUserId("u1");
        Batch active = new Batch();
        active.setId("batch1");
        active.setActive(true);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member));
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of(active));
        when(mongo.find(any(Query.class), eq(BundleAccessGrant.class))).thenReturn(List.of(batchGrant("g2", "b1", "batch1")));
        assertTrue(new BundleAccessService(mongo).canAccess(user("u1"), bundle));
    }

    @Test
    void duplicateGrantIsIdempotent() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ProblemBundle bundle = activeBundle("b1", "RESTRICTED");
        User target = user("u1");
        BundleAccessGrant existing = userGrant("g1", "b1", "u1");
        when(mongo.findById("b1", ProblemBundle.class)).thenReturn(bundle);
        when(mongo.findById("u1", User.class)).thenReturn(target);
        when(mongo.findOne(any(Query.class), eq(BundleAccessGrant.class))).thenReturn(existing);
        BundleAccessGrant result = new BundleAccessService(mongo).addGrant("b1", "USER", "u1", "admin");
        assertSame(existing, result);
        verify(mongo, never()).save(any(BundleAccessGrant.class));
    }

    @Test
    void duplicateKeyOnSaveReturnsExistingGrant() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ProblemBundle bundle = activeBundle("b1", "RESTRICTED");
        User target = user("u1");
        BundleAccessGrant existing = userGrant("g1", "b1", "u1");
        when(mongo.findById("b1", ProblemBundle.class)).thenReturn(bundle);
        when(mongo.findById("u1", User.class)).thenReturn(target);
        when(mongo.findOne(any(Query.class), eq(BundleAccessGrant.class)))
            .thenReturn(null)
            .thenReturn(existing);
        when(mongo.save(any(BundleAccessGrant.class))).thenThrow(new DuplicateKeyException("dup"));
        BundleAccessGrant result = new BundleAccessService(mongo).addGrant("b1", "USER", "u1", "admin");
        assertEquals("g1", result.getId());
    }

    @Test
    void invalidUserRejected() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById("b1", ProblemBundle.class)).thenReturn(activeBundle("b1", "RESTRICTED"));
        when(mongo.findById("missing", User.class)).thenReturn(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> new BundleAccessService(mongo).addGrant("b1", "USER", "missing", "admin"));
        assertTrue(ex.getMessage().contains("User not found"));
    }

    @Test
    void invalidBatchRejected() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById("b1", ProblemBundle.class)).thenReturn(activeBundle("b1", "RESTRICTED"));
        when(mongo.findById("missing", Batch.class)).thenReturn(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> new BundleAccessService(mongo).addGrant("b1", "BATCH", "missing", "admin"));
        assertTrue(ex.getMessage().contains("Batch not found"));
    }

    @Test
    void invalidBundleRejected() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById("missing", ProblemBundle.class)).thenReturn(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> new BundleAccessService(mongo).addGrant("missing", "USER", "u1", "admin"));
        assertTrue(ex.getMessage().contains("Bundle not found"));
    }

    @Test
    void deletedUserCannotBeGranted() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        User deleted = user("u1");
        deleted.setAccountStatus("DELETED");
        when(mongo.findById("b1", ProblemBundle.class)).thenReturn(activeBundle("b1", "RESTRICTED"));
        when(mongo.findById("u1", User.class)).thenReturn(deleted);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> new BundleAccessService(mongo).addGrant("b1", "USER", "u1", "admin"));
        assertTrue(ex.getMessage().contains("User not found"));
        verify(mongo, never()).save(any(BundleAccessGrant.class));
    }

    @Test
    void deletedUserGrantDoesNotGrantAccess() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ProblemBundle bundle = activeBundle("b1", "RESTRICTED");
        User deleted = user("u1");
        deleted.setAccountStatus("DELETED");
        when(mongo.find(any(Query.class), eq(BundleAccessGrant.class))).thenReturn(List.of(userGrant("g1", "b1", "u1")));
        assertFalse(new BundleAccessService(mongo).canAccess(deleted, bundle));
    }

    @Test
    void nonAdminCannotMutateGrantsThroughAdminMapping() {
        RequestMapping mapping = AdminBundleAccessController.class.getAnnotation(RequestMapping.class);
        PreAuthorize pre = AdminBundleAccessController.class.getAnnotation(PreAuthorize.class);
        assertEquals("/api/admin/bundles/{bundleId}", mapping.value()[0]);
        assertEquals("hasRole('ADMIN')", pre.value());
    }

    @Test
    void directBundleDetailEnforcesAccess() {
        MongoTemplate mongo = mongoForUserWithoutGrants("u1");
        when(mongo.findById("secret", ProblemBundle.class)).thenReturn(activeBundle("secret", "RESTRICTED"));
        assertThrows(ResponseStatusException.class,
            () -> new BundleAccessService(mongo).requireAccessibleBundle(user("u1"), "secret"));
    }

    @Test
    void bundleProblemsEndpointEnforcesAccess() {
        MongoTemplate mongo = mongoForUserWithoutGrants("u1");
        when(mongo.findById("secret", ProblemBundle.class)).thenReturn(activeBundle("secret", "RESTRICTED"));
        assertThrows(ResponseStatusException.class,
            () -> new BundleAccessService(mongo).problemsForAccessibleBundle(user("u1"), "secret"));
        verify(mongo, never()).find(any(Query.class), eq(Problem.class));
    }

    @Test
    void userBundleListUsesBoundedBulkLookups() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        AtomicInteger memberFinds = new AtomicInteger();
        AtomicInteger batchFinds = new AtomicInteger();
        AtomicInteger grantFinds = new AtomicInteger();
        AtomicInteger bundleFinds = new AtomicInteger();
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenAnswer(inv -> {
            memberFinds.incrementAndGet();
            return List.of();
        });
        when(mongo.find(any(Query.class), eq(Batch.class))).thenAnswer(inv -> {
            batchFinds.incrementAndGet();
            return List.of();
        });
        when(mongo.find(any(Query.class), eq(BundleAccessGrant.class))).thenAnswer(inv -> {
            grantFinds.incrementAndGet();
            return List.of();
        });
        when(mongo.find(any(Query.class), eq(ProblemBundle.class))).thenAnswer(inv -> {
            bundleFinds.incrementAndGet();
            List<ProblemBundle> many = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                many.add(activeBundle("b" + i, "PUBLIC"));
            }
            return many;
        });
        List<ProblemBundle> result = new BundleAccessService(mongo).listAccessibleActiveBundles(user("u1"));
        assertEquals(40, result.size());
        assertEquals(1, memberFinds.get());
        assertEquals(0, batchFinds.get());
        assertEquals(1, grantFinds.get());
        assertEquals(1, bundleFinds.get());
    }

    @Test
    void inactivePublicBundleHiddenFromNonAdmin() {
        ProblemBundle bundle = activeBundle("b1", "PUBLIC");
        bundle.setActive(false);
        assertFalse(new BundleAccessService(mock(MongoTemplate.class)).canAccess(user("u1"), bundle));
        User admin = user("admin");
        admin.setRole("ADMIN");
        assertTrue(new BundleAccessService(mock(MongoTemplate.class)).canAccess(admin, bundle));
    }

    private static ProblemBundle activeBundle(String id, String visibility) {
        ProblemBundle bundle = new ProblemBundle();
        bundle.setId(id);
        bundle.setActive(true);
        bundle.setVisibility(visibility);
        bundle.setProblemIds(List.of("p1"));
        return bundle;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setRole("USER");
        user.setAccountStatus("ACTIVE");
        return user;
    }

    private static BundleAccessGrant userGrant(String id, String bundleId, String userId) {
        BundleAccessGrant grant = new BundleAccessGrant();
        grant.setId(id);
        grant.setBundleId(bundleId);
        grant.setSubjectType(BundleAccessGrant.SUBJECT_USER);
        grant.setSubjectId(userId);
        return grant;
    }

    private static BundleAccessGrant batchGrant(String id, String bundleId, String batchId) {
        BundleAccessGrant grant = new BundleAccessGrant();
        grant.setId(id);
        grant.setBundleId(bundleId);
        grant.setSubjectType(BundleAccessGrant.SUBJECT_BATCH);
        grant.setSubjectId(batchId);
        return grant;
    }

    private static MongoTemplate mongoForUserWithoutGrants(String userId) {
        MongoTemplate mongo = mock(MongoTemplate.class);
        stubMemberships(mongo, userId, List.of());
        when(mongo.find(any(Query.class), eq(BundleAccessGrant.class))).thenReturn(List.of());
        return mongo;
    }

    private static void stubMemberships(MongoTemplate mongo, String userId, List<BatchMember> members) {
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(members);
    }
}
