import { LANGUAGE_TEMPLATES } from "../Components/LanguageTemplates.js";

function editorLanguageKey(problemId, contestId) {
  return `coderzclub:editor-language:v1:${contestId || "practice"}:${problemId || "scratch"}`;
}

export function readSavedEditorLanguage(problemId, contestId) {
  const key = editorLanguageKey(problemId, contestId);
  if (!key || typeof localStorage === "undefined") return 62;
  try {
    const parsed = Number(localStorage.getItem(key));
    return Object.prototype.hasOwnProperty.call(LANGUAGE_TEMPLATES, parsed) ? parsed : 62;
  } catch {
    return 62;
  }
}

export function saveEditorLanguage(problemId, contestId, languageId) {
  const key = editorLanguageKey(problemId, contestId);
  if (!key) return;
  try {
    localStorage.setItem(key, String(languageId));
  } catch {
    // The selected language still applies for this visit when storage is unavailable.
  }
}
