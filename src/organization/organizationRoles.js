export const ORG_OWNER = "ORG_OWNER";
export const ORG_ADMIN = "ORG_ADMIN";
export const INSTRUCTOR = "INSTRUCTOR";
export const STUDENT = "STUDENT";

export function normalizeOrgRoles(roles) {
  if (!Array.isArray(roles)) {
    return [];
  }
  return roles
    .map((role) => String(role || "").trim().toUpperCase())
    .filter(Boolean);
}

export function hasAnyOrgRole(roles, allowed) {
  const current = new Set(normalizeOrgRoles(roles));
  return normalizeOrgRoles(allowed).some((role) => current.has(role));
}

export function canAdministerOrganization(roles) {
  return hasAnyOrgRole(roles, [ORG_OWNER, ORG_ADMIN]);
}

export function canManageInstitutionBatches(roles) {
  return hasAnyOrgRole(roles, [ORG_OWNER, ORG_ADMIN, INSTRUCTOR]);
}

export function isInstitutionStudent(roles) {
  const normalized = normalizeOrgRoles(roles);
  if (canAdministerOrganization(normalized) || canManageInstitutionBatches(normalized)) {
    return false;
  }
  return normalized.includes(STUDENT);
}
