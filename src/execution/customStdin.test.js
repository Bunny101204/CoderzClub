import assert from "node:assert/strict";
import test from "node:test";
import { customStdinForExecution, executionStdin } from "./customStdin.js";

test("multiline stdin keeps newlines and spaces", () => {
  const pasted = "5 2\n10 20 30 40 50";
  assert.equal(customStdinForExecution(pasted), pasted);
});

test("a trailing newline typed by the user is preserved", () => {
  assert.equal(customStdinForExecution("5 2\n10 20 30 40 50\n"), "5 2\n10 20 30 40 50\n");
});

test("windows newlines become line feeds without trimming spaces", () => {
  assert.equal(customStdinForExecution("  5 2\r\n10 20 \r\n"), "  5 2\n10 20 \n");
});

test("empty and non-string input stay empty", () => {
  assert.equal(customStdinForExecution(""), "");
  assert.equal(customStdinForExecution(null), "");
});

test("Java and Python custom stdin reach the adapter without an added newline", () => {
  const pasted = "5 2\r\n10 20 30 40 50";
  const stdin = executionStdin({ preserveExactly: true, value: pasted });
  assert.equal(stdin, "5 2\n10 20 30 40 50");
  assert.equal(stdin.includes("\r"), false);
});
