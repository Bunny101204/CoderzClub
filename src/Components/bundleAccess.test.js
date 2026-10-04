import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const panel = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "BundleAccessPanel.jsx"),
  "utf8"
);

test("bundle access UI uses bounded admin searches", () => {
  assert.match(panel, /\/api\/admin\/batches\/users\?/);
  assert.match(panel, /\/api\/admin\/batches\?/);
  assert.match(panel, /size: "10"/);
  assert.doesNotMatch(panel, /\/api\/admin\/users"/);
});

test("restricted grants are managed through admin APIs", () => {
  assert.match(panel, /\/api\/admin\/bundles\/\$\{bundleId\}\/grants/);
  assert.match(panel, /\/visibility/);
  assert.match(panel, /Public/);
  assert.match(panel, /Restricted/);
});
