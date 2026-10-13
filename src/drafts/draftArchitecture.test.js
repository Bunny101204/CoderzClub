import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const editor = fs.readFileSync(new URL("../Components/Judge0CodeEditor.jsx", import.meta.url), "utf8");
const session = fs.readFileSync(new URL("./editorDraftSession.js", import.meta.url), "utf8");

test("editor typing does not call the draft API", () => {
  assert.match(editor, /onChange=\{\(e\) => setSourceCode\(e\.target\.value\)\}/);
  assert.equal(editor.includes("editorStorageKey"), false);
  assert.equal(editor.includes("localStorage"), false);
  assert.equal(editor.includes("api.drafts"), false);
  assert.equal(editor.includes("code_${"), false);
  const noteEdit = session.slice(session.indexOf("function noteEdit"), session.indexOf("function dedupeKey"));
  assert.equal(noteEdit.includes("transport"), false);
  assert.equal(noteEdit.includes("fetch"), false);
  assert.equal(noteEdit.includes("axios"), false);
});
