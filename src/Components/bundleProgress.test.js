import test from "node:test";
import assert from "node:assert/strict";
import {
  buildProblemIdAliases,
  buildProblemStatus,
  getBundleProgress
} from "./bundleProgress.js";

test("classifies numeric submission IDs against Mongo bundle IDs", () => {
  const aliases = buildProblemIdAliases([
    { id: "mongo-6", numericId: 6 },
    { id: "mongo-7", numericId: 7 }
  ]);
  const status = buildProblemStatus([
    { problemId: 6, result: "WRONG_ANSWER" },
    { problemId: 6, result: "" , verdict: " ACCEPTED " },
    { problemId: 7, status: "WRONG_ANSWER" }
  ], aliases);

  assert.deepEqual(status, { "mongo-6": "SOLVED", "mongo-7": "ATTEMPTED" });
  assert.deepEqual(
    getBundleProgress({ problemIds: ["mongo-6", "mongo-7"] }, status, aliases),
    { total: 2, solved: 1, attempted: 2, state: "incomplete" }
  );
});

test("requires every problem to be solved for completed", () => {
  const aliases = buildProblemIdAliases([{ id: "mongo-1", numericId: 1 }]);
  const status = buildProblemStatus([{ problemId: 1, result: "COMPLETED" }], aliases);
  assert.equal(getBundleProgress({ problemIds: ["mongo-1"] }, status, aliases).state, "completed");
});

test("classifies bundles with no matching submissions as unattempted", () => {
  assert.equal(getBundleProgress({ problemIds: ["p1", "p2"] }, {}).state, "unattempted");
});
