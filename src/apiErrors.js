function isSafeBusinessMessage(value) {
  if (typeof value !== "string") {
    return false;
  }
  const text = value.trim();
  if (!text) {
    return false;
  }
  if (text.includes("\n") || text.includes("\t") || text.length > 300) {
    return false;
  }
  if (/\bjava\.|Exception\b|\bat com\.|mongo|OperationNotSupportedInTransaction|(?:https?|redis|amqp):\/\/|stack\s*trace|\b(?:EADDR|ECONN|ENOTFOUND)|\b(?:\d{1,3}\.){3}\d{1,3}\b/i.test(text)) {
    return false;
  }
  return true;
}

export function apiErrorMessage(error, fallback = "Request failed") {
  // Server failures never provide displayable diagnostics.
  if (Number(error?.response?.status) >= 500) return fallback;
  const data = error?.response?.data;
  if (isSafeBusinessMessage(data)) {
    return data.trim();
  }
  if (isSafeBusinessMessage(data?.error)) {
    return data.error.trim();
  }
  if (isSafeBusinessMessage(data?.message)) {
    return data.message.trim();
  }
  return fallback;
}
