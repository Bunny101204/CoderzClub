export const THEME_STORAGE_KEY = "coderzclub-theme";
export const DEFAULT_THEME = "dark";

export function normalizeTheme(value) {
  return value === "light" ? "light" : "dark";
}

export function readStoredTheme(storage) {
  try {
    const raw = storage && typeof storage.getItem === "function"
      ? storage.getItem(THEME_STORAGE_KEY)
      : null;
    if (raw == null || raw === "") {
      return DEFAULT_THEME;
    }
    return normalizeTheme(raw);
  } catch {
    return DEFAULT_THEME;
  }
}

export function persistTheme(theme, storage) {
  const next = normalizeTheme(theme);
  if (storage && typeof storage.setItem === "function") {
    storage.setItem(THEME_STORAGE_KEY, next);
  }
  return next;
}

export function applyThemeClass(theme, root) {
  const next = normalizeTheme(theme);
  if (!root || !root.classList) {
    return next;
  }
  if (next === "dark") {
    root.classList.add("dark");
  } else {
    root.classList.remove("dark");
  }
  return next;
}

export function editorThemeName(theme) {
  return normalizeTheme(theme) === "light" ? "vs" : "vs-dark";
}

export function editorSurfaceClasses(theme) {
  return normalizeTheme(theme) === "light"
    ? "bg-white text-gray-900 border-gray-300"
    : "bg-gray-800 text-gray-100 border-gray-700";
}
