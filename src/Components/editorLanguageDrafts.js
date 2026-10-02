export function editorStorageKey(problemId, languageId) {
  return `code_${problemId}_${languageId}`;
}

export function resolveEditorSource({ storedDraft, languageTemplate }) {
  if (storedDraft != null) {
    return storedDraft;
  }
  if (typeof languageTemplate === "string") {
    return languageTemplate;
  }
  return "";
}

export function applyLanguageSwitch({
  previousLanguageId,
  nextLanguageId,
  currentCode,
  drafts,
  templates,
}) {
  const nextDrafts = { ...drafts };
  if (previousLanguageId != null) {
    nextDrafts[previousLanguageId] = currentCode;
  }
  const storedDraft = Object.prototype.hasOwnProperty.call(nextDrafts, nextLanguageId)
    ? nextDrafts[nextLanguageId]
    : null;
  const languageTemplate = Object.prototype.hasOwnProperty.call(templates, nextLanguageId)
    ? templates[nextLanguageId]
    : "";
  const code = resolveEditorSource({ storedDraft, languageTemplate });
  return { drafts: nextDrafts, code };
}
