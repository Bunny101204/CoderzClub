import test from "node:test";
import assert from "node:assert/strict";
import {
  EXECUTION_MODES,
  selectableExecutionModes,
  isBatchedExecutionMode,
  helpForExecutionMode,
  languageSupportsMode,
} from "./executionModes.js";

test("admin cannot select BATCH_STDIN_PROGRAM", () => {
  assert.deepEqual(selectableExecutionModes().map((mode) => mode.id), [
    "STANDARD_PER_CASE",
    "FUNCTION_HARNESS_BATCH",
  ]);
  const batch = EXECUTION_MODES.find((mode) => mode.id === "BATCH_STDIN_PROGRAM");
  assert.equal(batch.selectable, false);
  assert.match(batch.label, /not currently supported/i);
});

test("only function harness is treated as a live batched mode", () => {
  assert.equal(isBatchedExecutionMode("BATCH_STDIN_PROGRAM"), false);
  assert.equal(isBatchedExecutionMode("FUNCTION_HARNESS_BATCH"), true);
  assert.equal(isBatchedExecutionMode("STANDARD_PER_CASE"), false);
});

test("help text explains BATCH_STDIN is reserved", () => {
  assert.match(helpForExecutionMode("BATCH_STDIN_PROGRAM"), /not implemented/i);
  assert.match(helpForExecutionMode("FUNCTION_HARNESS_BATCH"), /rejected/i);
});

test("language capability helper never advertises batch stdin", () => {
  const python = { id: 71, batchStdinProgram: true, functionHarnessBatch: true, standardPerCase: true };
  const go = { id: 60, batchStdinProgram: false, functionHarnessBatch: false, standardPerCase: true };
  assert.equal(languageSupportsMode(python, "BATCH_STDIN_PROGRAM"), false);
  assert.equal(languageSupportsMode(go, "BATCH_STDIN_PROGRAM"), false);
  assert.equal(languageSupportsMode(go, "STANDARD_PER_CASE"), true);
  assert.equal(languageSupportsMode(python, "FUNCTION_HARNESS_BATCH"), true);
});
