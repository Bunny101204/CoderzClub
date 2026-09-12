const SOLVED_RESULTS = new Set([
  "ACCEPTED",
  "SOLVED",
  "PASSED",
  "SUCCESS",
  "COMPLETED"
]);

export function normalizeId(value) {
  return value == null ? "" : String(value).trim();
}

export function buildProblemIdAliases(problems) {
  const aliases = {};
  (Array.isArray(problems) ? problems : []).forEach(problem => {
    const mongoId = normalizeId(problem?.id);
    if (!mongoId) return;
    aliases[mongoId] = mongoId;
    const numericId = normalizeId(problem?.numericId);
    if (numericId) aliases[numericId] = mongoId;
  });
  return aliases;
}

function submissionResultValues(submission) {
  return [
    submission?.result,
    submission?.verdict,
    submission?.status,
    submission?.finalResult,
    submission?.jobStatus
  ]
    .map(value => normalizeId(value).toUpperCase())
    .filter(Boolean);
}

export function buildProblemStatus(submissions, aliases = {}) {
  const status = {};
  (Array.isArray(submissions) ? submissions : []).forEach(submission => {
    const rawId = normalizeId(submission?.problemId ?? submission?.problem?.id);
    const problemId = aliases[rawId] || rawId;
    if (!problemId) return;

    const results = submissionResultValues(submission);
    if (results.some(result => SOLVED_RESULTS.has(result))) {
      status[problemId] = "SOLVED";
    } else if (status[problemId] !== "SOLVED") {
      status[problemId] = "ATTEMPTED";
    }
  });
  return status;
}

export function getBundleProgress(bundle, status, aliases = {}) {
  const problemIds = [...new Set((bundle?.problemIds || [])
    .map(normalizeId)
    .map(id => aliases[id] || id)
    .filter(Boolean))];
  const solved = problemIds.filter(id => status[id] === "SOLVED").length;
  const attempted = problemIds.filter(id => status[id]).length;

  return {
    total: problemIds.length,
    solved,
    attempted,
    state: problemIds.length > 0 && solved === problemIds.length
      ? "completed"
      : attempted > 0 ? "incomplete" : "unattempted"
  };
}
