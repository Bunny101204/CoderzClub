package com.coderzclub.service;

import com.coderzclub.model.Problem;
import com.coderzclub.model.User;
import com.coderzclub.model.UserSolvedProblem;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonObjectId;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrgPrivatePointsReconciliationServiceTest {

    @Test
    void dryRunReportsInflatedOrgPrivateAwardsWithoutMutating() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        OrganizationVisibilityPolicy policy =
            new OrganizationVisibilityPolicy(new OrganizationResourceCompatibility());
        OrgPrivatePointsReconciliationService service =
            new OrgPrivatePointsReconciliationService(mongo, policy);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "dryRun", true);

        Problem orgPrivate = new Problem();
        orgPrivate.setId("p-org");
        orgPrivate.setOrganizationId("org-1");
        orgPrivate.setPoints(100);

        UserSolvedProblem applied = new UserSolvedProblem();
        applied.setId("usp-1");
        applied.setUserId("u1");
        applied.setProblemId("p-org");
        applied.setPointsAwarded(100);
        applied.setRewardStatus(UserSolvedProblem.RewardStatus.APPLIED);

        User user = new User();
        user.setId("u1");
        user.setUsername("alice");
        user.setTotalPoints(150);
        user.setProblemsSolved(2);
        user.setSolvedProblemIds(List.of("p-org", "p-public"));

        when(mongo.find(any(), eq(Problem.class))).thenReturn(List.of(orgPrivate));
        when(mongo.findAll(UserSolvedProblem.class)).thenReturn(List.of(applied));
        when(mongo.findById("u1", User.class)).thenReturn(user);

        Map<String, Object> report = service.audit();

        assertEquals("COMPLETED", report.get("status"));
        assertEquals(true, report.get("dryRun"));
        assertFalse((Boolean) report.get("mutationsApplied"));
        assertEquals(1, report.get("inflatedSolvedRowCount"));
        assertEquals(1, report.get("affectedUserCount"));
        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(UserSolvedProblem.class));
        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(User.class));
        verify(mongo).save(any(), eq(OrgPrivatePointsReconciliationService.AUDIT_COLLECTION));
    }

    @Test
    void applyWritesWhenEnabledAndDryRunDisabledIsIdempotentOnRepeat() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        OrganizationVisibilityPolicy policy =
            new OrganizationVisibilityPolicy(new OrganizationResourceCompatibility());
        OrgPrivatePointsReconciliationService service =
            new OrgPrivatePointsReconciliationService(mongo, policy);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "dryRun", false);

        Problem orgPrivate = new Problem();
        orgPrivate.setId("p-org");
        orgPrivate.setOrganizationId("org-1");

        UserSolvedProblem applied = new UserSolvedProblem();
        applied.setId("usp-1");
        applied.setUserId("u1");
        applied.setProblemId("p-org");
        applied.setPointsAwarded(100);
        applied.setRewardStatus(UserSolvedProblem.RewardStatus.APPLIED);

        User user = new User();
        user.setId("u1");
        user.setTotalPoints(150);
        user.setProblemsSolved(2);
        user.setSolvedProblemIds(List.of("p-org", "p-public"));

        when(mongo.find(any(), eq(Problem.class))).thenReturn(List.of(orgPrivate));
        when(mongo.findAll(UserSolvedProblem.class)).thenReturn(List.of(applied));
        when(mongo.findById("u1", User.class)).thenReturn(user);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(UserSolvedProblem.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, new BsonObjectId(new ObjectId())));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(User.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, new BsonObjectId(new ObjectId())));

        Map<String, Object> first = service.reconcile(true);
        assertEquals("COMPLETED", first.get("status"));
        assertTrue((Boolean) first.get("mutationsApplied"));
        assertEquals(1, first.get("clampedSolvedRows"));
        assertEquals(1, first.get("usersCorrected"));

        // Second pass: awards already zero → no matching rows to clamp/correct.
        UserSolvedProblem clamped = new UserSolvedProblem();
        clamped.setId("usp-1");
        clamped.setUserId("u1");
        clamped.setProblemId("p-org");
        clamped.setPointsAwarded(0);
        clamped.setRewardStatus(UserSolvedProblem.RewardStatus.APPLIED);
        when(mongo.findAll(UserSolvedProblem.class)).thenReturn(List.of(clamped));
        Map<String, Object> second = service.reconcile(true);
        assertFalse((Boolean) second.get("mutationsApplied"));
        assertEquals(0, second.get("clampedSolvedRows"));
        assertEquals(0, second.get("usersCorrected"));
        verify(mongo, times(2)).save(any(), eq(OrgPrivatePointsReconciliationService.AUDIT_COLLECTION));
    }

    @Test
    void applyRequestedButDryRunFlagStillBlocksWrites() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        OrgPrivatePointsReconciliationService service =
            new OrgPrivatePointsReconciliationService(
                mongo,
                new OrganizationVisibilityPolicy(new OrganizationResourceCompatibility()));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "dryRun", true);

        when(mongo.find(any(), eq(Problem.class))).thenReturn(List.of());
        when(mongo.findAll(UserSolvedProblem.class)).thenReturn(List.of());

        Map<String, Object> report = service.reconcile(true);
        assertEquals(true, report.get("dryRun"));
        assertFalse((Boolean) report.get("mutationsApplied"));
        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), any(Class.class));
    }

    @Test
    void disabledFlagSkipsWork() {
        OrgPrivatePointsReconciliationService service =
            new OrgPrivatePointsReconciliationService(
                mock(MongoTemplate.class),
                new OrganizationVisibilityPolicy(new OrganizationResourceCompatibility()));
        ReflectionTestUtils.setField(service, "enabled", false);

        Map<String, Object> report = service.audit();
        assertEquals("SKIPPED", report.get("status"));
    }

    @Test
    void duplicatePositiveRowsAreAllClamped() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        OrgPrivatePointsReconciliationService service =
            new OrgPrivatePointsReconciliationService(
                mongo,
                new OrganizationVisibilityPolicy(new OrganizationResourceCompatibility()));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "dryRun", false);

        Problem orgPrivate = new Problem();
        orgPrivate.setId("p-org");
        orgPrivate.setOrganizationId("org-1");

        UserSolvedProblem first = new UserSolvedProblem();
        first.setId("usp-1");
        first.setUserId("u1");
        first.setProblemId("p-org");
        first.setPointsAwarded(40);
        first.setRewardStatus(UserSolvedProblem.RewardStatus.APPLIED);

        UserSolvedProblem duplicate = new UserSolvedProblem();
        duplicate.setId("usp-2");
        duplicate.setUserId("u1");
        duplicate.setProblemId("p-org");
        duplicate.setPointsAwarded(40);
        duplicate.setRewardStatus(UserSolvedProblem.RewardStatus.APPLIED);

        User user = new User();
        user.setId("u1");
        user.setTotalPoints(80);
        user.setProblemsSolved(1);
        user.setSolvedProblemIds(List.of("p-org"));

        when(mongo.find(any(), eq(Problem.class))).thenReturn(List.of(orgPrivate));
        when(mongo.findAll(UserSolvedProblem.class)).thenReturn(List.of(first, duplicate));
        when(mongo.findById("u1", User.class)).thenReturn(user);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(UserSolvedProblem.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(User.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        Map<String, Object> report = service.reconcile(true);
        assertEquals(2, report.get("clampedSolvedRows"));
        assertEquals(1, report.get("usersCorrected"));
        ArgumentCaptor<Update> userUpdate = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), userUpdate.capture(), eq(User.class));
        assertTrue(userUpdate.getValue().getUpdateObject().toJson().contains("totalPoints"));
    }
}
