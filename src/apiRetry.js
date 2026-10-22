const IDEMPOTENT_READ_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

export function isIdempotentReadMethod(method) {
  return IDEMPOTENT_READ_METHODS.has(String(method || "").toUpperCase());
}

/**
 * Automatic retries are limited to safe, idempotent reads.
 * POST, PUT, PATCH, and DELETE are never retried, including HTTP 500.
 */
export function shouldRetryTransientFailure({ method, status, code, message, alreadyRetried }) {
  if (alreadyRetried || !isIdempotentReadMethod(method)) {
    return false;
  }
  const network = code === "ECONNABORTED" || code === "ERR_NETWORK" || message === "Network Error";
  const serverError = typeof status === "number" && status >= 500 && status < 600;
  return network || serverError;
}
