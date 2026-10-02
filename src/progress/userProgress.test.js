import test from "node:test";
import assert from "node:assert/strict";
import { indexProgress, statusForProblem } from "./userProgress.js";

test("indexes mongo and numeric aliases so ACCEPTED wins", () => {
  const index = indexProgress([
    { problemId: "mongo-1", numericId: 6, status: "ATTEMPTED", aliases: ["mongo-1", "6"] },
    { problemId: "mongo-1", numericId: 6, status: "SOLVED", aliases: ["mongo-1", "6"] },
  ]);
  assert.equal(statusForProblem({ id: "mongo-1", numericId: 6 }, index), "SOLVED");
  assert.equal(statusForProblem({ id: "other" }, index), null);
});

test("unsolved problems stay null rather than attempted", () => {
  const index = indexProgress([{ problemId: "p2", status: "ATTEMPTED" }]);
  assert.equal(statusForProblem({ id: "p1" }, index), null);
});
