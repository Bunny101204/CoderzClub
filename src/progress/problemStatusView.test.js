import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";
import { hasVisibleUnsolvedPlaceholder, statusPresentation } from "./problemStatusView.js";

const home = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "../Components/HomePage.jsx"),
  "utf8"
);
const mark = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "../Components/ProblemStatusMark.jsx"),
  "utf8"
);

test("SOLVED uses a check kind with a screen-reader label", () => {
  const presentation = statusPresentation("SOLVED");
  assert.equal(presentation.kind, "check");
  assert.equal(presentation.label, "Solved");
  assert.equal(presentation.visibleText, "");
});

test("ATTEMPTED uses a non-emoji dot kind", () => {
  const presentation = statusPresentation("ATTEMPTED");
  assert.equal(presentation.kind, "dot");
  assert.equal(presentation.label, "Attempted");
  assert.equal(presentation.visibleText, "");
});

test("UNSOLVED is visually blank without dash or N/A", () => {
  const presentation = statusPresentation("UNSOLVED");
  assert.equal(presentation.kind, "blank");
  assert.equal(presentation.label, "Not attempted");
  assert.equal(presentation.visibleText, "");
  assert.equal(hasVisibleUnsolvedPlaceholder("UNSOLVED"), false);
  assert.notEqual(presentation.visibleText, "-");
  assert.notEqual(presentation.visibleText, "–");
  assert.notEqual(presentation.visibleText, "N/A");
});

test("problems list uses SVG check/dot and a screen-reader label for blank unsolved", () => {
  assert.match(home, /ProblemStatusMark/);
  assert.doesNotMatch(home, /aria-hidden="true">–</);
  assert.match(mark, /CheckIcon/);
  assert.match(mark, /StatusDotIcon/);
  assert.match(mark, /sr-only/);
});
