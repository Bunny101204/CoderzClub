import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const text = readFileSync(new URL("./prepare-staging-artifact.ps1", import.meta.url), "utf8");
test("artifact preparation is dry-run by default and has no registry/GitHub push", () => {
  assert.match(text, /\[switch\]\$Apply/);
  assert.match(text, /-Approve explicitly/);
  assert.match(text, /coderzclub-worker-1/);
  assert.match(text, /\[ValidateSet\("coderzclub-api-1", "coderzclub-worker-1"\)\]/);
  assert.match(text, /Invoke-Gcloud @\([\s\S]*?"compute",\s*"scp"/);
  assert.match(text, /coderzclub-backend-\$hash\.jar/);
  assert.match(text, /Remote SHA-256 mismatch/);
  assert.match(text, /mvn test package/);
  assert.doesNotMatch(text, /(^|\n)\s*(docker\s+push|git\s+push)\b/i);
  assert.match(text, /never copies env files|secrets/i);
});
