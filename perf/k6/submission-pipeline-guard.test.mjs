import test from "node:test";
import assert from "node:assert/strict";
import { assertLoopbackTarget, DEFAULT_BASE_URL } from "./submission-pipeline-guard.js";

test("defaults to the guarded loopback target", () => {
  assert.equal(assertLoopbackTarget(), DEFAULT_BASE_URL);
});

test("accepts loopback targets and strips a trailing slash", () => {
  assert.equal(assertLoopbackTarget("http://127.0.0.1:8082/"), DEFAULT_BASE_URL);
  assert.equal(assertLoopbackTarget("http://localhost:8082"), "http://localhost:8082");
});

test("rejects remote, non-http, and credential-bearing targets", () => {
  for (const target of [
    "https://127.0.0.1:8082",
    "http://10.0.0.5:8082",
    "http://example.test:8082",
    "http://user:pass@127.0.0.1:8082",
  ]) {
    assert.throws(() => assertLoopbackTarget(target), /Refusing non-loopback|absolute loopback/);
  }
});
