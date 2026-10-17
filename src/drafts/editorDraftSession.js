import {
  buildDraftKey,
  createDraftRecord,
  deriveDraftStatus,
  parseDraftRecord,
  reconcileDraft,
  shouldKeepaliveSync,
} from "./codeDraft.js";

const LOCAL_DEBOUNCE_MS = 300;
const DEDUPE_MS = 2000;

function defaultSchedule() {
  return {
    later(fn, ms) {
      return setTimeout(fn, ms);
    },
    cancel(id) {
      clearTimeout(id);
    },
  };
}

function payloadFor(code, expectedRevision) {
  return {
    code: code ?? "",
    expectedRevision: expectedRevision > 0 ? expectedRevision : 0,
  };
}

/**
 * Single owner of one editor's draft key, dirty flag, and cloud revision.
 * Typing updates local storage only. Cloud writes happen at session boundaries.
 */
export function createDraftSession({
  storage,
  transport,
  now = () => new Date().toISOString(),
  schedule = defaultSchedule(),
  onApply = () => {},
  onView = () => {},
  clock = () => Date.now(),
}) {
  let current = null;
  let debounceTimer = null;
  let storageError = false;
  let syncing = false;
  let syncFailed = false;
  let conflict = null;
  let generation = 0;
  let suppressCode = null;
  const inflight = new Map();
  const recent = new Map();

  function applyCode(code) {
    suppressCode = code ?? "";
    onApply(suppressCode);
  }

  function emit() {
    onView({
      status: deriveDraftStatus({
        storageError,
        conflict,
        syncing,
        syncFailed,
        record: current?.record || null,
      }),
      conflict,
    });
  }

  function read(key) {
    try {
      return parseDraftRecord(storage.getItem(key));
    } catch {
      return null;
    }
  }

  function write(key, record) {
    try {
      storage.setItem(key, JSON.stringify(record));
      storageError = false;
      return true;
    } catch {
      storageError = true;
      return false;
    }
  }

  function flushLocal() {
    if (debounceTimer != null) {
      schedule.cancel(debounceTimer);
      debounceTimer = null;
    }
    if (!current?.record || !current.persist) return;
    write(current.key, current.record);
  }

  function noteEdit(code) {
    const nextCode = code ?? "";
    if (suppressCode !== null) {
      if (nextCode === suppressCode) suppressCode = null;
      return;
    }
    if (!current?.acceptEdits || !current.record) return;
    if (nextCode === current.record.code && current.record.dirty) return;
    if (nextCode === current.record.code && !current.record.dirty && current.persist) return;
    if (nextCode === current.record.code && !current.persist) return;
    current.record = {
      ...current.record,
      code: nextCode,
      dirty: true,
      conflict: false,
      localUpdatedAt: now(),
    };
    current.persist = true;
    conflict = null;
    syncFailed = false;
    emit();
    if (debounceTimer != null) schedule.cancel(debounceTimer);
    const key = current.key;
    debounceTimer = schedule.later(() => {
      debounceTimer = null;
      if (current?.key === key && current.record) write(key, current.record);
      emit();
    }, LOCAL_DEBOUNCE_MS);
  }

  function dedupeKey(snapshot) {
    return `${snapshot.key}\0${snapshot.expectedRevision}\0${snapshot.code}`;
  }

  function recentlyStarted(key) {
    const at = recent.get(key);
    return at != null && clock() - at < DEDUPE_MS;
  }

  async function syncBoundary({ keepalive = false } = {}) {
    flushLocal();
    if (!current?.record?.dirty || !current.persist) return { skipped: true };
    if (conflict) return { skipped: true, reason: "conflict" };
    const snapshot = {
      key: current.key,
      identity: { ...current.identity },
      code: current.record.code,
      expectedRevision: current.record.baseRevision || 0,
    };
    const key = dedupeKey(snapshot);
    if (inflight.has(key)) return inflight.get(key);
    if (recentlyStarted(key)) return { skipped: true, reason: "deduped" };
    const run = performSync(snapshot, keepalive, key);
    inflight.set(key, run);
    try {
      return await run;
    } finally {
      inflight.delete(key);
    }
  }

  async function performSync(snapshot, keepalive, key) {
    const body = payloadFor(snapshot.code, snapshot.expectedRevision);
    if (keepalive) {
      const serialized = JSON.stringify(body);
      if (!shouldKeepaliveSync(serialized)) {
        return { skipped: true, reason: "too-large" };
      }
      recent.set(key, clock());
      transport.putKeepalive?.(snapshot.identity, body);
      return { started: true, keepalive: true };
    }
    syncing = true;
    syncFailed = false;
    emit();
    recent.set(key, clock());
    let result;
    try {
      result = await transport.put(snapshot.identity, body);
    } catch {
      result = { ok: false, status: 0 };
    }
    syncing = false;
    if (!result || result.skipped) {
      recent.delete(key);
      syncFailed = Boolean(current?.record?.dirty);
      emit();
      return result || { ok: false };
    }
    if (result.ok) {
      applyAck(snapshot, result.draft);
      syncFailed = false;
    } else if (result.status === 409 && result.body) {
      recent.delete(key);
      syncFailed = false;
      applyConflict(snapshot, result.body);
    } else {
      recent.delete(key);
      syncFailed = true;
    }
    emit();
    return result;
  }

  function applyAck(snapshot, draft) {
    const stored = read(snapshot.key);
    const base = stored && stored.code === snapshot.code ? stored : null;
    const memoryMatch = current?.key === snapshot.key && current.record?.code === snapshot.code;
    if (!base && !memoryMatch) return;
    const clean = createDraftRecord({
      code: snapshot.code,
      dirty: false,
      baseRevision: draft?.revision,
      cloudUpdatedAt: draft?.updatedAt || null,
      localUpdatedAt: (base || current.record).localUpdatedAt,
    });
    write(snapshot.key, clean);
    if (memoryMatch) {
      current.record = clean;
      current.persist = true;
      conflict = null;
    }
  }

  function applyConflict(snapshot, body) {
    if (typeof body?.code === "string" && body.code === snapshot.code) {
      applyAck(snapshot, { revision: body.revision, updatedAt: body.updatedAt });
      return;
    }
    if (current?.key !== snapshot.key) return;
    conflict = {
      code: body?.code ?? "",
      revision: Number(body?.revision) || 0,
      updatedAt: body?.updatedAt || null,
    };
    if (current.record) {
      current.record = { ...current.record, conflict: true };
      current.persist = true;
      write(snapshot.key, current.record);
    }
  }

  async function open(next) {
    const nextKey = buildDraftKey(next);
    if (current && current.key !== nextKey) {
      flushLocal();
      await syncBoundary({ keepalive: false });
    }
    const token = ++generation;
    if (!nextKey) {
      current = null;
      conflict = null;
      emit();
      return;
    }
    const starter = next.starterCode || "";
    let record = read(nextKey);
    let persist = Boolean(record);
    // A blank editor before the template is applied is not an intentional empty draft.
    // A saved record with code "" still wins above.
    if (
      next.importVisibleCode === true &&
      !record &&
      typeof next.currentCode === "string" &&
      next.currentCode !== "" &&
      next.currentCode !== starter
    ) {
      record = createDraftRecord({
        code: next.currentCode,
        dirty: true,
        baseRevision: 0,
        localUpdatedAt: now(),
      });
      persist = true;
      write(nextKey, record);
    }
    const showCode = record ? record.code : starter;
    current = {
      key: nextKey,
      identity: {
        userId: next.userId,
        contestId: next.contestId || null,
        problemId: next.problemId,
        languageId: next.languageId,
      },
      record: record || createDraftRecord({ code: starter, dirty: false, baseRevision: 0 }),
      persist,
      acceptEdits: true,
    };
    if (!record) current.persist = false;
    conflict = null;
    syncFailed = false;
    applyCode(showCode);
    emit();

    let cloud = null;
    let cloudAvailable = false;
    try {
      cloud = await transport.get(current.identity);
      cloudAvailable = true;
    } catch {
      cloudAvailable = false;
    }
    if (token !== generation || current?.key !== nextKey) return;
    const reconciled = reconcileDraft({
      local: current.persist || current.record?.dirty ? current.record : null,
      cloud,
      cloudAvailable,
    });
    if (reconciled.action === "use-cloud" || reconciled.action === "adopt-cloud-revision") {
      current.record = reconciled.record;
      current.persist = true;
      write(nextKey, current.record);
      conflict = null;
      applyCode(current.record.code);
    } else if (reconciled.action === "conflict") {
      current.record = reconciled.record;
      current.persist = true;
      conflict = reconciled.cloud;
      write(nextKey, current.record);
    } else if (reconciled.record && (current.persist || reconciled.record.dirty)) {
      current.record = reconciled.record;
      current.persist = true;
      if (reconciled.record.dirty) write(nextKey, current.record);
    }
    if (!cloudAvailable && current?.persist) {
      syncFailed = true;
    }
    emit();
  }

  function useCloud() {
    if (!conflict || !current) return;
    const record = createDraftRecord({
      code: conflict.code ?? "",
      dirty: false,
      baseRevision: conflict.revision,
      cloudUpdatedAt: conflict.updatedAt,
      localUpdatedAt: now(),
    });
    current.record = record;
    current.persist = true;
    write(current.key, record);
    conflict = null;
    syncFailed = false;
    applyCode(record.code);
    emit();
  }

  async function keepLocal() {
    if (!conflict || !current?.record) return { skipped: true };
    const revision = conflict.revision;
    conflict = null;
    current.record = {
      ...current.record,
      dirty: true,
      conflict: false,
      baseRevision: revision > 0 ? revision : 0,
      localUpdatedAt: now(),
    };
    current.persist = true;
    write(current.key, current.record);
    emit();
    return syncBoundary({ keepalive: false });
  }

  return { noteEdit, open, flushLocal, syncBoundary, useCloud, keepLocal };
}
