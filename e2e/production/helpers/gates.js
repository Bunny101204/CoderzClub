import process from "node:process";

export const PRODUCTION_WEB_ORIGIN = "https://coderzclub.site";
export const PRODUCTION_API_ORIGIN = "https://api.coderzclub.site";
export const INITIAL_STUDENT_CONCURRENCY = 5;

export function productionWriteGate(env = process.env) {
  const reasons = [];
  if (env.PRODUCTION_E2E_MODE !== "write") reasons.push("mode is not write");
  if (env.PRODUCTION_E2E_ENABLED !== "true") reasons.push("production E2E is not enabled");
  if (env.PRODUCTION_E2E_CONFIRM !== "coderzclub.site") reasons.push("production hostname is not confirmed");
  if (!/^(?:write-)?[a-zA-Z0-9][a-zA-Z0-9_-]{5,80}$/.test(env.PRODUCTION_E2E_RUN_ID || "")) {
    reasons.push("run id is missing or invalid");
  }
  return { allowed: reasons.length === 0, reasons };
}

export function assertProductionOrigins(webOrigin, apiOrigin) {
  if (webOrigin !== PRODUCTION_WEB_ORIGIN || apiOrigin !== PRODUCTION_API_ORIGIN) {
    throw new Error("Production E2E origin allowlist check failed.");
  }
}

export function selectedStudents(students, requested = INITIAL_STUDENT_CONCURRENCY) {
  const count = Number(requested);
  if (!Number.isInteger(count) || count < 1 || count > INITIAL_STUDENT_CONCURRENCY) {
    throw new Error(`Initial production submission concurrency must be between 1 and ${INITIAL_STUDENT_CONCURRENCY}.`);
  }
  if (!Array.isArray(students) || students.length < count) {
    throw new Error("Not enough synthetic student accounts are configured.");
  }
  return students.slice(0, count);
}
