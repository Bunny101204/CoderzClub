export const ACTIVE_ORGANIZATION_STORAGE_KEY = "coderzclub-active-organization-id";
export const ORGANIZATION_HEADER = "X-Organization-Id";
export const ORGANIZATION_CHANGED_EVENT = "coderzclub:organization-changed";

export function readStoredOrganizationId(storage = globalThis.localStorage) {
  try {
    const value = storage?.getItem?.(ACTIVE_ORGANIZATION_STORAGE_KEY);
    return typeof value === "string" && value.trim() ? value.trim() : null;
  } catch {
    return null;
  }
}

export function persistOrganizationId(organizationId, storage = globalThis.localStorage) {
  const next = typeof organizationId === "string" && organizationId.trim()
    ? organizationId.trim()
    : null;
  try {
    if (!next) {
      storage?.removeItem?.(ACTIVE_ORGANIZATION_STORAGE_KEY);
    } else {
      storage?.setItem?.(ACTIVE_ORGANIZATION_STORAGE_KEY, next);
    }
  } catch {
    // Ignore quota / private-mode failures; in-memory context still works.
  }
  return next;
}

export function clearStoredOrganizationId(storage = globalThis.localStorage) {
  return persistOrganizationId(null, storage);
}

export function resolveActiveOrganizationId(organizations, preferredId) {
  const list = Array.isArray(organizations) ? organizations : [];
  if (list.length === 0) {
    return null;
  }
  if (preferredId && list.some((org) => org?.id === preferredId)) {
    return preferredId;
  }
  return list[0]?.id || null;
}

export function organizationCacheEpoch(organizationId, generation = 0) {
  return `${organizationId || "platform"}:${generation}`;
}

export function notifyOrganizationChanged(detail = {}) {
  if (typeof window === "undefined" || typeof window.dispatchEvent !== "function") {
    return;
  }
  window.dispatchEvent(new CustomEvent(ORGANIZATION_CHANGED_EVENT, { detail }));
}
