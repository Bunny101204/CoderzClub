import test from "node:test";
import assert from "node:assert/strict";
import { assertApplyAllowed, classifyApiBase, difficultyCaseMinimums } from "./gates.mjs";

test("classifies local staging as non-production", () => {
  const local = classifyApiBase("http://127.0.0.1:8080");
  assert.equal(local.ok, true);
  assert.equal(local.production, false);
});

test("classifies production API as production", () => {
  const prod = classifyApiBase("https://api.coderzclub.site");
  assert.equal(prod.ok, true);
  assert.equal(prod.production, true);
});

test("apply refuses production without explicit allow", () => {
  const blocked = assertApplyAllowed({
    apiBase: "https://api.coderzclub.site",
    apply: true,
    allowProduction: false,
  });
  assert.equal(blocked.allowed, false);
});

test("apply allows production only with explicit flag", () => {
  const allowed = assertApplyAllowed({
    apiBase: "https://api.coderzclub.site",
    apply: true,
    allowProduction: true,
  });
  assert.equal(allowed.allowed, true);
  assert.equal(allowed.production, true);
});

test("dry-run is always allowed without api-base", () => {
  const dry = assertApplyAllowed({ apply: false });
  assert.equal(dry.allowed, true);
  assert.equal(dry.mode, "dry-run");
});

test("difficulty minima match curriculum", () => {
  assert.deepEqual(difficultyCaseMinimums("EASY"), { public: 2, hidden: 6 });
  assert.deepEqual(difficultyCaseMinimums("MEDIUM"), { public: 2, hidden: 12 });
  assert.deepEqual(difficultyCaseMinimums("HARD"), { public: 3, hidden: 20 });
});
