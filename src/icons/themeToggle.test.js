import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";
import { themeTogglePresentation } from "./themeToggle.js";

const header = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "../Components/Header.jsx"),
  "utf8"
);

test("dark mode shows a sun icon mapping for switching to light", () => {
  const presentation = themeTogglePresentation("dark");
  assert.equal(presentation.icon, "sun");
  assert.equal(presentation.label, "Switch to light theme");
  assert.equal(presentation.next, "light");
});

test("light mode shows a moon icon mapping for switching to dark", () => {
  const presentation = themeTogglePresentation("light");
  assert.equal(presentation.icon, "moon");
  assert.equal(presentation.label, "Switch to dark theme");
});

test("header toggle is an icon button with accessible labels and no Light/Dark text", () => {
  assert.match(header, /ThemeToggleIcon/);
  assert.match(header, /themeTogglePresentation\(theme\)\.label/);
  assert.doesNotMatch(header, />Light</);
  assert.doesNotMatch(header, />Dark</);
});
