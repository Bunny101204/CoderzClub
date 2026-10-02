import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  SEARCH_DEBOUNCE_MS,
  MISSING_NUMERIC_ID_LABEL,
  displayProblemId,
  internalProblemId,
  editProblemPath,
  nextPageForFilterChange,
  createRequestSequence,
  debounce
} from "./adminProblems.js";

const adminDashboardSource = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "../Components/AdminDashboard.jsx"),
  "utf8"
);

test("legacy string _id with numericId 23 displays 23", () => {
  const problem = { id: "23", numericId: 23, title: "Legacy" };
  assert.equal(displayProblemId(problem), "23");
  assert.equal(internalProblemId(problem), "23");
  assert.equal(editProblemPath(problem), "/admin/edit-problem/23");
});

test("ObjectId-backed problem displays numericId 26 rather than Mongo id", () => {
  const problem = {
    id: "6aa407db2f4d12188ea742e2",
    numericId: 26,
    title: "Newer"
  };
  assert.equal(displayProblemId(problem), "26");
  assert.notEqual(displayProblemId(problem), problem.id);
  assert.equal(internalProblemId(problem), "6aa407db2f4d12188ea742e2");
  assert.equal(editProblemPath(problem), "/admin/edit-problem/6aa407db2f4d12188ea742e2");
});

test("missing numericId uses a fallback label and keeps Mongo identity for actions", () => {
  const problem = { id: "6aace03dfa4bb1754fdc3396", title: "Broken" };
  assert.equal(displayProblemId(problem), MISSING_NUMERIC_ID_LABEL);
  assert.equal(internalProblemId(problem), "6aace03dfa4bb1754fdc3396");
});

test("numericId 0 is displayed rather than treated as missing", () => {
  const problem = { id: "mongo-0", numericId: 0 };
  assert.equal(displayProblemId(problem), "0");
  assert.equal(internalProblemId(problem), "mongo-0");
});

test("changing topic or items resets pagination to page 1", () => {
  assert.equal(nextPageForFilterChange(), 1);
});

test("stale list responses do not replace a newer search", () => {
  const seq = createRequestSequence();
  const requestA = seq.next();
  const requestB = seq.next();
  assert.equal(seq.isCurrent(requestA), false);
  assert.equal(seq.isCurrent(requestB), true);
});

test("search debounce waits about 400ms and does not fire on every keystroke", async () => {
  assert.equal(SEARCH_DEBOUNCE_MS, 400);
  const calls = [];
  const run = debounce((value) => calls.push(value), 40);
  run("a");
  run("ar");
  run("array");
  assert.equal(calls.length, 0);
  await new Promise((resolve) => setTimeout(resolve, 60));
  assert.deepEqual(calls, ["array"]);
  run.cancel();
});

test("AdminDashboard keeps the search input mounted during load and refresh", () => {
  assert.equal(adminDashboardSource.includes("if (loading) {"), false);
  assert.match(adminDashboardSource, /value=\{problemSearch\}/);
  assert.match(adminDashboardSource, /setDebouncedProblemSearch\(problemSearch\), SEARCH_DEBOUNCE_MS\)/);
  assert.match(adminDashboardSource, /displayProblemId\(problem\)/);
  assert.match(adminDashboardSource, /internalProblemId\(problem\)/);
  assert.match(adminDashboardSource, /handleDeleteProblem\(internalProblemId\(problem\)\)/);
  assert.doesNotMatch(adminDashboardSource, />\{problem\.id\}</);
  assert.match(adminDashboardSource, /clearInterval\(interval\)/);
  assert.match(adminDashboardSource, /if \(seq !== problemFetchSeq\.current\) \{\s*return;/);
  assert.match(adminDashboardSource, /if \(seq === problemFetchSeq\.current\) \{\s*setLoading\(false\);/);
});
