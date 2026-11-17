import test from "node:test";
import assert from "node:assert/strict";

/**
 * Ordering contract for Character Frequency (character-code ascending).
 * Kept inside the test module so a full reference solution is not published
 * as a reusable public oracle module.
 */
function characterFrequencyOrdering(input) {
  const s = String(input ?? "").replace(/\r?\n$/, "");
  const freq = new Map();
  for (const ch of s) freq.set(ch, (freq.get(ch) || 0) + 1);
  return [...freq.entries()]
    .sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0))
    .map(([c, n]) => `${c} ${n}`)
    .join("\n");
}

test("empty input yields empty output", () => {
  assert.equal(characterFrequencyOrdering(""), "");
  assert.equal(characterFrequencyOrdering("\n"), "");
});

test("orders by character code ascending (A before a)", () => {
  assert.equal(characterFrequencyOrdering("Aa"), "A 1\na 1");
  assert.equal(characterFrequencyOrdering("aA"), "A 1\na 1");
});

test("does not use locale-sensitive case folding order", () => {
  const got = characterFrequencyOrdering("bBaA");
  assert.equal(got, "A 1\nB 1\na 1\nb 1");
  assert.notEqual(got, "a 1\nA 1\nb 1\nB 1");
});

test("preserves space characters as keys", () => {
  assert.equal(characterFrequencyOrdering("  "), "  2");
  assert.equal(characterFrequencyOrdering("a a"), "  1\na 2");
});

test("sample abca", () => {
  assert.equal(characterFrequencyOrdering("abca"), "a 2\nb 1\nc 1");
});
