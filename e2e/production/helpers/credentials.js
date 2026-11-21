import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import process from "node:process";

const DEFAULT_FILE = "e2e/.fixtures/production-live.json";
const ACCOUNT_KEYS = [
  "platformAdmin",
  "organizationOwner",
  "coordinator",
  "unenrolledStudent",
  "collegeBOutsider",
];

function requireString(value, label) {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`Production E2E credentials are missing ${label}.`);
  }
  if (/^<[^>]+>$/.test(value.trim())) {
    throw new Error(`Production E2E credentials still contain the ${label} placeholder.`);
  }
}

function validateAccount(account, label) {
  if (!account || typeof account !== "object") {
    throw new Error(`Production E2E credentials are missing ${label}.`);
  }
  requireString(account.username, `${label}.username`);
  requireString(account.password, `${label}.password`);
}

export function credentialsPath(env = process.env) {
  return resolve(env.PRODUCTION_E2E_CREDENTIALS_FILE || DEFAULT_FILE);
}

export function validateCredentials(config) {
  if (!config || typeof config !== "object") {
    throw new Error("Production E2E credential file must contain a JSON object.");
  }
  for (const key of ACCOUNT_KEYS) validateAccount(config[key], key);
  requireString(config.collegeBOutsider.organizationId, "collegeBOutsider.organizationId");

  if (!Array.isArray(config.students) || config.students.length < 5 || config.students.length > 10) {
    throw new Error("Production E2E requires between 5 and 10 synthetic student accounts.");
  }
  config.students.forEach((student, index) => validateAccount(student, `students[${index}]`));
  const usernames = [
    ...ACCOUNT_KEYS.map((key) => config[key].username),
    ...config.students.map((student) => student.username),
  ].map((value) => value.trim().toLowerCase());
  if (new Set(usernames).size !== usernames.length) {
    throw new Error("Every production E2E role must use an independently authenticated account.");
  }

  const problem = config.problem;
  if (!problem || typeof problem !== "object") throw new Error("Production E2E problem configuration is missing.");
  for (const key of ["id", "title", "language", "correctCode", "incorrectCode"]) {
    requireString(problem[key], `problem.${key}`);
  }
  if (!Number.isInteger(problem.languageId) || problem.languageId < 1 || problem.languageId > 91) {
    throw new Error("Production E2E problem.languageId must be a valid Judge0 language id.");
  }
  return config;
}

export function loadCredentials(env = process.env) {
  const path = credentialsPath(env);
  if (!existsSync(path)) {
    throw new Error(
      "Production E2E credential file is unavailable. Copy the example to the ignored fixture path or set PRODUCTION_E2E_CREDENTIALS_FILE."
    );
  }
  let config;
  try {
    config = JSON.parse(readFileSync(path, "utf8"));
  } catch {
    throw new Error("Production E2E credential file is not valid JSON.");
  }
  return validateCredentials(config);
}

export function secretValues(config) {
  return [
    ...ACCOUNT_KEYS.map((key) => config?.[key]?.password),
    ...(config?.students || []).map((student) => student.password),
  ].filter((value) => typeof value === "string" && value);
}
