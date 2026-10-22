export function indexProgress(entries) {
  const index = {};
  (Array.isArray(entries) ? entries : []).forEach((entry) => {
    if (!entry || !entry.status) return;
    const status = entry.status;
    const ids = [
      entry.problemId,
      entry.numericId,
      ...(Array.isArray(entry.aliases) ? entry.aliases : []),
    ];
    ids.forEach((id) => {
      if (id == null || String(id).trim() === "") return;
      index[String(id)] = status;
    });
  });
  return index;
}

export function statusForProblem(problem, index) {
  if (!problem || !index) return null;
  return index[String(problem.id)]
    || (problem.numericId != null ? index[String(problem.numericId)] : null)
    || null;
}
