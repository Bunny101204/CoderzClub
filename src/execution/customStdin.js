/**
 * Stdin sent to Judge0. Line breaks are preserved. The value is not trimmed
 * and newlines are not collapsed. CRLF from pasted Windows text becomes LF.
 */
export function customStdinForExecution(value) {
  if (typeof value !== "string") {
    return "";
  }
  return value.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
}

/** Direct custom stdin is sent unchanged after CRLF normalization. */
export function executionStdin({ preserveExactly, value }) {
  if (preserveExactly) {
    return customStdinForExecution(value);
  }
  const built = typeof value === "string" ? value : "";
  return built.endsWith("\n") ? built : `${built}\n`;
}
