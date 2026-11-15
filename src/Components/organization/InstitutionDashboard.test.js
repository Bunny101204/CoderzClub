import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(here, "InstitutionDashboard.jsx"), "utf8");

test("institution dashboard covers admin workflow controls", () => {
  assert.match(source, /institution-enroll-form/);
  assert.match(source, /institution-remove-student-form/);
  assert.match(source, /institution-coordinator-form/);
  assert.match(source, /Enroll student/);
  assert.match(source, /Remove student/);
  assert.match(source, /Grant coordinator/);
  assert.match(source, /View report/);
  assert.match(source, /Export CSV/);
  assert.match(source, /Schedule contest/);
  assert.match(source, /Contest for batch/);
  assert.match(source, /institution\/contests\/new\?batchId=/);
  assert.match(source, /adminBatches\.addMembers/);
  assert.match(source, /adminBatches\.importMembers/);
  assert.match(source, /adminBatches\.removeMembers/);
  assert.match(source, /adminBatches\.grantCoordinator/);
  assert.match(source, /adminBatches\.report/);
  assert.match(source, /adminBatches\.reportCsv/);
  assert.match(source, /updateMemberRoles/);
  assert.match(source, /organizations\.removeMember/);
  assert.match(source, /organizations\.previewBranding/);
  assert.match(source, /organizations\.resetBranding/);
  assert.match(source, /landingTagline/);
  assert.match(source, /emailFromName/);
  assert.match(source, /cacheEpoch/);
});
