export const CONTEST_RUN_MAX_POLLS = 90;

export function getContestRunPollDelay(pollNumber) {
  if (pollNumber <= 0) {
    return 1000;
  }
  if (pollNumber === 1) {
    return 1500;
  }
  return 2000;
}

export function isTerminalContestRun(status) {
  return status === "COMPLETED" || status === "FAILED";
}
