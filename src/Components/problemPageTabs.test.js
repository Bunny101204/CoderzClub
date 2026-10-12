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

function sliceBetween(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.ok(start >= 0, startMarker);
  assert.ok(end > start, endMarker);
  return source.slice(start, end);
}

test("inline history is not rendered inside the Problem tab", () => {
  const problemTab = sliceBetween(
    page,
    '{contentTab === "problem" && (',
    "{showSubmissions && submissionsOpened && ("
  );
  assert.equal(problemTab.includes("ProblemSubmissionHistory"), false);
  const submissionsPanel = sliceBetween(
    page,
    "{showSubmissions && submissionsOpened && (",
    "<Judge0CodeEditor"
  );
  assert.match(submissionsPanel, /<ProblemSubmissionHistory/);
  assert.match(submissionsPanel, /enabled=\{contentTab === "submissions"\}/);
});

test("editor stays mounted while switching tabs", () => {
  const problemTab = sliceBetween(
    page,
    '{contentTab === "problem" && (',
    "{showSubmissions && submissionsOpened && ("
  );
  const submissionsPanel = sliceBetween(
    page,
    "{showSubmissions && submissionsOpened && (",
    "<Judge0CodeEditor"
  );
  assert.equal(problemTab.includes("Judge0CodeEditor"), false);
  assert.equal(submissionsPanel.includes("<Judge0CodeEditor"), false);
  assert.match(submissionsPanel, /contentTab === "submissions" \? "" : "hidden"/);
  const editorAt = page.indexOf("<Judge0CodeEditor");
  assert.ok(editorAt > page.indexOf("{showSubmissions && submissionsOpened && ("));
  assert.doesNotMatch(page.slice(editorAt - 180, editorAt), /contentTab ===/);
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
