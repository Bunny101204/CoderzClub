import test from "node:test";
import assert from "node:assert/strict";
import { characterFrequencyOracle } from "./character-frequency-oracle.mjs";

test("empty input yields empty output", () => {
  assert.equal(characterFrequencyOracle(""), "");
  assert.equal(characterFrequencyOracle("\n"), "");
});

test("orders by character code ascending (A before a)", () => {
  assert.equal(characterFrequencyOracle("Aa"), "A 1\na 1");
  assert.equal(characterFrequencyOracle("aA"), "A 1\na 1");
});

test("does not use locale-sensitive case folding order", () => {
  // localeCompare in many locales puts lowercase before uppercase; code-point does not.
  const got = characterFrequencyOracle("bBaA");
  assert.equal(got, "A 1\nB 1\na 1\nb 1");
  assert.notEqual(got, "a 1\nA 1\nb 1\nB 1");
});

test("preserves space characters as keys", () => {
  assert.equal(characterFrequencyOracle("  "), "  2");
  assert.equal(characterFrequencyOracle("a a"), "  1\na 2");
});

test("sample abca", () => {
  assert.equal(characterFrequencyOracle("abca"), "a 2\nb 1\nc 1");
});
