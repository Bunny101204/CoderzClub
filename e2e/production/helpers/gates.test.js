import test from "node:test";
import assert from "node:assert/strict";
import {
  INITIAL_STUDENT_CONCURRENCY,
  assertProductionOrigins,
  productionWriteGate,
  selectedStudents,
} from "./gates.js";

test("production write gate fails closed", () => {
  assert.equal(productionWriteGate({}).allowed, false);
  assert.equal(productionWriteGate({
    PRODUCTION_E2E_MODE: "read",
    PRODUCTION_E2E_ENABLED: "true",
    PRODUCTION_E2E_CONFIRM: "coderzclub.site",
    PRODUCTION_E2E_RUN_ID: "write-2026-11-16",
  }).allowed, false);
  assert.equal(productionWriteGate({
    PRODUCTION_E2E_MODE: "write",
    PRODUCTION_E2E_ENABLED: "true",
    PRODUCTION_E2E_CONFIRM: "coderzclub.site",
    PRODUCTION_E2E_RUN_ID: "write-2026-11-16",
  }).allowed, true);
});

test("origins are pinned to production hosts", () => {
  assert.doesNotThrow(() =>
    assertProductionOrigins("https://coderzclub.site", "https://api.coderzclub.site")
  );
  assert.throws(() =>
    assertProductionOrigins("https://preview.example", "https://api.coderzclub.site")
  );
});

test("initial student concurrency cannot exceed five", () => {
  const students = Array.from({ length: 10 }, (_, index) => ({ username: `student-${index}` }));
  assert.equal(selectedStudents(students).length, INITIAL_STUDENT_CONCURRENCY);
  assert.throws(() => selectedStudents(students, 6));
});
