import test from "node:test";
import assert from "node:assert/strict";
import { shouldRetryTransientFailure } from "./apiRetry.js";

test("a failed POST is not retried", () => {
  assert.equal(shouldRetryTransientFailure({
    method: "post",
    status: 500,
    alreadyRetried: false,
  }), false);
  assert.equal(shouldRetryTransientFailure({
    method: "POST",
    code: "ERR_NETWORK",
    message: "Network Error",
    alreadyRetried: false,
  }), false);
});

test("admin mutations and revision writes are not retried", () => {
  for (const method of ["PUT", "PATCH", "DELETE"]) {
    assert.equal(shouldRetryTransientFailure({
      method,
      status: 503,
      alreadyRetried: false,
    }), false);
  }
});

test("idempotent reads retry once on a transient server or network failure", () => {
  assert.equal(shouldRetryTransientFailure({
    method: "GET",
    status: 500,
    alreadyRetried: false,
  }), true);
  assert.equal(shouldRetryTransientFailure({
    method: "HEAD",
    code: "ECONNABORTED",
    alreadyRetried: false,
  }), true);
  assert.equal(shouldRetryTransientFailure({
    method: "OPTIONS",
    message: "Network Error",
    alreadyRetried: false,
  }), true);
  assert.equal(shouldRetryTransientFailure({
    method: "GET",
    status: 500,
    alreadyRetried: true,
  }), false);
  assert.equal(shouldRetryTransientFailure({
    method: "GET",
    status: 404,
    alreadyRetried: false,
  }), false);
});
