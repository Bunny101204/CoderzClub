import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const page = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "ProblemPageNew.jsx"),
  "utf8"
);
const history = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "ProblemSubmissionHistory.jsx"),
  "utf8"
);

test("Problem is the default tab", () => {
  assert.match(page, /useState\("problem"\)/);
  assert.match(page, />\s*Problem\s*</);
  assert.match(page, />\s*Submissions\s*</);
});

test("history is not fetched until the Submissions tab is opened", () => {
  assert.match(history, /import \{ useEffect, useState \} from "react"/);
  assert.match(page, /submissionsOpened/);
  assert.match(page, /setSubmissionsOpened\(true\)/);
  assert.match(history, /if \(!enabled \|\| !problemId\) return;/);
  assert.equal(page.includes("<ProblemSubmissionHistory") && page.includes("enabled={contentTab === \"submissions\"}"), true);
});

test("inline history is not rendered inside the Problem tab", () => {
  assert.doesNotMatch(page, /contentTab === "problem"[\s\S]*ProblemSubmissionHistory/);
});

test("editor stays mounted while switching tabs", () => {
  const editorIndex = page.indexOf("<Judge0CodeEditor");
  const tabIndex = page.indexOf("contentTab === \"submissions\"");
  assert.ok(editorIndex > 0);
  assert.ok(tabIndex > 0);
  assert.match(page, /hidden/);
  assert.doesNotMatch(page, /contentTab === "problem"[\s\S]*Judge0CodeEditor/);
});

test("compact back arrow sits before the title", () => {
  assert.match(page, /aria-label="Back to problems"/);
  assert.match(page, /title="Back to problems"/);
  assert.match(page, /BackArrowIcon/);
  assert.doesNotMatch(page, /Back to Problems/);
});

test("a completed submission refreshes history without polling", () => {
  assert.match(page, /setHistoryRefreshKey/);
  assert.match(page, /onSubmissionSuccess/);
  assert.doesNotMatch(page, /setInterval/);
  assert.match(history, /Load more/);
});
