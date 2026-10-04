/**
 * MongoDB Index Creation Script for CoderzClub
 *
 * Run this script in the MongoDB shell to create or verify production-ready indexes.
 * This script is idempotent and avoids duplicate indexes when Spring auto-index
 * creation is enabled.
 *
 * Apply after numericId has been backfilled:
 *   mongosh "your-connection-string/coderzclub" mongodb-indexes.js
 * Verify with:
 *   mongosh "your-connection-string/coderzclub" --eval 'db.problems.getIndexes(); db.user_solved_problems.getIndexes()'
 */

function ensureIndex(coll, keys, options) {
  options = options || {};
  var collectionExists = db.getCollectionNames().indexOf(coll.getName()) !== -1;
  if (!collectionExists) {
    print('Creating collection and index ' + (options.name || JSON.stringify(keys)) + ' on ' + coll.getName());
    coll.createIndex(keys, options);
    return;
  }
  var existing = coll.getIndexes().some(function(idx) {
    return JSON.stringify(idx.key) === JSON.stringify(keys);
  });
  if (!existing) {
    print('Creating index ' + (options.name || JSON.stringify(keys)) + ' on ' + coll.getName());
    coll.createIndex(keys, options);
  } else {
    print('Index already exists on ' + coll.getName() + ': ' + (options.name || JSON.stringify(keys)));
  }
}

function dropIndexIfPresent(coll, name) {
  if (db.getCollectionNames().indexOf(coll.getName()) === -1) return;
  var existing = coll.getIndexes().some(function(idx) { return idx.name === name; });
  if (existing) {
    print('Dropping obsolete index ' + name + ' from ' + coll.getName());
    coll.dropIndex(name);
  }
}

function assertIndex(coll, name, predicate) {
  if (db.getCollectionNames().indexOf(coll.getName()) === -1) {
    throw new Error('Critical collection missing: ' + coll.getName());
  }
  var index = coll.getIndexes().find(function(idx) { return idx.name === name; });
  if (!index || (predicate && !predicate(index))) {
    throw new Error('Critical index missing or misconfigured: ' + coll.getName() + '.' + name);
  }
  print('Verified critical index ' + coll.getName() + '.' + name);
}

function assertIndexKeys(coll, keys, predicate) {
  if (db.getCollectionNames().indexOf(coll.getName()) === -1) {
    throw new Error('Critical collection missing: ' + coll.getName());
  }
  var index = coll.getIndexes().find(function(idx) {
    return JSON.stringify(idx.key) === JSON.stringify(keys);
  });
  if (!index || (predicate && !predicate(index))) {
    throw new Error('Critical index missing or misconfigured: ' + coll.getName() + ' ' + JSON.stringify(keys));
  }
  print('Verified critical index ' + coll.getName() + ' ' + JSON.stringify(keys) + ' (' + index.name + ')');
}

// ===== PROBLEMS COLLECTION INDEXES =====
// ProblemController filters difficulty/category/tags and prefixes title or _id,
// then sorts by numericId and _id before applying skip/limit.
ensureIndex(db.problems, { "difficulty": 1, "category": 1 }, { name: "difficulty_category_idx" });
ensureIndex(db.problems, { "tags": 1 }, { name: "tags_idx" });
ensureIndex(db.problems, { "numericId": 1, "_id": 1 }, { name: "numericId_id_idx" });
// Run the numericId backfill and duplicate cleanup before this unique index is created.
ensureIndex(db.problems, { "numericId": 1 }, { unique: true, sparse: true, name: "numericId_unique_sparse_idx" });
// Prefix search uses title or _id regex; title supports the title branch.
ensureIndex(db.problems, { "title": 1 }, { name: "title_idx" });
ensureIndex(db.problems, { "difficulty": 1 }, { name: "difficulty_idx" });
ensureIndex(db.problems, { "difficulty": 1, "tags": 1 }, { name: "difficulty_tags_idx" });

// ===== USERS COLLECTION INDEXES =====
// Leaderboard, role lookup, streak ordering, and email verification filtering.
ensureIndex(db.users, { "totalPoints": -1 }, { name: "totalPoints_desc_idx" });
ensureIndex(db.users, { "role": 1 }, { name: "role_idx" });
ensureIndex(db.users, { "currentStreak": -1 }, { name: "currentStreak_desc_idx" });
ensureIndex(db.users, { "emailVerified": 1 }, { name: "emailVerified_idx" });
ensureIndex(db.users, { "totalPoints": -1, "_id": 1 }, { name: "totalPoints_id_desc_idx" });

// O(1) profile statistics read model.
ensureIndex(db.user_stats, { "updatedAt": -1 }, { name: "updatedAt_desc_idx" });

// Unique username/email indexes are created by Spring @Indexed(unique=true) in User model.

// ===== SUBMISSIONS COLLECTION INDEXES =====
// Recent history, per-user/problem counts, and result-based filtering.
ensureIndex(db.submissions, { "userId": 1, "createdAt": -1 }, { name: "userId_createdAt_desc_idx" });
ensureIndex(db.submissions, { "userId": 1, "problemId": 1, "createdAt": -1 }, { name: "userId_problemId_createdAt_desc_idx" });
ensureIndex(db.submissions, { "userId": 1, "result": 1, "createdAt": -1 }, { name: "userId_result_createdAt_desc_idx" });
ensureIndex(db.submissions, { "problemId": 1, "result": 1, "createdAt": -1 }, { name: "problemId_result_createdAt_desc_idx" });
ensureIndex(db.submissions, { "userId": 1, "problemId": 1, "result": 1 }, { name: "userId_problemId_result_idx" });

// Direct repository lookups used by user/profile and history endpoints.
ensureIndex(db.submissions, { "userId": 1 }, { name: "userId_idx" });
ensureIndex(db.submissions, { "problemId": 1 }, { name: "problemId_idx" });
ensureIndex(db.submissions, { "result": 1 }, { name: "result_idx" });
ensureIndex(db.submissions, { "createdAt": -1 }, { name: "createdAt_desc_idx" });
// Unique job-backed submissions. Legacy documents omit submissionJobId and are excluded
// by the partial filter (safer than a naive unique index, and safer than sparse+null).
ensureIndex(db.submissions, { "submissionJobId": 1 }, {
  unique: true,
  name: "submissionJobId_unique_sparse_idx",
  partialFilterExpression: { submissionJobId: { $type: "string" } }
});

// ===== SUBMISSION_JOBS COLLECTION INDEXES =====
// Queue scanning and recovery for pending/locked jobs.
ensureIndex(db.submission_jobs, { "status": 1, "createdAt": 1 }, { name: "status_createdAt_idx" });
ensureIndex(db.submission_jobs, { "userId": 1, "status": 1, "createdAt": -1 }, { name: "userId_status_createdAt_desc_idx" });
ensureIndex(db.submission_jobs, { "lockedUntil": 1 }, { name: "lockedUntil_idx" });
ensureIndex(db.submission_jobs, { "status": 1, "lockedUntil": 1 }, { name: "status_lockedUntil_idx" });

// Existing supporting indexes.
ensureIndex(db.submission_jobs, { "submissionId": 1 }, { name: "submissionId_idx" });
ensureIndex(db.submission_jobs, { "status": 1 }, { name: "status_idx" });
ensureIndex(db.submission_jobs, { "createdAt": -1 }, { name: "createdAt_desc_idx" });
ensureIndex(db.submission_jobs, { "problemId": 1, "testcaseVersion": 1 }, { name: "problemId_testcaseVersion_idx" });

// Standalone per-testcase results. Hidden payload is never needed by these indexes.
dropIndexIfPresent(db.submission_test_results, "job_testcase_index");
dropIndexIfPresent(db.submission_test_results, "jobId_testcaseIndex_unique_idx");
ensureIndex(db.submission_test_results, { "jobId": 1, "testcaseIndex": 1, "attemptCount": 1 }, { unique: true, name: "jobId_testcase_attempt_unique_idx" });
ensureIndex(db.submission_test_results, { "jobId": 1, "testcaseType": 1 }, { name: "jobId_testcaseType_idx" });

// UserSolvedProblem unique award identity and per-user solved history queries.
ensureIndex(db.user_solved_problems, { "userId": 1, "problemId": 1 }, { unique: true, name: "userId_problemId_unique_idx" });
ensureIndex(db.user_solved_problems, { "userId": 1, "solvedAt": -1 }, { name: "userId_solvedAt_desc_idx" });

// One creation event per job makes orphan repair safe across multiple API instances.
ensureIndex(db.submission_outbox, { "aggregateId": 1, "eventType": 1 }, { unique: true, name: "aggregate_event_unique_idx" });
ensureIndex(db.submission_outbox, { "status": 1, "nextAttemptAt": 1, "lockedUntil": 1 }, { name: "status_nextAttempt_locked_idx" });

// ===== PROBLEM_BUNDLES COLLECTION INDEXES =====
ensureIndex(db.problem_bundles, { "name": 1 }, { name: "name_idx" });
ensureIndex(db.problem_bundles, { "difficulty": 1 }, { name: "difficulty_idx" });
ensureIndex(db.problem_bundles, { "createdAt": -1 }, { name: "createdAt_desc_idx" });

// ===== SUBSCRIPTIONS COLLECTION INDEXES =====
ensureIndex(db.subscriptions, { "userId": 1 }, { name: "userId_idx" });
ensureIndex(db.subscriptions, { "status": 1 }, { name: "status_idx" });
ensureIndex(db.subscriptions, { "endDate": 1 }, { name: "endDate_idx" });
dropIndexIfPresent(db.subscriptions, "expiryDate_idx");
// Batch member picker GET /api/admin/batches/users excludes DELETED accounts.
ensureIndex(db.users, { "accountStatus": 1 }, { name: "accountStatus_idx" });

// ===== BATCH / CLASSROOM INDEXES =====
// List batches by recency and optional active filter.
ensureIndex(db.batches, { "createdAt": -1 }, { name: "createdAt_desc_idx" });
ensureIndex(db.batches, { "active": 1, "createdAt": -1 }, { name: "active_createdAt_desc_idx" });
ensureIndex(db.batches, { "name": 1 }, { name: "name_idx" });
// Membership uniqueness and per-batch member scans / counts.
ensureIndex(db.batch_members, { "batchId": 1, "userId": 1 }, { unique: true, name: "batchId_userId_unique_idx" });
ensureIndex(db.batch_members, { "batchId": 1, "addedAt": 1 }, { name: "batchId_addedAt_idx" });
// userId -> batchIds for bundle access resolution (memberships of a user).
ensureIndex(db.batch_members, { "userId": 1, "batchId": 1 }, { name: "userId_batchId_idx" });
// Assignment uniqueness and per-batch assignment scans / counts.
ensureIndex(db.batch_assignments, { "batchId": 1, "problemId": 1 }, { unique: true, name: "batchId_problemId_unique_idx" });
ensureIndex(db.batch_assignments, { "batchId": 1, "assignedAt": 1 }, { name: "batchId_assignedAt_idx" });
// Bundle access grants: uniqueness and subject lookup for USER/BATCH grants.
ensureIndex(db.bundle_access_grants, { "bundleId": 1, "subjectType": 1, "subjectId": 1 }, { unique: true, name: "bundle_subject_unique_idx" });
ensureIndex(db.bundle_access_grants, { "subjectType": 1, "subjectId": 1, "bundleId": 1 }, { name: "subject_bundle_idx" });
ensureIndex(db.problem_bundles, { "isActive": 1, "visibility": 1 }, { name: "isActive_visibility_idx" });
// Batch reports query submissions with userId $in page-or-all members AND problemId $in assigned IDs/aliases.
// Existing submissions.userId_problemId_createdAt_desc_idx already covers that compound lookup.

print('Index creation script completed. Verify indexes with db.collection.getIndexes().');

// Fail migration/deployment validation if critical query indexes are absent or unsafe.
assertIndex(db.problems, "numericId_id_idx");
assertIndex(db.problems, "numericId_unique_sparse_idx", function(idx) {
  return idx.unique === true && idx.sparse === true;
});
assertIndex(db.submissions, "userId_createdAt_desc_idx");
assertIndex(db.submissions, "submissionJobId_unique_sparse_idx", function(idx) {
  return idx.unique === true && idx.partialFilterExpression != null;
});
assertIndex(db.submission_jobs, "status_lockedUntil_idx");
assertIndexKeys(db.user_solved_problems, { "userId": 1, "problemId": 1 }, function(idx) {
  return idx.unique === true;
});
assertIndex(db.submission_outbox, "aggregate_event_unique_idx", function(idx) {
  return idx.unique === true;
});
assertIndex(db.submission_outbox, "status_nextAttempt_locked_idx");
assertIndex(db.batch_members, "batchId_userId_unique_idx", function(idx) {
  return idx.unique === true;
});
assertIndex(db.batch_assignments, "batchId_problemId_unique_idx", function(idx) {
  return idx.unique === true;
});
assertIndex(db.batch_members, "userId_batchId_idx");
assertIndex(db.bundle_access_grants, "bundle_subject_unique_idx", function(idx) {
  return idx.unique === true;
});
assertIndex(db.bundle_access_grants, "subject_bundle_idx");
