import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";
import {
  formatSubmissionMemory,
  formatSubmissionRuntime,
  formatSubmissionTimestamp,
} from "./submissionMetrics.js";

test("runtime stored in milliseconds formats sub-second, seconds, and zero", () => {
  assert.equal(formatSubmissionRuntime(4), "4 ms");
  assert.equal(formatSubmissionRuntime(82), "82 ms");
  assert.equal(formatSubmissionRuntime(950), "950 ms");
  assert.equal(formatSubmissionRuntime(1234), "1.23 s");
  assert.equal(formatSubmissionRuntime(0), "0 ms");
});

test("missing or invalid runtime stays an em dash", () => {
  assert.equal(formatSubmissionRuntime(null), "—");
  assert.equal(formatSubmissionRuntime(undefined), "—");
  assert.equal(formatSubmissionRuntime(Number.NaN), "—");
  assert.equal(formatSubmissionRuntime(-1), "—");
});

test("memory stored in bytes uses IEC binary units", () => {
  assert.equal(formatSubmissionMemory(512), "512 B");
  assert.equal(formatSubmissionMemory(1536), "1.5 KiB");
  assert.equal(formatSubmissionMemory(512 * 1024), "512 KiB");
  assert.equal(formatSubmissionMemory(15309209), "14.6 MiB");
  assert.equal(formatSubmissionMemory(1024 * 1024 * 1024), "1 GiB");
  assert.equal(formatSubmissionMemory(0), "0 B");
});

test("missing or invalid memory stays an em dash", () => {
  assert.equal(formatSubmissionMemory(null), "—");
  assert.equal(formatSubmissionMemory(undefined), "—");
  assert.equal(formatSubmissionMemory(Number.NaN), "—");
  assert.equal(formatSubmissionMemory(-1), "—");
});

test("submitted timestamp uses the existing local-time formatting", () => {
  const createdAt = "2026-10-12T10:40:00.000Z";
  assert.equal(formatSubmissionTimestamp(createdAt), new Date(createdAt).toLocaleString());
  assert.equal(formatSubmissionTimestamp(null), "—");
});

test("history row labels runtime, memory, submitted, and coding time separately", () => {
  const metrics = readFileSync(
    join(dirname(fileURLToPath(import.meta.url)), "submissionMetrics.js"),
    "utf8"
  );
  const history = readFileSync(
    join(dirname(fileURLToPath(import.meta.url)), "../Components/ProblemSubmissionHistory.jsx"),
    "utf8"
  );
  assert.match(metrics, /Persisted Submission\.runtime: judged code-execution time in milliseconds/);
  assert.match(history, /Runtime: \{formatSubmissionRuntime\(submission\.runtime\)\}/);
  assert.match(history, /Memory: \{formatSubmissionMemory\(submission\.memory\)\}/);
  assert.match(history, /Submitted: \{formatSubmissionTimestamp\(submission\.createdAt\)\}/);
  assert.match(history, /Coding time: \{formatCodingDuration\(submission\.codingDurationSeconds\)\}/);
  assert.doesNotMatch(history, /completedAt\s*-\s*createdAt/);
  assert.doesNotMatch(history, /total submission time|queue time|wall-clock/i);
  assert.doesNotMatch(metrics, /total submission time|queue time|wall-clock/i);
});
