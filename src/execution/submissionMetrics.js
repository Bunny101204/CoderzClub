function finiteNonNegative(value) {
  if (value == null || value === "") return null;
  const number = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(number) || number < 0) return null;
  return number;
}

function formatQuantity(amount, unit) {
  const rounded = Math.round(amount * 10) / 10;
  const text = Math.abs(rounded - Math.round(rounded)) < 0.05
    ? String(Math.round(rounded))
    : rounded.toFixed(1);
  return `${text} ${unit}`;
}

/** Persisted Submission.runtime: judged code-execution time in milliseconds (maximum judged testcase runtime). */
export function formatSubmissionRuntime(value) {
  const milliseconds = finiteNonNegative(value);
  if (milliseconds == null) return "—";
  if (milliseconds < 1000) return `${Math.round(milliseconds)} ms`;
  return `${(milliseconds / 1000).toFixed(2)} s`;
}

/** Persisted Submission.memory: judged execution memory in bytes, shown in IEC binary units. */
export function formatSubmissionMemory(value) {
  const bytes = finiteNonNegative(value);
  if (bytes == null) return "—";
  const kib = 1024;
  const mib = kib * 1024;
  const gib = mib * 1024;
  if (bytes < kib) return `${Math.round(bytes)} B`;
  if (bytes < mib) return formatQuantity(bytes / kib, "KiB");
  if (bytes < gib) return formatQuantity(bytes / mib, "MiB");
  return formatQuantity(bytes / gib, "GiB");
}

export function formatSubmissionTimestamp(value) {
  if (value == null || value === "") return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleString();
}
