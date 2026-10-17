import test from "node:test";
import assert from "node:assert/strict";
import { formatJoined, isoDayBound, studentHitStatus, studentSearchMessage } from "./batchStudentSearch.js";

test("a failed student search is not described as an empty result", () => {
  assert.equal(studentSearchMessage("error"), "Student search failed. Nothing was added.");
  assert.equal(studentSearchMessage("empty"), "No matching registered users.");
  assert.notEqual(studentSearchMessage("error"), studentSearchMessage("empty"));
});

test("registered users are marked eligible or already in the batch", () => {
  assert.equal(studentHitStatus({ id: "u1", alreadyMember: false }), "Eligible to add");
  assert.equal(studentHitStatus({ id: "u2", alreadyMember: true }), "Already in this batch");
});

test("joined dates and report day bounds stay explicit", () => {
  assert.equal(formatJoined(null), "—");
  assert.equal(formatJoined("not-a-date"), "—");
  assert.equal(isoDayBound("2026-10-16", false), "2026-10-16T00:00:00.000Z");
  assert.equal(isoDayBound("2026-10-16", true), "2026-10-16T23:59:59.999Z");
  assert.equal(isoDayBound("", false), "");
});
