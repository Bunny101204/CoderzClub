import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { getContestRunPollDelay, isTerminalContestRun } from "./contestRunPolling.js";

const root = dirname(fileURLToPath(import.meta.url));

test("contest run uses async contest endpoint and restrained backoff", () => {
  const editor = readFileSync(join(root, "../Components/Judge0CodeEditor.jsx"), "utf8");
  const page = readFileSync(join(root, "../Components/contests/ContestProblemPage.jsx"), "utf8");
  const client = readFileSync(join(root, "../apiClient.js"), "utf8");

  assert.match(client, /run: \(contestId, problemId, payload\)/);
  assert.match(client, /getRun: \(runId\)/);
  assert.match(client, /\/api\/contests\/\$\{contestId\}\/problems\/\$\{problemId\}\/runs/);
  assert.match(client, /\/api\/contest-runs\/\$\{runId\}/);
  assert.match(editor, /api\.contests\.run\(contestId, problemId/);
  assert.match(editor, /getContestRunPollDelay/);
  assert.match(editor, /contestMode \? !contestLive : executionLocked/);
  assert.match(editor, /customRunLocked/);
  assert.doesNotMatch(editor, /contestId[\s\S]{0,200}\/api\/problems\/\$\{problemId\}\/run-public/);
  assert.doesNotMatch(editor, /contestMode[\s\S]{0,80}\/api\/judge0\/execute/);
  assert.doesNotMatch(page, /executionLocked/);
  assert.match(page, /contestId=\{contest\?\.id \|\| contestId\}/);
});

test("contest run poll delay starts at 1s then 1.5s then caps at 2s", () => {
  assert.equal(getContestRunPollDelay(0), 1000);
  assert.equal(getContestRunPollDelay(1), 1500);
  assert.equal(getContestRunPollDelay(2), 2000);
  assert.equal(getContestRunPollDelay(20), 2000);
  assert.equal(isTerminalContestRun("COMPLETED"), true);
  assert.equal(isTerminalContestRun("FAILED"), true);
  assert.equal(isTerminalContestRun("QUEUED"), false);
});
