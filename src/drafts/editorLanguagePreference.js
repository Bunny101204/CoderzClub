import { RELEASE_ENABLED_LANGUAGE_IDS } from "../Components/LanguageTemplates.js";

const DEFAULT_EDITOR_LANGUAGE = 71;

function editorLanguageKey(problemId, contestId) {
  return `coderzclub:editor-language:v1:${contestId || "practice"}:${problemId || "scratch"}`;
}

function isReleaseEnabledLanguage(languageId) {
  return RELEASE_ENABLED_LANGUAGE_IDS.includes(Number(languageId));
}

export function readSavedEditorLanguage(problemId, contestId) {
  const key = editorLanguageKey(problemId, contestId);
  if (!key || typeof localStorage === "undefined") return DEFAULT_EDITOR_LANGUAGE;
  try {
    const parsed = Number(localStorage.getItem(key));
    return isReleaseEnabledLanguage(parsed) ? parsed : DEFAULT_EDITOR_LANGUAGE;
  } catch {
    return DEFAULT_EDITOR_LANGUAGE;
  }
}

export function saveEditorLanguage(problemId, contestId, languageId) {
  const key = editorLanguageKey(problemId, contestId);
  if (!key) return;
  try {
    if (!isReleaseEnabledLanguage(languageId)) return;
    localStorage.setItem(key, String(languageId));
  } catch {
    // The selected language still applies for this visit when storage is unavailable.
  }
}
