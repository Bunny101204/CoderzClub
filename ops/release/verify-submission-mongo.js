// Read-only mongosh preflight. Select the application database explicitly.
// No create/drop/update/delete, collection creation, or write transaction occurs.
(() => {
  const failures = [];
  const warnings = [];
  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
  const hello = db.runCommand({ hello: 1 });
  if (!hello.ok || !hello.setName || hello.msg === "isdbgrid" || !hello.isWritablePrimary) {
    failures.push("A writable replica-set primary is required by the current admission implementation");
  }
  if (!hello.logicalSessionTimeoutMinutes) failures.push("MongoDB sessions are unavailable");
  const required = [
    ["mutation_idempotency", "idempotency_operation_unique", { userId: 1, scope: 1, operationKey: 1 }, true],
    ["mutation_idempotency", "idempotency_open_subject_unique", { userId: 1, scope: 1, subjectId: 1 }, true, { open: true }],
    ["mutation_idempotency", "mutation_idempotency_ttl", { expiresAt: 1 }, false, undefined, 0],
    ["submission_jobs", "submission_job_idempotency_unique", { userId: 1, idempotencyScope: 1, idempotencyKey: 1 }, true, { idempotencyKey: { $exists: true } }],
    ["submission_outbox", "aggregate_event_unique_idx", { aggregateId: 1, eventType: 1 }, true],
    ["submissions", "submissionJobId_unique_sparse_idx", { submissionJobId: 1 }, true, { submissionJobId: { $type: "string" } }],
    ["user_solved_problems", null, { userId: 1, problemId: 1 }, true],
    ["submission_test_results", "job_testcase_attempt_index", { jobId: 1, testcaseIndex: 1, attemptCount: 1 }, true],
    ["contest_problem_snapshots", "contestId_problemId_unique_idx", { contestId: 1, problemId: 1 }, true],
    ["contest_problem_solves", "contestId_userId_problemId_unique_idx", { contestId: 1, userId: 1, problemId: 1 }, true],
    ["contest_scores", "contestId_userId_unique_idx", { contestId: 1, userId: 1 }, true],
    ["code_drafts", "userId_scopeKey_problemId_languageId_unique_idx", { userId: 1, scopeKey: 1, problemId: 1, languageId: 1 }, true],
  ];
  const infos = db.getCollectionInfos({}, { nameOnly: true });
  const names = new Set(infos.map(c => c.name));
  const indexes = {};
  for (const [collection, name, key, unique, partial, ttl] of required) {
    if (!names.has(collection)) {
      failures.push(`Missing collection ${collection}`);
      continue;
    }
    indexes[collection] ||= db.getCollection(collection).getIndexes();
    const match = indexes[collection].find(i => (!name || i.name === name) && same(i.key, key));
    if (!match || Boolean(match.unique) !== unique || Boolean(match.sparse) || match.hidden
        || !same(match.partialFilterExpression, partial)
        || (ttl === undefined ? match.expireAfterSeconds !== undefined : Number(match.expireAfterSeconds) !== ttl)
        || (match.collation && match.collation.locale !== "simple")) {
      failures.push(`Missing or incompatible index ${collection}.${name || "unique(userId,problemId)"}`);
    }
  }
  for (const collection of ["users", "problems"]) {
    if (!names.has(collection)) failures.push(`Missing collection ${collection}`);
  }
  for (const [collection, key] of [
    ["submission_jobs", { status: 1, lockedUntil: 1 }],
    ["submission_outbox", { status: 1, nextAttemptAt: 1, lockedUntil: 1 }],
    ["submissions", { userId: 1, createdAt: -1 }],
  ]) {
    if (!(indexes[collection] || []).some(i => same(i.key, key) && !i.hidden)) {
      warnings.push(`Review performance index ${collection} ${JSON.stringify(key)}`);
    }
  }
  // Privilege inspection is read-only; it cannot prove successful write operations.
  const auth = db.runCommand({ connectionStatus: 1, showPrivileges: true });
  const privileges = auth.authInfo?.authenticatedUserPrivileges;
  if (!auth.ok || !privileges?.length) {
    warnings.push("Effective privileges unavailable (or local auth disabled); verify the deployed application's role separately");
  } else {
    const actions = new Set(privileges.flatMap(p => {
      const r = p.resource || {};
      return r.anyResource || ((r.db === db.getName() || r.db === "") && r.collection === "") ? p.actions : [];
    }));
    print(JSON.stringify({ database: db.getName(), databaseWideActions: [...actions].sort() }));
    warnings.push("Review collection-scoped privileges, startup createIndex privileges, and read/write access for API and worker identities");
  }
  if (!failures.length) {
    const session = db.getMongo().startSession();
    try {
      session.startTransaction({ readConcern: { level: "snapshot" } });
      session.getDatabase(db.getName()).getCollection("submission_jobs").findOne({}, { _id: 1 });
      session.abortTransaction();
    } finally { session.endSession(); }
  }
  print(JSON.stringify({ database: db.getName(), checks: required.length, failures, warnings }));
  if (failures.length) throw new Error("Read-only submission preflight failed; no data or indexes changed");
})();
