import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const editor = fs.readFileSync(new URL("./Judge0CodeEditor.jsx", import.meta.url), "utf8");

test("run and submit send the selected Judge0 language id", () => {
  assert.match(editor, /language_id:\s*languageId/);
  assert.equal(editor.includes("language_id: 62"), false);
  assert.equal(editor.includes("language_id:62"), false);
});
