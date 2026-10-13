export const DRAFT_RECORD_VERSION = 1;
export const KEEPALIVE_MAX_BYTES = 60 * 1024;

export function buildDraftKey({ userId, contestId, problemId, languageId }) {
  if (!userId || problemId == null || problemId === "" || languageId == null || languageId === "") {
    return null;
  }
  const user = encodeURIComponent(String(userId));
  const problem = encodeURIComponent(String(problemId));
  const language = encodeURIComponent(String(languageId));
  if (contestId) {
    return `coderzclub:draft:v1:${user}:contest:${encodeURIComponent(String(contestId))}:${problem}:${language}`;
  }
  return `coderzclub:draft:v1:${user}:practice:${problem}:${language}`;
}

export function legacyPracticeKey(problemId, languageId) {
  return `code_${problemId}_${languageId}`;
}

export function parseDraftRecord(raw) {
  if (raw == null || raw === "") return null;
  let parsed;
  try {
    parsed = typeof raw === "string" ? JSON.parse(raw) : raw;
  } catch {
    return null;
  }
  if (!parsed || parsed.version !== DRAFT_RECORD_VERSION || typeof parsed.code !== "string") {
    return null;
  }
  const baseRevision = Number(parsed.baseRevision);
  return {
    version: DRAFT_RECORD_VERSION,
    code: parsed.code,
    dirty: Boolean(parsed.dirty),
    baseRevision: Number.isFinite(baseRevision) && baseRevision > 0 ? baseRevision : 0,
    cloudUpdatedAt: typeof parsed.cloudUpdatedAt === "string" ? parsed.cloudUpdatedAt : null,
    localUpdatedAt: typeof parsed.localUpdatedAt === "string" ? parsed.localUpdatedAt : null,
    conflict: Boolean(parsed.conflict),
  };
}

export function createDraftRecord({
  code,
  dirty = false,
  baseRevision = 0,
  cloudUpdatedAt = null,
  localUpdatedAt = null,
  conflict = false,
}) {
  const revision = Number(baseRevision);
  return {
    version: DRAFT_RECORD_VERSION,
    code: code ?? "",
    dirty: Boolean(dirty),
    baseRevision: Number.isFinite(revision) && revision > 0 ? revision : 0,
    cloudUpdatedAt: cloudUpdatedAt || null,
    localUpdatedAt: localUpdatedAt || null,
    conflict: Boolean(conflict),
  };
}

function cloudDraft(cloud) {
  const revision = Number(cloud?.revision);
  if (!cloud || !Number.isFinite(revision) || revision <= 0 || typeof cloud.code !== "string") {
    return null;
  }
  return {
    code: cloud.code,
    revision,
    updatedAt: cloud.updatedAt || null,
  };
}

/**
 * Deterministic local/cloud reconciliation. Client timestamps are never used as ordering.
 */
export function reconcileDraft({ local, cloud, cloudAvailable }) {
  const remote = cloudAvailable ? cloudDraft(cloud) : null;
  if (!cloudAvailable) {
    return { action: local ? "keep-local" : "use-starter", record: local || null, cloud: null };
  }
  if (!local) {
    if (!remote) return { action: "use-starter", record: null, cloud: null };
    return {
      action: "use-cloud",
      cloud: null,
      record: createDraftRecord({
        code: remote.code,
        dirty: false,
        baseRevision: remote.revision,
        cloudUpdatedAt: remote.updatedAt,
      }),
    };
  }
  if (!local.dirty) {
    if (!remote) {
      if (local.baseRevision > 0) {
        return {
          action: "keep-local",
          cloud: null,
          record: { ...local, dirty: true, baseRevision: 0, conflict: false },
        };
      }
      return { action: "keep-local", record: local, cloud: null };
    }
    if (local.baseRevision !== remote.revision) {
      return {
        action: "use-cloud",
        cloud: null,
        record: createDraftRecord({
          code: remote.code,
          dirty: false,
          baseRevision: remote.revision,
          cloudUpdatedAt: remote.updatedAt,
        }),
      };
    }
    return { action: "keep-local", record: { ...local, conflict: false }, cloud: null };
  }
  if (!remote) {
    return { action: "keep-local", record: local, cloud: null };
  }
  if (remote.revision === local.baseRevision) {
    return { action: "keep-local", record: { ...local, conflict: false }, cloud: null };
  }
  if (local.code === remote.code) {
    return {
      action: "adopt-cloud-revision",
      cloud: null,
      record: createDraftRecord({
        code: remote.code,
        dirty: false,
        baseRevision: remote.revision,
        cloudUpdatedAt: remote.updatedAt,
        localUpdatedAt: local.localUpdatedAt,
      }),
    };
  }
  return {
    action: "conflict",
    record: { ...local, conflict: true },
    cloud: remote,
  };
}

export function shouldKeepaliveSync(serializedBody, maxBytes = KEEPALIVE_MAX_BYTES) {
  if (typeof serializedBody !== "string") return false;
  const bytes = new TextEncoder().encode(serializedBody).length;
  return bytes > 0 && bytes <= maxBytes;
}

export function deriveDraftStatus({ storageError, conflict, syncing, syncFailed, record }) {
  if (storageError) return "Local draft save unavailable";
  if (conflict) return "Cloud draft conflict";
  if (syncing) return "Sync pending";
  if (syncFailed && record) return "Sync unavailable";
  if (record?.dirty) return "Saved locally";
  if (record && !record.dirty && record.baseRevision > 0) return "Synced to account";
  return "";
}

export function readInitialDraftCode({ userId, contestId, problemId, languageId, storage }) {
  const store = storage || (typeof localStorage !== "undefined" ? localStorage : null);
  if (!store) return null;
  const key = buildDraftKey({ userId, contestId, problemId, languageId });
  if (!key) return null;
  try {
    const record = parseDraftRecord(store.getItem(key));
    if (record) return record.code;
  } catch {
    return null;
  }
  return null;
}
