export const POLL_INTERVALS_MS = [
  { polls: 5, delay: 1000 },
  { polls: 10, delay: 2000 },
  { polls: 12, delay: 5000 },
  { polls: Infinity, delay: 10000 },
];

export const MAX_POLLS = 36;

export function getPollDelay(pollNumber) {
  if (pollNumber < 5) return 1000;
  if (pollNumber < 15) return 2000;
  if (pollNumber < 27) return 5000;
  return 10000;
}

export function isTerminalJobState(status) {
  return ["COMPLETED", "FAILED", "TIMEOUT", "CANCELLED"].includes(status);
}

export function getJobStatusMessage(job) {
  switch (job?.status) {
    case "QUEUED":
      return "Submission queued";
    case "RUNNING": {
      const completed = job.progress?.completed;
      const total = job.progress?.total;
      return Number.isFinite(completed) && Number.isFinite(total) && total > 0
        ? `Running ${completed}/${total} testcases`
        : "Running tests...";
    }
    case "RETRYING":
      return "Temporary execution issue; retrying";
    default:
      return "";
  }
}

export function normalizeJobResult(result) {
  const isHidden = result?.type === "hidden";
  return {
    type: isHidden ? "hidden" : "public",
    input: isHidden ? undefined : result?.input,
    expected: isHidden ? undefined : result?.expectedOutput,
    actual: isHidden ? undefined : result?.actualOutput,
    passed: Boolean(result?.passed),
    error: result?.errorType
      ? { type: result.errorType, message: result.errorMessage || "Test case failed" }
      : null,
    runtime: result?.runtime,
    memory: result?.memory,
  };
}

export function isHiddenResult(result) {
  return result?.type === "hidden";
}
