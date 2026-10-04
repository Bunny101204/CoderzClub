import test from "node:test";
import assert from "node:assert/strict";
import { formatSupportReference, studentVerdictCopy } from "./studentVerdict.js";

test("hidden wrong answer does not request input or expected output", () => {
  const copy = studentVerdictCopy({
    verdict: "WRONG_ANSWER",
    hidden: true,
    passedCount: 2,
    totalCount: 3
  });
  assert.equal(copy.showHiddenIo, false);
  assert.equal(copy.summary, "Wrong answer on a hidden test.");
  assert.equal(copy.detail, "Passed 2 of 3 test cases.");
});

test("internal error never uses a null explanation", () => {
  const copy = studentVerdictCopy({
    verdict: "INTERNAL_ERROR",
    errorMessage: "Judge0 provider error: null"
  });
  assert.equal(copy.code, "JUDGE0_PROVIDER_ERROR");
  assert.match(copy.detail, /temporarily unavailable/);
  assert.doesNotMatch(copy.detail, /null/);
  assert.match(copy.summary, /not a wrong answer/);
});

test("support reference is a short existing id prefix", () => {
  assert.equal(formatSupportReference("6ac247abcdef"), "6ac247ab…");
});
