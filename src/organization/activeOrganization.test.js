import test from "node:test";
import assert from "node:assert/strict";
import {
  ACTIVE_ORGANIZATION_STORAGE_KEY,
  clearStoredOrganizationId,
  organizationCacheEpoch,
  persistOrganizationId,
  readStoredOrganizationId,
  resolveActiveOrganizationId,
} from "./activeOrganization.js";
import {
  canAdministerOrganization,
  canManageInstitutionBatches,
  isInstitutionStudent,
} from "./organizationRoles.js";
import {
  DEFAULT_BRANDING,
  brandingCssVariables,
  customDomainReadinessNotes,
  isSafeHexColor,
  isSafeHttpsAsset,
  resolveOrganizationBranding,
} from "./branding.js";

function memoryStorage(seed = {}) {
  const map = new Map(Object.entries(seed));
  return {
    getItem: (key) => (map.has(key) ? map.get(key) : null),
    setItem: (key, value) => { map.set(key, String(value)); },
    removeItem: (key) => { map.delete(key); },
  };
}

test("active organization persistence round-trips and clears", () => {
  const storage = memoryStorage();
  assert.equal(readStoredOrganizationId(storage), null);
  assert.equal(persistOrganizationId(" org-a ", storage), "org-a");
  assert.equal(storage.getItem(ACTIVE_ORGANIZATION_STORAGE_KEY), "org-a");
  assert.equal(readStoredOrganizationId(storage), "org-a");
  clearStoredOrganizationId(storage);
  assert.equal(readStoredOrganizationId(storage), null);
});

test("resolveActiveOrganizationId prefers stored membership then first org", () => {
  const orgs = [{ id: "a" }, { id: "b" }];
  assert.equal(resolveActiveOrganizationId(orgs, "b"), "b");
  assert.equal(resolveActiveOrganizationId(orgs, "missing"), "a");
  assert.equal(resolveActiveOrganizationId([], "a"), null);
});

test("organizationCacheEpoch changes when generation or tenant changes", () => {
  assert.notEqual(organizationCacheEpoch("org-a", 1), organizationCacheEpoch("org-b", 1));
  assert.notEqual(organizationCacheEpoch("org-a", 1), organizationCacheEpoch("org-a", 2));
});

test("organization role helpers never elevate platform ADMIN", () => {
  assert.equal(canAdministerOrganization(["ORG_OWNER"]), true);
  assert.equal(canAdministerOrganization(["ORG_ADMIN"]), true);
  assert.equal(canAdministerOrganization(["INSTRUCTOR"]), false);
  assert.equal(canManageInstitutionBatches(["INSTRUCTOR"]), true);
  assert.equal(isInstitutionStudent(["STUDENT"]), true);
  assert.equal(isInstitutionStudent(["ORG_ADMIN", "STUDENT"]), false);
  assert.equal(canAdministerOrganization(["ADMIN"]), false);
});

test("branding resolves safe assets and falls back to CoderzClub defaults", () => {
  assert.equal(isSafeHttpsAsset("https://cdn.example.com/logo.png"), true);
  assert.equal(isSafeHttpsAsset("javascript:alert(1)"), false);
  assert.equal(isSafeHttpsAsset("http://insecure.example/logo.png"), false);
  assert.equal(isSafeHexColor("#0af"), true);
  assert.equal(isSafeHexColor("red"), false);

  const branded = resolveOrganizationBranding({
    id: "org-1",
    name: "Alpha College",
    branding: {
      displayName: "Alpha Labs",
      logoUrl: "https://cdn.example.com/a.png",
      primaryColor: "#123456",
      faviconUrl: "javascript:bad",
    },
  });
  assert.equal(branded.displayName, "Alpha Labs");
  assert.equal(branded.logoUrl, "https://cdn.example.com/a.png");
  assert.equal(branded.faviconUrl, null);
  assert.equal(branded.primaryColor, "#123456");
  assert.equal(branded.source, "organization");

  const fallback = resolveOrganizationBranding(null);
  assert.equal(fallback.displayName, DEFAULT_BRANDING.displayName);
  assert.deepEqual(brandingCssVariables(fallback)["--org-brand-primary"], DEFAULT_BRANDING.primaryColor);
  assert.ok(customDomainReadinessNotes().some((note) => note.includes("TLS")));
});
