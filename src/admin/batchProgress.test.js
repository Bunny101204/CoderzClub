import test from "node:test";
import assert from "node:assert/strict";
import { batchStatusSymbol, batchStatusLabel, displayAssignedProblemId } from "./batchProgress.js";
import { statusForProblem, indexProgress } from "../progress/userProgress.js";

test("batch symbols match the existing progress vocabulary", () => {
  assert.equal(batchStatusSymbol("SOLVED"), "check");
  assert.equal(batchStatusSymbol("ATTEMPTED"), "dot");
  assert.equal(batchStatusSymbol("UNSOLVED"), "");
  assert.equal(batchStatusLabel("SOLVED"), "Solved");
});

test("batch report SOLVED agrees with profile progress for the same problem", () => {
  const index = indexProgress([
    { problemId: "mongo-26", numericId: 26, status: "SOLVED", aliases: ["mongo-26", "26"] }
  ]);
  assert.equal(statusForProblem({ id: "mongo-26", numericId: 26 }, index), "SOLVED");
  assert.equal(batchStatusSymbol("SOLVED"), "check");
});

test("assigned problems display numericId rather than Mongo id", () => {
  assert.equal(displayAssignedProblemId({ id: "6aa407db2f4d12188ea742e2", numericId: 26 }), "26");
  assert.equal(displayAssignedProblemId({ id: "6aa407db2f4d12188ea742e2" }), "—");
});
