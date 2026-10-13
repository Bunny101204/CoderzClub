import test from "node:test";
import assert from "node:assert/strict";
import {
  buildDraftKey,
  createDraftRecord,
  deriveDraftStatus,
  legacyPracticeKey,
  parseDraftRecord,
  readInitialDraftCode,
  reconcileDraft,
  shouldKeepaliveSync,
  KEEPALIVE_MAX_BYTES,
} from "./codeDraft.js";

test("draft keys namespace user, scope, problem, and language", () => {
  const practiceJava = buildDraftKey({ userId: "user-a", problemId: "p1", languageId: 62 });
  const practicePython = buildDraftKey({ userId: "user-a", problemId: "p1", languageId: 71 });
  const otherUser = buildDraftKey({ userId: "user-b", problemId: "p1", languageId: 62 });
  const contestX = buildDraftKey({ userId: "user-a", contestId: "contest-x", problemId: "p1", languageId: 62 });
  const contestY = buildDraftKey({ userId: "user-a", contestId: "contest-y", problemId: "p1", languageId: 62 });

  assert.equal(practiceJava, "coderzclub:draft:v1:user-a:practice:p1:62");
  assert.equal(practicePython, "coderzclub:draft:v1:user-a:practice:p1:71");
  assert.equal(contestX, "coderzclub:draft:v1:user-a:contest:contest-x:p1:62");
  assert.equal(contestY, "coderzclub:draft:v1:user-a:contest:contest-y:p1:62");
  assert.notEqual(practiceJava, practicePython);
  assert.notEqual(practiceJava, otherUser);
  assert.notEqual(practiceJava, contestX);
  assert.notEqual(contestX, contestY);
  assert.equal(buildDraftKey({ problemId: "p1", languageId: 62 }), null);
  assert.equal(buildDraftKey({ userId: "", problemId: "p1", languageId: 62 }), null);
});

test("parseDraftRecord accepts only versioned records", () => {
  const raw = JSON.stringify(createDraftRecord({
    code: "class A {}",
    dirty: true,
    baseRevision: 4,
    cloudUpdatedAt: "2026-10-12T00:00:00Z",
    localUpdatedAt: "2026-10-12T00:01:00Z",
  }));
  const parsed = parseDraftRecord(raw);
  assert.equal(parsed.code, "class A {}");
  assert.equal(parsed.dirty, true);
  assert.equal(parsed.baseRevision, 4);
  assert.equal(parseDraftRecord("class A {}"), null);
  assert.equal(parseDraftRecord("{"), null);
  assert.equal(parseDraftRecord(JSON.stringify({ version: 2, code: "x" })), null);
});

test("no local draft uses cloud and stores it clean", () => {
  const result = reconcileDraft({
    local: null,
    cloud: { code: "cloud", revision: 3, updatedAt: "t" },
    cloudAvailable: true,
  });
  assert.equal(result.action, "use-cloud");
  assert.equal(result.record.code, "cloud");
  assert.equal(result.record.dirty, false);
  assert.equal(result.record.baseRevision, 3);
});

test("clean local yields to a newer cloud revision", () => {
  const result = reconcileDraft({
    local: createDraftRecord({ code: "old", dirty: false, baseRevision: 2 }),
    cloud: { code: "newer", revision: 5, updatedAt: "t" },
    cloudAvailable: true,
  });
  assert.equal(result.action, "use-cloud");
  assert.equal(result.record.code, "newer");
  assert.equal(result.record.dirty, false);
  assert.equal(result.record.baseRevision, 5);
});

test("dirty local continues when cloud revision is unchanged", () => {
  const local = createDraftRecord({ code: "unsynced", dirty: true, baseRevision: 4 });
  const result = reconcileDraft({
    local,
    cloud: { code: "base", revision: 4, updatedAt: "t" },
    cloudAvailable: true,
  });
  assert.equal(result.action, "keep-local");
  assert.equal(result.record.code, "unsynced");
  assert.equal(result.record.dirty, true);
});

test("dirty local with the same cloud code adopts the cloud revision", () => {
  const result = reconcileDraft({
    local: createDraftRecord({ code: "same", dirty: true, baseRevision: 2 }),
    cloud: { code: "same", revision: 3, updatedAt: "server" },
    cloudAvailable: true,
  });
  assert.equal(result.action, "adopt-cloud-revision");
  assert.equal(result.record.dirty, false);
  assert.equal(result.record.baseRevision, 3);
  assert.equal(result.record.code, "same");
});

test("dirty local with different newer cloud code is a conflict", () => {
  const result = reconcileDraft({
    local: createDraftRecord({ code: "local", dirty: true, baseRevision: 2 }),
    cloud: { code: "other device", revision: 6, updatedAt: "server" },
    cloudAvailable: true,
  });
  assert.equal(result.action, "conflict");
  assert.equal(result.record.code, "local");
  assert.equal(result.record.dirty, true);
  assert.equal(result.cloud.code, "other device");
  assert.equal(result.cloud.revision, 6);
});

test("dirty local is kept when the cloud draft does not exist yet", () => {
  const result = reconcileDraft({
    local: createDraftRecord({ code: "new draft", dirty: true, baseRevision: 0 }),
    cloud: { code: null, revision: 0, updatedAt: null },
    cloudAvailable: true,
  });
  assert.equal(result.action, "keep-local");
  assert.equal(result.record.code, "new draft");
  assert.equal(result.record.dirty, true);
});

test("an unscoped legacy key is not treated as the current user's draft", () => {
  const storage = {
    getItem(key) {
      return key === legacyPracticeKey("p1", 62) ? "alice code" : null;
    },
  };
  assert.equal(readInitialDraftCode({
    userId: "bob",
    problemId: "p1",
    languageId: 62,
    storage,
  }), null);
  assert.notEqual(
    buildDraftKey({ userId: "alice", problemId: "p1", languageId: 62 }),
    buildDraftKey({ userId: "bob", problemId: "p1", languageId: 62 })
  );
});

test("offline load keeps the dirty local draft", () => {
  const local = createDraftRecord({ code: "offline", dirty: true, baseRevision: 1 });
  const result = reconcileDraft({ local, cloud: null, cloudAvailable: false });
  assert.equal(result.action, "keep-local");
  assert.equal(result.record.code, "offline");
});

test("keepalive allows a small body and rejects an oversized body", () => {
  assert.equal(shouldKeepaliveSync(JSON.stringify({ code: "print(1)", expectedRevision: 1 })), true);
  const oversized = JSON.stringify({ code: "x".repeat(KEEPALIVE_MAX_BYTES), expectedRevision: 1 });
  assert.equal(shouldKeepaliveSync(oversized), false);
  assert.equal(oversized.includes("x".repeat(100)), true);
});

test("status never claims a sync while the draft is dirty", () => {
  const dirty = createDraftRecord({ code: "local", dirty: true, baseRevision: 2 });
  assert.equal(deriveDraftStatus({ record: dirty }), "Saved locally");
  assert.equal(deriveDraftStatus({ record: dirty, syncing: true }), "Sync pending");
  assert.equal(deriveDraftStatus({ record: dirty, syncFailed: true }), "Sync unavailable");
  assert.equal(
    deriveDraftStatus({
      record: createDraftRecord({ code: "cloud", dirty: false, baseRevision: 3 }),
      syncFailed: true,
    }),
    "Sync unavailable"
  );
  assert.equal(deriveDraftStatus({ storageError: true, record: dirty }), "Local draft save unavailable");
  assert.equal(deriveDraftStatus({ conflict: { code: "c", revision: 3 }, record: dirty }), "Cloud draft conflict");
  assert.equal(
    deriveDraftStatus({ record: createDraftRecord({ code: "cloud", dirty: false, baseRevision: 3 }) }),
    "Synced to account"
  );
  assert.equal(deriveDraftStatus({ record: createDraftRecord({ code: "starter", dirty: false, baseRevision: 0 }) }), "");
});
