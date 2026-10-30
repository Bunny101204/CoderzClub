import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { legacyProblemTarget } from "./legacyProblem.js";

test("legacy problem urls keep the problem id", () => {
  assert.equal(legacyProblemTarget("1"), "/problem/1");
  assert.equal(legacyProblemTarget("abc def"), "/problem/abc%20def");
});

test("a missing legacy id does not invent a problem", () => {
  assert.equal(legacyProblemTarget(""), "/home");
  assert.equal(legacyProblemTarget("a/b"), "/home");
});

test("the old route redirects instead of mounting the crashing page", () => {
  const app = readFileSync(new URL("../App.jsx", import.meta.url), "utf8");
  assert.match(app, /path="\/problem-old\/:id"/);
  assert.match(app, /LegacyProblemRedirect/);
  assert.doesNotMatch(app, /path="\/problem-old\/:id"[\s\S]{0,180}ProblemPage \/>/);
});
