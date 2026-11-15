import test from "node:test";
import assert from "node:assert/strict";
import {
  institutionFixturesReady,
  institutionPrerequisiteMessage,
  liveInstitutionE2EEnabled,
  missingInstitutionFixtureKeys,
} from "./institutionEnv.js";

test("live institution workflow stays off unless INSTITUTION_E2E_ENABLED=true", () => {
  const previous = process.env.INSTITUTION_E2E_ENABLED;
  try {
    delete process.env.INSTITUTION_E2E_ENABLED;
    assert.equal(liveInstitutionE2EEnabled(), false);
    process.env.INSTITUTION_E2E_ENABLED = "false";
    assert.equal(liveInstitutionE2EEnabled(), false);
    process.env.INSTITUTION_E2E_ENABLED = "1";
    assert.equal(liveInstitutionE2EEnabled(), false);
    process.env.INSTITUTION_E2E_ENABLED = "true";
    assert.equal(liveInstitutionE2EEnabled(), true);
  } finally {
    if (previous == null) delete process.env.INSTITUTION_E2E_ENABLED;
    else process.env.INSTITUTION_E2E_ENABLED = previous;
  }
});

test("a seeded fixture file does not make the live suite ready without the opt-in", () => {
  const previous = process.env.INSTITUTION_E2E_ENABLED;
  const previousOwner = process.env.E2E_ORG_OWNER_USER;
  const previousPass = process.env.E2E_ORG_OWNER_PASS;
  const previousOrg = process.env.E2E_ORG_A_ID;
  try {
    delete process.env.INSTITUTION_E2E_ENABLED;
    process.env.E2E_ORG_OWNER_USER = "owner";
    process.env.E2E_ORG_OWNER_PASS = "secret";
    process.env.E2E_ORG_A_ID = "org-a";
    assert.equal(institutionFixturesReady(), false);
  } finally {
    if (previous == null) delete process.env.INSTITUTION_E2E_ENABLED;
    else process.env.INSTITUTION_E2E_ENABLED = previous;
    if (previousOwner == null) delete process.env.E2E_ORG_OWNER_USER;
    else process.env.E2E_ORG_OWNER_USER = previousOwner;
    if (previousPass == null) delete process.env.E2E_ORG_OWNER_PASS;
    else process.env.E2E_ORG_OWNER_PASS = previousPass;
    if (previousOrg == null) delete process.env.E2E_ORG_A_ID;
    else process.env.E2E_ORG_A_ID = previousOrg;
  }
});

test("prerequisite errors name the missing fixture keys and an unreachable backend", () => {
  assert.match(institutionPrerequisiteMessage({ enabled: false }), /INSTITUTION_E2E_ENABLED=true/);
  assert.match(
    institutionPrerequisiteMessage({ enabled: true, missingKeys: ["E2E_ORG_A_ID"] }),
    /E2E_ORG_A_ID/
  );
  assert.match(
    institutionPrerequisiteMessage({ enabled: true, unreachable: ["http://127.0.0.1:8080 (ECONNREFUSED)"] }),
    /ECONNREFUSED/
  );
  assert.equal(institutionPrerequisiteMessage({ enabled: true }), null);
  assert.deepEqual(missingInstitutionFixtureKeys({}), [
    "E2E_ORG_OWNER_USER",
    "E2E_ORG_OWNER_PASS",
    "E2E_ORG_A_ID",
    "E2E_ORG_B_ID",
    "E2E_ORG_B_OWNER_USER",
    "E2E_ORG_B_OWNER_PASS",
    "E2E_INSTRUCTOR_A_USER",
    "E2E_INSTRUCTOR_A_PASS",
    "E2E_INSTRUCTOR_A_ID",
    "E2E_STUDENT_A1_USER",
    "E2E_STUDENT_A1_PASS",
    "E2E_STUDENT_A2_USER",
    "E2E_STUDENT_A2_PASS",
    "E2E_STUDENT_B1_USER",
    "E2E_STUDENT_B1_PASS",
    "E2E_MULTI_USER",
    "E2E_MULTI_PASS",
    "E2E_PROBLEM_ORG_A_TITLE",
    "E2E_PROBLEM_ORG_A_ID",
  ]);
});
