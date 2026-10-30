import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));

test("contest submit uses the explicit contest endpoint and does not fall back to practice submit", () => {
  const editor = readFileSync(join(root, "../Components/Judge0CodeEditor.jsx"), "utf8");
  const page = readFileSync(join(root, "../Components/contests/ContestProblemPage.jsx"), "utf8");
  const workspace = readFileSync(join(root, "../Components/ProblemPageNew.jsx"), "utf8");
  const client = readFileSync(join(root, "../apiClient.js"), "utf8");

  assert.match(client, /submit: \(contestId, problemId, payload, operationId\)/);
  assert.match(client, /getProblem: \(contestId, problemId\)/);
  assert.match(client, /\/api\/contests\/\$\{contestId\}\/problems\/\$\{problemId\}/);
  assert.match(client, /\/api\/contests\/\$\{contestId\}\/problems\/\$\{problemId\}\/submission-jobs/);
  assert.match(editor, /api\.contests\.submit\(contestId, problemId, executionPayload, claimed\.operationId\)/);
  assert.match(editor, /contestStatus === "LIVE"/);
  assert.match(editor, /disabled=\{isLoading \|\| submitLocked\}/);
  assert.match(page, /contestId=\{contest\?\.id \|\| contestId\}/);
  assert.match(page, /contestStatus=\{contest\?\.status\}/);
  assert.match(page, /api\.contests\.getProblem\(contestId, problemId\)/);
  assert.doesNotMatch(page, /api\.problems\.getById/);
  assert.match(workspace, /contestId=\{contestId\}/);
  assert.match(editor, /contestId\s*\?\s*await api\.contests\.submit/);
  assert.match(editor, /api\.submissionJobs\.create\(\{ problemId, \.\.\.executionPayload \}, claimed\.operationId\)/);
  assert.doesNotMatch(editor, /catch[\s\S]{0,500}api\.contests\.submit[\s\S]{0,200}\/api\/submission-jobs/);
});
