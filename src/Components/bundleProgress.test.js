import test from "node:test";
import assert from "node:assert/strict";
import {
  buildProblemIdAliases,
  buildProblemStatus,
  getBundleProgress,
  indexFromProgressEntries,
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

  assert.deepEqual(status, { "mongo-6": "SOLVED" });
  assert.deepEqual(
    getBundleProgress({ problemIds: ["mongo-6", "mongo-7"] }, { "mongo-6": "SOLVED", "mongo-7": "ATTEMPTED" }, aliases),
    { total: 2, solved: 1, attempted: 2, state: "incomplete" }
  );
});

test("job COMPLETED is not treated as solved", () => {
  const aliases = buildProblemIdAliases([{ id: "mongo-1", numericId: 1 }]);
  const status = buildProblemStatus([{ problemId: 1, result: "COMPLETED" }], aliases);
  assert.deepEqual(status, {});
  assert.equal(getBundleProgress({ problemIds: ["mongo-1"] }, status, aliases).state, "unattempted");
});

test("progress entries share aliases between bundles and problems", () => {
  const index = indexFromProgressEntries([
    { problemId: "mongo-1", numericId: 1, status: "SOLVED", aliases: ["mongo-1", "1"] }
  ]);
  assert.equal(getBundleProgress({ problemIds: ["1"] }, index, { "1": "mongo-1" }).solved, 1);
});

test("classifies bundles with no matching submissions as unattempted", () => {
  assert.equal(getBundleProgress({ problemIds: ["p1", "p2"] }, {}).state, "unattempted");
});

test("INTERNAL_ERROR is not solved or attempted", () => {
  const aliases = buildProblemIdAliases([{ id: "mongo-1", numericId: 1 }]);
  const status = buildProblemStatus([{ problemId: "mongo-1", result: "INTERNAL_ERROR" }], aliases);
  assert.deepEqual(status, {});
});
