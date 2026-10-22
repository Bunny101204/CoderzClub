export function formatCodingDuration(seconds) {
  if (seconds == null || seconds === "") {
    return "—";
  }
  const value = Number(seconds);
  if (!Number.isFinite(value) || value < 0) {
    return "—";
  }
  const total = Math.floor(value);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const secs = total % 60;
  if (hours > 0) {
    return `${hours}h ${String(minutes).padStart(2, "0")}m`;
  }
  if (minutes > 0) {
    return `${minutes}m ${String(secs).padStart(2, "0")}s`;
  }
  return `${secs}s`;
}

export function codingDurationSecondsFromElapsedMs(elapsedMs) {
  if (elapsedMs == null || !Number.isFinite(Number(elapsedMs)) || Number(elapsedMs) < 0) {
    return null;
  }
  return Math.floor(Number(elapsedMs) / 1000);
}

export const MAX_CODING_DURATION_SECONDS = 7 * 24 * 60 * 60;

export function sanitizeCodingDurationSeconds(value) {
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0 || n > MAX_CODING_DURATION_SECONDS) {
    return null;
  }
  return Math.floor(n);
}
