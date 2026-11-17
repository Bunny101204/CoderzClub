import test from "node:test";
import assert from "node:assert/strict";
import { readSavedEditorLanguage, saveEditorLanguage } from "./editorLanguagePreference.js";

test("stale unsupported language preferences fall back to Python", () => {
  try {
    for (const value of ["999", "0", "-1", "NaN", "java", "", null, "62", "63", "60"]) {
      global.localStorage = { getItem: () => value };
      assert.equal(readSavedEditorLanguage("p1", null), 71);
    }
  } finally {
    delete global.localStorage;
  }
});

test("the selected release language is restored for the same problem", () => {
  const store = new Map();
  global.localStorage = {
    getItem: (key) => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => store.set(key, String(value)),
  };
  assert.equal(readSavedEditorLanguage("p1", null), 71);
  saveEditorLanguage("p1", null, 54);
  assert.equal(readSavedEditorLanguage("p1", null), 54);
  assert.equal(readSavedEditorLanguage("p2", null), 71);
  saveEditorLanguage("p2", null, 62);
  assert.equal(readSavedEditorLanguage("p2", null), 71);
  delete global.localStorage;
});
