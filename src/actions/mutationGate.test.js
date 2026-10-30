import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {
  createMutationGate,
  createOperationId,
  isUncertainOutcome,
  runExclusive,
} from "./mutationGate.js";
import {
  adoptServerAttempt,
  attachAcceptedJob,
  claimAttempt,
  memoryStorage,
  readAttempt,
  settleAttempt,
} from "./submissionAttempt.js";

test("a single begin is allowed", () => {
  const gate = createMutationGate();
  const attempt = gate.tryBegin();
  assert.ok(attempt);
  assert.equal(gate.pending, true);
  attempt.finish();
  assert.equal(gate.pending, false);
});

test("a second begin in the same turn is rejected", () => {
  const gate = createMutationGate();
  const first = gate.tryBegin();
  assert.equal(gate.tryBegin(), null);
  first.finish();
});

test("five rapid begins create one attempt", () => {
  const gate = createMutationGate();
  const attempts = [0, 1, 2, 3, 4].map(() => gate.tryBegin()).filter(Boolean);
  assert.equal(attempts.length, 1);
  attempts[0].finish();
});

test("repeated begin while a submit is held is rejected", async () => {
  const gate = createMutationGate();
  let calls = 0;
  const pending = runExclusive(gate, async () => {
    calls += 1;
    await Promise.resolve();
  });
  const second = await runExclusive(gate, async () => {
    calls += 1;
  });
  assert.equal(second.skipped, true);
  await pending;
  assert.equal(calls, 1);
});

test("a second begin while the first request is pending is rejected", async () => {
  const gate = createMutationGate();
  let release;
  const first = runExclusive(gate, () => new Promise((resolve) => {
    release = resolve;
  }));
  assert.equal((await runExclusive(gate, async () => "again")).skipped, true);
  release();
  assert.equal((await first).skipped, false);
});

test("the gate stays held for a delayed response of any listed duration", async () => {
  const gate = createMutationGate();
  let release;
  const pending = runExclusive(gate, () => new Promise((resolve) => {
    release = resolve;
  }));
  for (const seconds of [2, 5, 15, 30]) {
    assert.equal(gate.pending, true, `still pending at ${seconds}s`);
    assert.equal(gate.tryBegin(), null);
  }
  release();
  await pending;
  assert.equal(gate.pending, false);
});

test("a dropped response keeps the same operation id", () => {
  const storage = memoryStorage();
  const first = claimAttempt({ storage, problemId: "p1", now: 1_000, createId: () => "op-1" });
  assert.equal(isUncertainOutcome({ code: "ECONNABORTED", message: "timeout of 30000ms exceeded" }), true);
  assert.equal(isUncertainOutcome({ code: "ERR_NETWORK", message: "Network Error" }), true);
  const retry = claimAttempt({ storage, problemId: "p1", now: 1_500, createId: () => "op-2" });
  assert.equal(retry.operationId, first.operationId);
  assert.equal(retry.reuse, true);
});

test("a timeout after acceptance resumes the accepted job", () => {
  const storage = memoryStorage();
  const first = claimAttempt({ storage, problemId: "p1", now: 1_000, createId: () => "op-1" });
  attachAcceptedJob(storage, first.operationId, "job-7", "p1");
  const refreshed = claimAttempt({ storage, problemId: "p1", now: 20_000, createId: () => "op-new" });
  assert.equal(refreshed.resume, true);
  assert.equal(refreshed.jobId, "job-7");
  assert.equal(refreshed.operationId, "op-1");
});

test("retry after an uncertain attempt reuses the operation id", () => {
  const storage = memoryStorage();
  claimAttempt({ storage, problemId: "p1", now: 1_000, createId: () => "op-1" });
  const retry = claimAttempt({ storage, problemId: "p1", now: 2_000, createId: () => "op-2" });
  assert.equal(retry.operationId, "op-1");
});

test("a new gate after refresh still sees the accepted job", () => {
  const storage = memoryStorage();
  const firstGate = createMutationGate();
  const attempt = firstGate.tryBegin();
  const claimed = claimAttempt({ storage, problemId: "p1", now: 1_000, createId: () => "op-1" });
  attachAcceptedJob(storage, claimed.operationId, "job-9", "p1");
  attempt.finish();
  const refreshedGate = createMutationGate();
  assert.equal(refreshedGate.pending, false);
  const restored = readAttempt(storage, "p1");
  assert.equal(restored.jobId, "job-9");
  assert.equal(claimAttempt({ storage, problemId: "p1", now: 5_000, createId: () => "op-x" }).resume, true);
});

test("two tabs that share storage use one operation id", () => {
  const shared = memoryStorage();
  const tabA = claimAttempt({ storage: shared, problemId: "p1", now: 1_000, createId: () => "op-a" });
  const tabB = claimAttempt({ storage: shared, problemId: "p1", now: 1_010, createId: () => "op-b" });
  assert.equal(tabB.operationId, tabA.operationId);
});

test("a finished attempt can be followed by a genuine new submission", () => {
  const gate = createMutationGate();
  const storage = memoryStorage();
  const first = gate.tryBegin();
  const claimed = claimAttempt({ storage, problemId: "p1", now: 1_000, createId: () => "op-1" });
  first.finish();
  settleAttempt(storage, claimed.operationId, "p1");
  const second = gate.tryBegin();
  const again = claimAttempt({ storage, problemId: "p1", now: 2_000, createId: () => "op-2" });
  assert.ok(second);
  assert.equal(again.operationId, "op-2");
  assert.notEqual(again.operationId, claimed.operationId);
  second.finish();
});

test("identical code does not keep the same operation id after the attempt settles", () => {
  const storage = memoryStorage();
  const code = "print(1)";
  const first = claimAttempt({ storage, problemId: "p1", now: 1_000, createId: () => "op-1" });
  settleAttempt(storage, first.operationId, "p1");
  const second = claimAttempt({ storage, problemId: "p1", now: 2_000, createId: () => `op-for-${code.length}` });
  assert.equal(second.operationId, "op-for-8");
  assert.notEqual(second.operationId, first.operationId);
});

test("a confirmed failure releases the gate and the next attempt is new", async () => {
  const gate = createMutationGate();
  const storage = memoryStorage();
  const claimed = claimAttempt({ storage, problemId: "p1", now: 1_000, createId: () => "op-1" });
  const failed = await runExclusive(gate, async () => {
    throw Object.assign(new Error("Please wait"), { response: { status: 429, data: { error: "Please wait" } } });
  }).catch((error) => error);
  assert.equal(failed.response.status, 429);
  assert.equal(isUncertainOutcome(failed), false);
  assert.equal(gate.pending, false);
  settleAttempt(storage, claimed.operationId, "p1");
  const retry = claimAttempt({ storage, problemId: "p1", now: 30_000, createId: () => "op-2" });
  assert.equal(retry.operationId, "op-2");
  const next = await runExclusive(gate, async () => "ok");
  assert.equal(next.skipped, false);
});

test("finish is safe to call twice and from an unmount cleanup", () => {
  const gate = createMutationGate();
  const attempt = gate.tryBegin();
  attempt.finish();
  attempt.finish();
  assert.equal(gate.pending, false);
  assert.ok(gate.tryBegin());
});

test("operation ids are unique and a confirmed http error is not uncertain", () => {
  assert.notEqual(createOperationId(), createOperationId());
  assert.equal(isUncertainOutcome({ response: { status: 400 }, message: "bad" }), false);
  assert.equal(isUncertainOutcome({ response: { status: 409 }, message: "in progress" }), false);
});

test("a second claim does not replace a stored job id", () => {
  const storage = memoryStorage();
  const first = claimAttempt({ storage, problemId: "p1", now: 1_000, createId: () => "op-1" });
  attachAcceptedJob(storage, first.operationId, "job-7", "p1");
  const second = claimAttempt({ storage, problemId: "p1", now: 1_500, createId: () => "op-2" });
  assert.equal(second.operationId, "op-1");
  assert.equal(second.jobId, "job-7");
  assert.equal(readAttempt(storage, "p1").operationId, "op-1");
  assert.equal(readAttempt(storage, "p1").jobId, "job-7");
});

test("a 409 owner key replaces a local id that has no job and the next claim reuses it", () => {
  const storage = memoryStorage();
  claimAttempt({ storage, problemId: "p1", now: 1_000, createId: () => "op-local" });
  const adopted = adoptServerAttempt(storage, {
    operationId: "op-server",
    problemId: "p1",
    now: 1_200,
  });
  assert.equal(adopted.operationId, "op-server");
  assert.equal(adopted.jobId, null);
  const retry = claimAttempt({ storage, problemId: "p1", now: 1_300, createId: () => "op-new" });
  assert.equal(retry.operationId, "op-server");
  assert.equal(retry.reuse, true);
});

test("adopting the same key keeps a stored job id", () => {
  const storage = memoryStorage();
  const first = claimAttempt({ storage, problemId: "p1", now: 1_000, createId: () => "op-1" });
  attachAcceptedJob(storage, first.operationId, "job-7", "p1");
  const adopted = adoptServerAttempt(storage, { operationId: "op-1", problemId: "p1", now: 2_000 });
  assert.equal(adopted.jobId, "job-7");
  assert.equal(readAttempt(storage, "p1").jobId, "job-7");
});

test("a job id written by another tab before this tab stores its id is kept", () => {
  const storage = memoryStorage();
  let reads = 0;
  const wrapped = {
    getItem(key) {
      reads += 1;
      if (reads === 2) {
        storage.setItem(key, JSON.stringify({
          operationId: "op-a",
          problemId: "p1",
          contestId: "",
          at: 1_000,
          jobId: "job-1",
          settled: false,
        }));
      }
      return storage.getItem(key);
    },
    setItem(key, value) {
      storage.setItem(key, value);
    },
    removeItem(key) {
      storage.removeItem(key);
    },
  };
  const claimed = claimAttempt({ storage: wrapped, problemId: "p1", now: 1_500, createId: () => "op-b" });
  assert.equal(claimed.operationId, "op-a");
  assert.equal(claimed.jobId, "job-1");
  assert.equal(readAttempt(storage, "p1").jobId, "job-1");
});

test("submit takes the gate before it waits for submission limits", () => {
  const source = fs.readFileSync(new URL("../Components/Judge0CodeEditor.jsx", import.meta.url), "utf8");
  const start = source.indexOf("const handleSubmitSolution");
  const end = source.indexOf("const startPollingFallback");
  const submit = source.slice(start, end);
  assert.ok(submit.indexOf("tryBegin()") < submit.indexOf("getLimits"));
  assert.match(source, /Idempotency-Key|operationId/);
});
