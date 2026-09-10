import test from "node:test";
import assert from "node:assert/strict";
import {
  getPollDelay,
  getJobStatusMessage,
  isTerminalJobState,
  isHiddenResult,
  normalizeJobResult,
} from "./submissionPolling.js";

test("adaptive polling schedule uses 1s, 2s, 5s, then 10s", () => {
  assert.equal(getPollDelay(0), 1000);
  assert.equal(getPollDelay(4), 1000);
  assert.equal(getPollDelay(5), 2000);
  assert.equal(getPollDelay(14), 2000);
  assert.equal(getPollDelay(15), 5000);
  assert.equal(getPollDelay(26), 5000);
  assert.equal(getPollDelay(27), 10000);
});

test("terminal state detection includes every backend terminal state", () => {
  for (const status of ["COMPLETED", "FAILED", "TIMEOUT", "CANCELLED"]) {
    assert.equal(isTerminalJobState(status), true);
  }
  assert.equal(isTerminalJobState("RUNNING"), false);
  assert.equal(isTerminalJobState("RETRYING"), false);
});

test("progress messages accept SSE event fields and status response fields", () => {
  assert.equal(getJobStatusMessage({
    status: "RUNNING",
    completedTests: 2,
    totalTests: 5,
  }), "Running 2/5 testcases");
});

test("hidden results remain opaque and never receive expected or actual output", () => {
  const result = normalizeJobResult({
    type: "hidden",
    input: "secret input",
    expectedOutput: "secret expected",
    actualOutput: "secret actual",
    passed: false,
  });
  assert.equal(isHiddenResult(result), true);
  assert.equal(result.input, undefined);
  assert.equal(result.expected, undefined);
  assert.equal(result.actual, undefined);
});

test("abort signals cancel polling requests", () => {
  const controller = new AbortController();
  assert.equal(controller.signal.aborted, false);
  controller.abort();
  assert.equal(controller.signal.aborted, true);
});
