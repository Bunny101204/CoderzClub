import { createOperationId } from "./mutationGate.js";

export const ATTEMPT_STORAGE_KEY = "cz-submission-attempt";
const UNRESOLVED_MS = 2 * 60 * 1000;
const ACCEPTED_JOB_MS = 24 * 60 * 60 * 1000;

export function memoryStorage(initial) {
  const data = new Map(initial || []);
  return {
    getItem(key) {
      return data.has(key) ? data.get(key) : null;
    },
    setItem(key, value) {
      data.set(key, String(value));
    },
    removeItem(key) {
      data.delete(key);
    },
  };
}

function storageKey(problemId, contestId) {
  return `${ATTEMPT_STORAGE_KEY}:${String(contestId || "")}:${String(problemId ?? "")}`;
}

function read(storage, problemId, contestId) {
  try {
    const raw = storage.getItem(storageKey(problemId, contestId));
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed.operationId !== "string") return null;
    return parsed;
  } catch {
    return null;
  }
}

function write(storage, problemId, contestId, value) {
  try {
    storage.setItem(storageKey(problemId, contestId), JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}

function sameSubject(current, problemId, contestId) {
  return current
    && current.problemId === String(problemId ?? "")
    && String(current.contestId || "") === String(contestId || "");
}

function stillOpen(current, now) {
  if (!current || current.settled) return false;
  const age = now - Number(current.at || 0);
  if (current.jobId) return age < ACCEPTED_JOB_MS;
  return age < UNRESOLVED_MS;
}

/**
 * One open attempt per problem across clicks, Enter, and tabs that share
 * storage. A settled attempt, or a different problem, gets a new id.
 * Identical source code is not an input: a later attempt is a new submission.
 */
export function claimAttempt({
  storage,
  problemId,
  contestId = null,
  now = Date.now(),
  createId = createOperationId,
}) {
  const current = read(storage, problemId, contestId);
  if (sameSubject(current, problemId, contestId) && stillOpen(current, now)) {
    return {
      operationId: current.operationId,
      jobId: current.jobId || null,
      resume: Boolean(current.jobId),
      reuse: !current.jobId,
    };
  }
  const next = {
    operationId: createId(),
    problemId: String(problemId ?? ""),
    contestId: String(contestId || ""),
    at: now,
    jobId: null,
    settled: false,
  };
  const latest = read(storage, problemId, contestId);
  if (sameSubject(latest, problemId, contestId) && stillOpen(latest, now)) {
    return {
      operationId: latest.operationId,
      jobId: latest.jobId || null,
      resume: Boolean(latest.jobId),
      reuse: !latest.jobId,
    };
  }
  write(storage, problemId, contestId, next);
  const confirmed = read(storage, problemId, contestId);
  if (confirmed && confirmed.operationId !== next.operationId && stillOpen(confirmed, now)
      && sameSubject(confirmed, problemId, contestId)) {
    return {
      operationId: confirmed.operationId,
      jobId: confirmed.jobId || null,
      resume: Boolean(confirmed.jobId),
      reuse: !confirmed.jobId,
    };
  }
  return { operationId: next.operationId, jobId: null, resume: false, reuse: false };
}

export function attachAcceptedJob(storage, operationId, jobId, problemId, contestId = null) {
  const current = read(storage, problemId, contestId);
  if (!current || current.operationId !== operationId || !jobId) return current;
  const next = { ...current, jobId, at: current.at || Date.now() };
  write(storage, problemId, contestId, next);
  return next;
}

export function settleAttempt(storage, operationId, problemId, contestId = null) {
  const current = read(storage, problemId, contestId);
  if (!current) return;
  if (operationId && current.operationId !== operationId) return;
  write(storage, problemId, contestId, { ...current, settled: true });
}

export function readAttempt(storage, problemId, contestId = null) {
  return read(storage, problemId, contestId);
}

/**
 * A second tab that sent its own key is told the owner's key. Replace the
 * local id with that key so the retry replays the original job. A stored
 * job id for the same key is kept.
 */
export function adoptServerAttempt(storage, { operationId, problemId, contestId = null, jobId = null, now = Date.now() }) {
  if (!operationId) return read(storage, problemId, contestId);
  const current = read(storage, problemId, contestId);
  if (current && current.operationId === operationId && current.jobId && !jobId) {
    return current;
  }
  const next = {
    operationId,
    problemId: String(problemId ?? ""),
    contestId: String(contestId || ""),
    at: current && current.operationId === operationId ? current.at : now,
    jobId: jobId || (current && current.operationId === operationId ? current.jobId : null) || null,
    settled: false,
  };
  write(storage, problemId, contestId, next);
  return next;
}
