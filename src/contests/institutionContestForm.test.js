import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const form = readFileSync(join(here, "../Components/admin/contests/ContestForm.jsx"), "utf8");
const app = readFileSync(join(here, "../App.jsx"), "utf8");

test("contest form supports institution batch-scoped creation", () => {
  assert.match(form, /institutionMode/);
  assert.match(form, /batchIdFromQuery/);
  assert.match(form, /Schedule institution contest/);
  assert.match(form, /\/institution\/contests\/\$\{created\.data\.id\}/);
  assert.match(app, /path="\/institution\/contests\/:id"/);
});
