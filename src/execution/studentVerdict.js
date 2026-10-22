export function studentVerdictCopy({
  verdict,
  hidden = false,
  passedCount,
  totalCount,
  errorMessage,
  errorCode,
  diagnosticMessage,
  timeLimitSeconds,
  memoryLimitKb
} = {}) {
  const code = errorCode
    || (verdict === "INTERNAL_ERROR" ? "JUDGE0_PROVIDER_ERROR" : verdict);
  if (verdict === "INTERNAL_ERROR" || code === "JUDGE0_PROVIDER_ERROR") {
    return {
      title: "INTERNAL_ERROR",
      code: "JUDGE0_PROVIDER_ERROR",
      summary: "Judging infrastructure failed. This is not a wrong answer.",
      detail: "Execution service is temporarily unavailable. Please retry.",
      showHiddenIo: false
    };
  }
  if (verdict === "COMPILATION_ERROR") {
    return {
      title: "Compilation error",
      code: "COMPILATION_ERROR",
      summary: "Your code did not compile.",
      detail: bound(errorMessage) || "Compilation failed.",
      showHiddenIo: false
    };
  }
  if (verdict === "RUNTIME_ERROR") {
    return {
      title: "Runtime error",
      code: "RUNTIME_ERROR",
      summary: "Your program crashed while running a test.",
      detail: bound(errorMessage) || "A runtime error occurred.",
      showHiddenIo: false
    };
  }
  if (verdict === "TIME_LIMIT_EXCEEDED") {
    return {
      title: "Time limit exceeded",
      code: "TIME_LIMIT_EXCEEDED",
      summary: timeLimitSeconds
        ? `Time limit exceeded (${timeLimitSeconds}s per test).`
        : "Time limit exceeded.",
      detail: diagnosticMessage || null,
      showHiddenIo: false
    };
  }
  if (verdict === "MEMORY_LIMIT_EXCEEDED") {
    return {
      title: "Memory limit exceeded",
      code: "MEMORY_LIMIT_EXCEEDED",
      summary: memoryLimitKb
        ? `Memory limit exceeded (${memoryLimitKb} KB).`
        : "Memory limit exceeded.",
      detail: diagnosticMessage || null,
      showHiddenIo: false
    };
  }
  const passedLine = Number.isFinite(passedCount) && Number.isFinite(totalCount)
    ? `Passed ${passedCount} of ${totalCount} test cases.`
    : null;
  if (hidden) {
    return {
      title: "Wrong answer",
      code: "WRONG_ANSWER",
      summary: "Wrong answer on a hidden test.",
      detail: passedLine,
      showHiddenIo: false
    };
  }
  return {
    title: "Wrong answer",
    code: "WRONG_ANSWER",
    summary: "Wrong answer on a public test.",
    detail: passedLine,
    showHiddenIo: true
  };
}

function bound(text) {
  if (text == null) return "";
  const value = String(text);
  if (value === "null" || /:\s*null$/i.test(value)) return "";
  return value.length > 2000 ? value.slice(0, 2000) : value;
}

export function formatSupportReference(id) {
  if (!id) return "";
  const text = String(id);
  return text.length <= 8 ? text : `${text.slice(0, 8)}…`;
}
