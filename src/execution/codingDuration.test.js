import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import assert from "node:assert/strict";
import {
  codingDurationSecondsFromElapsedMs,
  formatCodingDuration,
  sanitizeCodingDurationSeconds
} from "./codingDuration.js";

test("formats seconds without raw milliseconds", () => {
  assert.equal(formatCodingDuration(42), "42s");
  assert.equal(formatCodingDuration(186), "3m 06s");
  assert.equal(formatCodingDuration(3840), "1h 04m");
  assert.equal(formatCodingDuration(null), "—");
  assert.equal(formatCodingDuration(undefined), "—");
});

test("submit payload uses whole seconds from the editor timer", () => {
  assert.equal(codingDurationSecondsFromElapsedMs(6500), 6);
  assert.equal(codingDurationSecondsFromElapsedMs(0), 0);
  assert.equal(codingDurationSecondsFromElapsedMs(-1), null);
  assert.equal(sanitizeCodingDurationSeconds(Number.NaN), null);
  assert.equal(sanitizeCodingDurationSeconds(999999999), null);
  const editor = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "../Components/Judge0CodeEditor.jsx"), "utf8");
  assert.match(editor, /codingDurationSeconds: codingDurationSecondsFromElapsedMs\(elapsedTime\)/);
  assert.doesNotMatch(editor, /codingDurationSecondsFromElapsedMs\(elapsedTime\).*handleRunAll/);
});
