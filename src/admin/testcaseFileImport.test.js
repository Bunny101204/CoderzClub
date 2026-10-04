import test from "node:test";
import assert from "node:assert/strict";
import {
  applyImportedCases,
  CASE_DELIMITER,
  parsePairedTestcaseFiles,
  splitExactCaseDelimiter
} from "./testcaseFileImport.js";

test("splits only on an exact ===CASE=== line", () => {
  const text = `1 2\n===CASE===\n3 4\nnot ===CASE=== inside`;
  assert.deepEqual(splitExactCaseDelimiter(text), ["1 2", "3 4\nnot ===CASE=== inside"]);
  assert.equal(CASE_DELIMITER, "===CASE===");
});

test("rejects mismatched pair counts", () => {
  const result = parsePairedTestcaseFiles("a\n===CASE===\nb", "only-one");
  assert.equal(result.ok, false);
  assert.match(result.error, /Counts must match/);
});

test("parses a matching public pair", () => {
  const result = parsePairedTestcaseFiles("1 2\n===CASE===\n3", "a\n===CASE===\nb");
  assert.equal(result.ok, true);
  assert.equal(result.cases.length, 2);
  assert.equal(result.preview.length, 2);
});

test("append keeps existing rows and replace swaps them", () => {
  const existing = [{ input: "old", output: "1", explanation: "" }];
  const incoming = [{ input: "new", output: "2" }];
  assert.equal(applyImportedCases(existing, incoming, "append").length, 2);
  assert.deepEqual(applyImportedCases(existing, incoming, "replace")[0].input, "new");
});
