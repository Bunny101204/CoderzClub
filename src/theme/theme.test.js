import test from "node:test";
import assert from "node:assert/strict";
import {
  DEFAULT_THEME,
  THEME_STORAGE_KEY,
  applyThemeClass,
  editorSurfaceClasses,
  editorThemeName,
  normalizeTheme,
  persistTheme,
  readStoredTheme
} from "./theme.js";

function memoryStorage(initial = {}) {
  const store = { ...initial };
  return {
    getItem(key) { return Object.prototype.hasOwnProperty.call(store, key) ? store[key] : null; },
    setItem(key, value) { store[key] = String(value); }
  };
}

test("default theme is dark", () => {
  assert.equal(DEFAULT_THEME, "dark");
  assert.equal(readStoredTheme(memoryStorage()), "dark");
});

test("saved light preference is restored", () => {
  assert.equal(readStoredTheme(memoryStorage({ [THEME_STORAGE_KEY]: "light" })), "light");
});

test("saved dark preference is restored", () => {
  assert.equal(readStoredTheme(memoryStorage({ [THEME_STORAGE_KEY]: "dark" })), "dark");
});

test("invalid stored value falls back to dark", () => {
  assert.equal(normalizeTheme("neon"), "dark");
  assert.equal(readStoredTheme(memoryStorage({ [THEME_STORAGE_KEY]: "neon" })), "dark");
});

test("toggling applies root class and persistence", () => {
  const classNames = new Set(["dark"]);
  const root = {
    classList: {
      add(name) { classNames.add(name); },
      remove(name) { classNames.delete(name); }
    }
  };
  const storage = memoryStorage({ [THEME_STORAGE_KEY]: "dark" });
  applyThemeClass("light", root);
  persistTheme("light", storage);
  assert.equal(classNames.has("dark"), false);
  assert.equal(storage.getItem(THEME_STORAGE_KEY), "light");
  applyThemeClass("dark", root);
  persistTheme("dark", storage);
  assert.equal(classNames.has("dark"), true);
  assert.equal(storage.getItem(THEME_STORAGE_KEY), "dark");
});

test("editor theme mapping follows global theme", () => {
  assert.equal(editorThemeName("dark"), "vs-dark");
  assert.equal(editorThemeName("light"), "vs");
  assert.match(editorSurfaceClasses("light"), /bg-white/);
  assert.match(editorSurfaceClasses("dark"), /bg-gray-800/);
});
