import { useRef } from "react";

/**
 * Synchronous in-flight gate.
 *
 * A React setState after a click is too late: the second click can run before
 * the next render. This flag flips in the same turn as the event.
 *
 * Holding the gate does not make the server exactly-once. Callers still need
 * an operation id for retries, and the server still decides whether a second
 * request is the same attempt.
 */
export function createMutationGate() {
  let held = false;
  let generation = 0;

  return {
    get pending() {
      return held;
    },
    tryBegin() {
      if (held) return null;
      held = true;
      generation += 1;
      const token = generation;
      let finished = false;
      return {
        token,
        isCurrent() {
          return generation === token;
        },
        finish() {
          if (finished || generation !== token) return;
          finished = true;
          held = false;
        },
      };
    },
  };
}

export function createOperationId() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `op-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/**
 * A response that never arrived. The server may already have accepted the
 * attempt, so the caller must retry with the same operation id.
 */
export function isUncertainOutcome(error) {
  if (!error || error.response) return false;
  const code = String(error.code || "");
  const message = String(error.message || "");
  return code === "ECONNABORTED"
    || code === "ERR_NETWORK"
    || code === "ETIMEDOUT"
    || message === "Network Error"
    || /timeout/i.test(message)
    || /network/i.test(message);
}

export async function runExclusive(gate, action) {
  const attempt = gate.tryBegin();
  if (!attempt) return { skipped: true };
  try {
    const result = await action();
    return { skipped: false, result };
  } finally {
    attempt.finish();
  }
}

export function useMutationGate() {
  const ref = useRef(null);
  if (ref.current == null) ref.current = createMutationGate();
  return ref.current;
}
