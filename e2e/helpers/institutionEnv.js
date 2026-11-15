import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const envPath = join(here, "../.fixtures/institution-two-org.env");
const fixturePath = join(here, "../.fixtures/institution-two-org.json");

export function loadInstitutionEnv() {
  if (!existsSync(envPath)) {
    return false;
  }
  for (const line of readFileSync(envPath, "utf8").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eq = trimmed.indexOf("=");
    if (eq <= 0) continue;
    const key = trimmed.slice(0, eq);
    const value = trimmed.slice(eq + 1);
    // Fixture file is authoritative for local E2E; overwrite stale shell exports.
    process.env[key] = value;
  }
  return true;
}

/** Opt-in for the six live two-organization tests. Default Playwright and CI leave this unset. */
export function liveInstitutionE2EEnabled() {
  return process.env.INSTITUTION_E2E_ENABLED === "true";
}

export const REQUIRED_INSTITUTION_FIXTURE_KEYS = [
  "E2E_ORG_OWNER_USER",
  "E2E_ORG_OWNER_PASS",
  "E2E_ORG_A_ID",
  "E2E_ORG_B_ID",
  "E2E_ORG_B_OWNER_USER",
  "E2E_ORG_B_OWNER_PASS",
  "E2E_INSTRUCTOR_A_USER",
  "E2E_INSTRUCTOR_A_PASS",
  "E2E_INSTRUCTOR_A_ID",
  "E2E_STUDENT_A1_USER",
  "E2E_STUDENT_A1_PASS",
  "E2E_STUDENT_A2_USER",
  "E2E_STUDENT_A2_PASS",
  "E2E_STUDENT_B1_USER",
  "E2E_STUDENT_B1_PASS",
  "E2E_MULTI_USER",
  "E2E_MULTI_PASS",
  "E2E_PROBLEM_ORG_A_TITLE",
  "E2E_PROBLEM_ORG_A_ID",
];

/** Vite dev server proxies browser /api calls here. API helpers use apiBase(), which defaults to the same port. */
export const VITE_API_PROXY = "http://127.0.0.1:8080";

export function missingInstitutionFixtureKeys(env = process.env) {
  return REQUIRED_INSTITUTION_FIXTURE_KEYS.filter((key) => !String(env[key] || "").trim());
}

/**
 * Fixture keys are present only after the explicit opt-in.
 * A local seed file alone is not enough: the previous check treated that file as ready
 * and then the suite failed when the backend was down.
 */
export function institutionFixturesReady() {
  if (!liveInstitutionE2EEnabled()) return false;
  loadInstitutionEnv();
  return missingInstitutionFixtureKeys().length === 0;
}

export function institutionPrerequisiteMessage({ enabled, missingKeys = [], unreachable = [] }) {
  if (!enabled) {
    return "Set INSTITUTION_E2E_ENABLED=true to run the live two-organization institution workflow. Seed with: node scripts/institution-e2e/seed.mjs";
  }
  if (missingKeys.length) {
    return `Live institution E2E is enabled but fixture configuration is incomplete (${missingKeys.join(", ")}). Run: node scripts/institution-e2e/seed.mjs and start the backend against coderzclub_institution_e2e.`;
  }
  if (unreachable.length) {
    return `Live institution E2E is enabled but the backend is not reachable: ${unreachable.join("; ")}. Start the API before Playwright. The Vite dev server proxies /api to http://localhost:8080.`;
  }
  return null;
}

export async function probeBackend(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 4000);
  try {
    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: "{}",
      signal: controller.signal,
    });
    return { ok: true, status: response.status };
  } catch (error) {
    const code = error?.cause?.code || error?.code || error?.name || "unavailable";
    return { ok: false, error: String(code) };
  } finally {
    clearTimeout(timer);
  }
}

/** Null when the opted-in suite may run. Otherwise an actionable failure message. */
export async function liveInstitutionPrerequisiteError() {
  if (!liveInstitutionE2EEnabled()) {
    return institutionPrerequisiteMessage({ enabled: false });
  }
  loadInstitutionEnv();
  const missingKeys = missingInstitutionFixtureKeys();
  if (missingKeys.length) {
    return institutionPrerequisiteMessage({ enabled: true, missingKeys });
  }
  const targets = new Set([apiBase().replace(/\/$/, ""), VITE_API_PROXY]);
  const unreachable = [];
  for (const base of targets) {
    const probe = await probeBackend(`${base}/api/login`);
    if (!probe.ok) unreachable.push(`${base} (${probe.error})`);
  }
  return institutionPrerequisiteMessage({ enabled: true, missingKeys: [], unreachable });
}

export function readInstitutionFixture() {
  if (!existsSync(fixturePath)) return null;
  return JSON.parse(readFileSync(fixturePath, "utf8"));
}

export function apiBase() {
  return process.env.E2E_API_BASE || "http://127.0.0.1:8080";
}
