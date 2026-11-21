import test from "node:test";
import assert from "node:assert/strict";
import { secretValues, validateCredentials } from "./credentials.js";

function account(name) {
  return { username: name, password: `password-${name}` };
}

function validConfig() {
  return {
    platformAdmin: account("admin"),
    organizationOwner: account("owner"),
    coordinator: account("coordinator"),
    students: Array.from({ length: 5 }, (_, index) => account(`student-${index + 1}`)),
    unenrolledStudent: account("unenrolled"),
    collegeBOutsider: { ...account("outsider"), organizationId: "college-b-id" },
    problem: {
      id: "problem-id",
      title: "Synthetic test problem",
      language: "python",
      languageId: 71,
      correctCode: "print(1)",
      incorrectCode: "print(0)",
    },
  };
}

test("credentials require independent accounts and five to ten students", () => {
  assert.equal(validateCredentials(validConfig()).students.length, 5);
  const duplicate = validConfig();
  duplicate.students[0].username = duplicate.organizationOwner.username.toUpperCase();
  assert.throws(() => validateCredentials(duplicate), /independently authenticated/);
  const tooFew = validConfig();
  tooFew.students = tooFew.students.slice(0, 4);
  assert.throws(() => validateCredentials(tooFew), /between 5 and 10/);
});

test("secret collection includes passwords without account identifiers", () => {
  const secrets = secretValues(validConfig());
  assert.equal(secrets.length, 10);
  assert.equal(secrets.includes("owner"), false);
});
