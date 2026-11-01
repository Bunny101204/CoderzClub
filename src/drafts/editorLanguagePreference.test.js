import test from "node:test";
import assert from "node:assert/strict";
import { readSavedEditorLanguage, saveEditorLanguage } from "./editorLanguagePreference.js";

test("stale unsupported language preferences fall back to a Java starter", () => {
  try {
    for (const value of ["999", "0", "-1", "NaN", "java", "", null]) {
      global.localStorage = { getItem: () => value };
      assert.equal(readSavedEditorLanguage("p1", null), 62);
    }
  } finally {
    delete global.localStorage;
  }
});

test("the selected language is restored for the same problem", () => {
  const store = new Map();
  global.localStorage = {
    getItem: (key) => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => store.set(key, String(value)),
  };
  assert.equal(readSavedEditorLanguage("p1", null), 62);
  saveEditorLanguage("p1", null, 71);
  assert.equal(readSavedEditorLanguage("p1", null), 71);
  assert.equal(readSavedEditorLanguage("p2", null), 62);
  delete global.localStorage;
});
