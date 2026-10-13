import test from "node:test";
import assert from "node:assert/strict";
import { createDraftSession } from "./editorDraftSession.js";
import { flushActiveDraftBoundary, setActiveDraftSession } from "./draftBoundary.js";
import { buildDraftKey, legacyPracticeKey } from "./codeDraft.js";

function memoryStorage() {
  const data = {};
  let failWrites = false;
  return {
    getItem(key) {
      return Object.prototype.hasOwnProperty.call(data, key) ? data[key] : null;
    },
    setItem(key, value) {
      if (failWrites) throw new Error("quota");
      data[key] = String(value);
    },
    removeItem(key) {
      delete data[key];
    },
    data,
    setFailWrites(value) {
      failWrites = value;
    },
  };
}

function manualSchedule() {
  const jobs = [];
  return {
    later(fn) {
      jobs.push(fn);
      return jobs.length;
    },
    cancel(id) {
      jobs[id - 1] = null;
    },
    flush() {
      const pending = jobs.splice(0);
      pending.forEach((fn) => fn && fn());
    },
  };
}

function harness(overrides = {}) {
  const storage = overrides.storage || memoryStorage();
  const schedule = manualSchedule();
  const calls = { get: [], put: [], keepalive: [] };
  const transport = {
    async get(identity) {
      calls.get.push(identity);
      if (overrides.get) return overrides.get(identity);
      return { code: null, revision: 0, updatedAt: null };
    },
    async put(identity, body) {
      calls.put.push({ identity, body });
      if (overrides.put) return overrides.put(identity, body);
      return { ok: true, draft: { code: body.code, revision: (body.expectedRevision || 0) + 1, updatedAt: "server" } };
    },
    putKeepalive(identity, body) {
      calls.keepalive.push({ identity, body });
      return true;
    },
  };
  const views = [];
  const applied = [];
  const session = createDraftSession({
    storage,
    transport,
    schedule,
    now: () => "local-time",
    clock: () => 1_000,
    onApply: (code) => applied.push(code),
    onView: (view) => views.push(view),
  });
  return { session, storage, schedule, calls, applied, views };
}

function practice(languageId = 62) {
  return { userId: "user-a", problemId: "p1", languageId, starterCode: "starter" };
}

test("typing writes local storage and does not call the cloud", async () => {
  const { session, storage, schedule, calls } = harness();
  await session.open(practice());
  session.noteEdit("starter");
  session.noteEdit("typed solution");
  assert.equal(calls.put.length, 0);
  assert.equal(calls.keepalive.length, 0);
  assert.equal(calls.get.length, 1);
  schedule.flush();
  const saved = JSON.parse(storage.getItem(buildDraftKey(practice())));
  assert.equal(saved.code, "typed solution");
  assert.equal(saved.dirty, true);
  assert.equal(saved.baseRevision, 0);
  assert.equal(calls.put.length, 0);
});

test("language switch syncs the old language and restores the new one", async () => {
  const { session, calls } = harness({
    get(identity) {
      if (identity.languageId === 71) return { code: "python cloud", revision: 2, updatedAt: "t" };
      return { code: null, revision: 0, updatedAt: null };
    },
  });
  await session.open(practice(62));
  session.noteEdit("starter");
  session.noteEdit("java solution");
  await session.open(practice(71));
  assert.equal(calls.put.length, 1);
  assert.equal(calls.put[0].identity.languageId, 62);
  assert.equal(calls.put[0].body.code, "java solution");
  assert.equal(calls.get.at(-1).languageId, 71);
  assert.equal(session, session);
  await session.open(practice(62));
  const javaGets = calls.get.filter((item) => item.languageId === 62);
  assert.ok(javaGets.length >= 1);
});

test("an unscoped legacy key is not imported for the logged-in user", async () => {
  const storage = memoryStorage();
  storage.setItem(legacyPracticeKey("p1", 62), "alice code");
  const { session, applied } = harness({ storage });
  await session.open({ userId: "bob", problemId: "p1", languageId: 62, starterCode: "starter" });
  assert.equal(applied.at(-1), "starter");
  assert.equal(storage.getItem(legacyPracticeKey("p1", 62)), "alice code");
  assert.equal(storage.getItem(buildDraftKey({
    userId: "bob",
    problemId: "p1",
    languageId: 62,
  })), null);
  const aliceKey = buildDraftKey({ userId: "alice", problemId: "p1", languageId: 62 });
  const bobKey = buildDraftKey({ userId: "bob", problemId: "p1", languageId: 62 });
  assert.notEqual(aliceKey, bobKey);
});

test("a failed local write does not throw or delete other drafts", async () => {
  const storage = memoryStorage();
  storage.setItem("coderzclub:draft:v1:user-b:practice:p1:62", "keep me");
  const { session, schedule, views } = harness({ storage });
  await session.open(practice());
  session.noteEdit("starter");
  storage.setFailWrites(true);
  session.noteEdit("cannot store");
  schedule.flush();
  assert.equal(views.at(-1).status, "Local draft save unavailable");
  assert.equal(storage.getItem("coderzclub:draft:v1:user-b:practice:p1:62"), "keep me");
});

test("pagehide keepalive leaves the local draft dirty", async () => {
  const { session, storage, calls } = harness();
  await session.open(practice());
  session.noteEdit("starter");
  session.noteEdit("close me");
  await session.syncBoundary({ keepalive: true });
  assert.equal(calls.keepalive.length, 1);
  assert.equal(calls.put.length, 0);
  const saved = JSON.parse(storage.getItem(buildDraftKey(practice())));
  assert.equal(saved.dirty, true);
  assert.equal(saved.code, "close me");
});

test("oversized keepalive is skipped and a live boundary can still sync", async () => {
  const { session, storage, calls } = harness();
  await session.open(practice());
  session.noteEdit("starter");
  session.noteEdit("y".repeat(70 * 1024));
  await session.syncBoundary({ keepalive: true });
  assert.equal(calls.keepalive.length, 0);
  assert.equal(calls.put.length, 0);
  const saved = JSON.parse(storage.getItem(buildDraftKey(practice())));
  assert.equal(saved.code.length, 70 * 1024);
  assert.equal(saved.dirty, true);
  await session.syncBoundary({ keepalive: false });
  assert.equal(calls.put.length, 1);
  assert.equal(calls.put[0].body.code.length, 70 * 1024);
  const synced = JSON.parse(storage.getItem(buildDraftKey(practice())));
  assert.equal(synced.dirty, false);
  assert.equal(synced.baseRevision, 1);
});

test("conflict keeps local code until the user chooses", async () => {
  const storage = memoryStorage();
  const key = buildDraftKey(practice());
  storage.setItem(key, JSON.stringify({
    version: 1,
    code: "local",
    dirty: true,
    baseRevision: 2,
    cloudUpdatedAt: null,
    localUpdatedAt: "t",
    conflict: false,
  }));
  const { session, applied, views, calls } = harness({
    storage,
    get: () => ({ code: "cloud", revision: 5, updatedAt: "server" }),
  });
  await session.open(practice());
  assert.equal(applied.at(-1), "local");
  assert.equal(views.at(-1).status, "Cloud draft conflict");
  assert.equal(views.at(-1).conflict.code, "cloud");
  assert.equal(calls.put.length, 0);

  session.useCloud();
  assert.equal(applied.at(-1), "cloud");
  assert.equal(JSON.parse(storage.getItem(key)).dirty, false);
  assert.equal(JSON.parse(storage.getItem(key)).baseRevision, 5);
});

test("keep this draft retries with the conflict revision", async () => {
  const storage = memoryStorage();
  storage.setItem(buildDraftKey(practice()), JSON.stringify({
    version: 1,
    code: "local",
    dirty: true,
    baseRevision: 2,
    conflict: false,
  }));
  const { session, calls } = harness({
    storage,
    get: () => ({ code: "cloud", revision: 5, updatedAt: "server" }),
    put: (_identity, body) => {
      if (body.expectedRevision !== 5) {
        return { ok: false, status: 409, body: { code: "newer", revision: 6, updatedAt: "later" } };
      }
      return { ok: true, draft: { code: body.code, revision: 6, updatedAt: "acked" } };
    },
  });
  await session.open(practice());
  await session.keepLocal();
  assert.equal(calls.put.length, 1);
  assert.equal(calls.put[0].body.expectedRevision, 5);
  assert.equal(calls.put[0].body.code, "local");
  assert.equal(JSON.parse(storage.getItem(buildDraftKey(practice()))).dirty, false);
});

test("identical cloud code after an unobserved sync becomes clean", async () => {
  const storage = memoryStorage();
  storage.setItem(buildDraftKey(practice()), JSON.stringify({
    version: 1,
    code: "same",
    dirty: true,
    baseRevision: 2,
    conflict: false,
  }));
  const { session, views } = harness({
    storage,
    get: () => ({ code: "same", revision: 3, updatedAt: "server" }),
  });
  await session.open(practice());
  assert.equal(views.at(-1).status, "Synced to account");
  assert.equal(JSON.parse(storage.getItem(buildDraftKey(practice()))).baseRevision, 3);
  assert.equal(JSON.parse(storage.getItem(buildDraftKey(practice()))).dirty, false);
});

test("a failed cloud load keeps the local draft and does not show the starter", async () => {
  const storage = memoryStorage();
  const key = buildDraftKey(practice());
  storage.setItem(key, JSON.stringify({
    version: 1,
    code: "local work",
    dirty: true,
    baseRevision: 4,
    conflict: false,
  }));
  const { session, applied, views } = harness({
    storage,
    get() {
      throw new Error("network");
    },
  });
  await session.open(practice());
  assert.equal(applied.at(-1), "local work");
  assert.equal(views.at(-1).status, "Sync unavailable");
  const saved = JSON.parse(storage.getItem(key));
  assert.equal(saved.code, "local work");
  assert.equal(saved.dirty, true);
  assert.equal(saved.baseRevision, 4);
});

test("a failed boundary save keeps the acknowledged revision", async () => {
  const storage = memoryStorage();
  const key = buildDraftKey(practice());
  storage.setItem(key, JSON.stringify({
    version: 1,
    code: "unsynced",
    dirty: true,
    baseRevision: 4,
    conflict: false,
  }));
  const { session, views, calls } = harness({
    storage,
    put: () => ({ ok: false, status: 503 }),
  });
  await session.open(practice());
  await session.syncBoundary({ keepalive: false });
  await session.syncBoundary({ keepalive: false });
  assert.equal(calls.put.length, 2);
  assert.equal(calls.put[0].body.expectedRevision, 4);
  assert.equal(calls.put[0].body.code, "unsynced");
  assert.equal(views.at(-1).status, "Sync unavailable");
  const saved = JSON.parse(storage.getItem(key));
  assert.equal(saved.code, "unsynced");
  assert.equal(saved.dirty, true);
  assert.equal(saved.baseRevision, 4);
});

test("a late java save cannot mark the python draft clean", async () => {
  let release;
  const gate = new Promise((resolve) => {
    release = resolve;
  });
  const { session, storage, calls, applied } = harness({
    put: () => gate.then(() => ({
      ok: true,
      draft: { code: "java solution", revision: 9, updatedAt: "server" },
    })),
  });
  await session.open(practice(62));
  session.noteEdit("starter");
  session.noteEdit("java solution");
  const switched = session.open(practice(71));
  assert.equal(calls.put.length, 1);
  assert.equal(calls.put[0].identity.languageId, 62);
  assert.equal(calls.put[0].body.code, "java solution");
  assert.equal(calls.get.filter((item) => item.languageId === 71).length, 0);
  release();
  await switched;
  assert.equal(applied.at(-1), "starter");
  assert.equal(calls.get.filter((item) => item.languageId === 71).length, 1);
  assert.equal(storage.getItem(buildDraftKey(practice(71))), null);
  const java = JSON.parse(storage.getItem(buildDraftKey(practice(62))));
  assert.equal(java.code, "java solution");
  assert.equal(java.dirty, false);
  assert.equal(java.baseRevision, 9);
});

test("navigation sync keeps the outgoing identity", async () => {
  let release;
  const gate = new Promise((resolve) => {
    release = resolve;
  });
  const problemA = { userId: "user-a", problemId: "problem-a", languageId: 62, starterCode: "starter" };
  const problemB = { userId: "user-a", problemId: "problem-b", languageId: 62, starterCode: "starter-b" };
  const contest = {
    userId: "user-a",
    contestId: "contest-x",
    problemId: "problem-a",
    languageId: 62,
    starterCode: "contest-starter",
  };
  const { session, storage, calls, applied } = harness({
    put: (identity) => gate.then(() => ({
      ok: true,
      draft: { code: identity.problemId, revision: 6, updatedAt: "server" },
    })),
  });
  await session.open(problemA);
  session.noteEdit("starter");
  session.noteEdit("solution a");
  const toB = session.open(problemB);
  assert.equal(calls.put[0].identity.problemId, "problem-a");
  assert.equal(calls.put[0].identity.contestId, null);
  assert.equal(calls.put[0].body.code, "solution a");
  release();
  await toB;
  assert.equal(applied.at(-1), "starter-b");
  assert.equal(storage.getItem(buildDraftKey(problemB)), null);
  const savedA = JSON.parse(storage.getItem(buildDraftKey(problemA)));
  assert.equal(savedA.code, "solution a");
  assert.equal(savedA.baseRevision, 6);
  assert.equal(savedA.dirty, false);

  session.noteEdit("starter-b");
  session.noteEdit("solution b");
  await session.open(contest);
  assert.equal(calls.put.at(-1).identity.problemId, "problem-b");
  assert.equal(calls.put.at(-1).identity.contestId, null);
  assert.equal(calls.put.at(-1).body.code, "solution b");
  assert.equal(applied.at(-1), "contest-starter");
  assert.equal(storage.getItem(buildDraftKey(contest)), null);
  const savedB = JSON.parse(storage.getItem(buildDraftKey(problemB)));
  assert.equal(savedB.code, "solution b");
  assert.equal(savedB.dirty, false);
});

test("dedupe does not suppress a different draft in the same window", async () => {
  const { session, calls } = harness();
  const first = { userId: "user-a", problemId: "problem-a", languageId: 62, starterCode: "starter" };
  const second = { userId: "user-a", problemId: "problem-b", languageId: 62, starterCode: "starter" };
  await session.open(first);
  session.noteEdit("starter");
  session.noteEdit("same");
  await session.syncBoundary({ keepalive: false });
  await session.open(second);
  session.noteEdit("starter");
  session.noteEdit("same");
  await session.syncBoundary({ keepalive: false });
  assert.equal(calls.put.length, 2);
  assert.equal(calls.put[0].identity.problemId, "problem-a");
  assert.equal(calls.put[1].identity.problemId, "problem-b");
  assert.equal(calls.put[0].body.code, "same");
  assert.equal(calls.put[1].body.code, "same");
});

test("logout starts keepalive with the current token and leaves other users' drafts", async () => {
  const storage = memoryStorage();
  const bobKey = buildDraftKey({ userId: "bob", problemId: "p1", languageId: 62 });
  storage.setItem(bobKey, "bob draft");
  let token = "alice-token";
  const seen = [];
  const transport = {
    async get() {
      return { code: null, revision: 0, updatedAt: null };
    },
    async put() {
      return { ok: false, status: 500 };
    },
    putKeepalive(identity, body) {
      seen.push({ token, identity, body });
      return true;
    },
  };
  const session = createDraftSession({
    storage,
    transport,
    schedule: manualSchedule(),
    now: () => "local-time",
    clock: () => 1_000,
  });
  const unregister = setActiveDraftSession(session);
  try {
    await session.open(practice());
    session.noteEdit("starter");
    session.noteEdit("alice draft");
    const pending = flushActiveDraftBoundary({ keepalive: true });
    assert.equal(seen.length, 1);
    assert.equal(seen[0].token, "alice-token");
    assert.equal(seen[0].identity.userId, "user-a");
    assert.equal(seen[0].body.code, "alice draft");
    token = null;
    assert.equal(storage.getItem(bobKey), "bob draft");
    const alice = JSON.parse(storage.getItem(buildDraftKey(practice())));
    assert.equal(alice.code, "alice draft");
    assert.equal(alice.dirty, true);
    await pending;
    assert.equal(JSON.parse(storage.getItem(buildDraftKey(practice()))).dirty, true);
  } finally {
    unregister();
  }
});
