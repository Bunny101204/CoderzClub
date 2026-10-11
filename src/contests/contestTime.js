export function browserTimeZone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  } catch {
    return "UTC";
  }
}

export function instantToDatetimeLocal(iso) {
  if (!iso) {
    return "";
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function datetimeLocalToIso(value) {
  if (!value) {
    return null;
  }
  const normalized = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value) ? `${value}:00` : value;
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date.toISOString();
}

export function formatLocalDateTime(iso) {
  if (!iso) {
    return "—";
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return "—";
  }
  return date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

export function msUntil(iso, now = Date.now()) {
  if (!iso) {
    return null;
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date.getTime() - now;
}

export function formatCountdown(ms) {
  if (ms == null) {
    return "";
  }
  if (ms <= 0) {
    return "0s";
  }
  const totalSeconds = Math.floor(ms / 1000);
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (days > 0) {
    return `${days}d ${hours}h ${minutes}m`;
  }
  if (hours > 0) {
    return `${hours}h ${minutes}m ${seconds}s`;
  }
  if (minutes > 0) {
    return `${minutes}m ${seconds}s`;
  }
  return `${seconds}s`;
}

export function countdownTarget(contest) {
  if (!contest) {
    return null;
  }
  if (contest.status === "UPCOMING") {
    return contest.startAt;
  }
  if (contest.status === "LIVE") {
    return contest.endAt;
  }
  return null;
}

export function countdownLabel(contest) {
  if (!contest) {
    return "";
  }
  if (contest.status === "UPCOMING") {
    return "Starts in";
  }
  if (contest.status === "LIVE") {
    return "Ends in";
  }
  return "";
}
