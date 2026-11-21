import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const workflow = readFileSync(new URL("./gcp-load-test.ps1", import.meta.url), "utf8");
const targetGuard = readFileSync(new URL("../../perf/k6/staging-target-guard.js", import.meta.url), "utf8");
const admission = readFileSync(new URL("../../perf/k6/staging-admission.js", import.meta.url), "utf8");
const verdict = readFileSync(new URL("../../perf/k6/staging-verdict.js", import.meta.url), "utf8");

test("GCP workflow defaults to plan and protects existing VM names", () => {
  assert.match(workflow, /\$Action = "Plan"/);
  assert.match(workflow, /coderzclub-api-1/);
  assert.match(workflow, /coderzclub-loadgen-1/);
  assert.match(workflow, /-ApproveReadOnly/);
  assert.match(workflow, /-Approve explicitly/);
});

test("GCP workflow targets exactly the five owner-approved VMs", () => {
  for (const name of ["coderzclub-api-1", "coderzclub-worker-1", "coderzclub-infra-1", "coderzclub-judge0-1", "coderzclub-loadgen-1"]) {
    assert.match(workflow, new RegExp(name));
  }
  assert.match(workflow, /Provision is disabled/);
  assert.match(workflow, /ConfirmExclusiveTestWindow/);
  assert.match(workflow, /ConfirmNoLiveUsers/);
  assert.match(workflow, /ConfirmIsolationVerified/);
  assert.match(workflow, /ConfirmSecureJudge0/);
});

test("existing API and worker remain separate roles", () => {
  assert.match(workflow, /Role = "api"; Name = "coderzclub-api-1"/);
  assert.match(workflow, /Role = "worker"; Name = "coderzclub-worker-1"/);
  assert.match(workflow, /API and worker remain separate existing VMs/);
});

test("remote k6 stages require explicit approvals and bound work", () => {
  assert.match(targetGuard, /STAGING_LOADTEST_APPROVED/);
  assert.match(admission, /__ITER > 0/);
  assert.match(admission, /status === 202/);
  assert.match(verdict, /REAL_VERDICT_TEST_APPROVED/);
  assert.match(verdict, /status === "COMPLETED"/);
  assert.match(verdict, /attempt < 120/);
});
