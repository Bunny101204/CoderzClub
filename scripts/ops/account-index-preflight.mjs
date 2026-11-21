#!/usr/bin/env node
/**
 * Read-only account index / collision preflight (mongosh-backed).
 *
 * Does NOT create indexes, modify users, or write to the database.
 *
 * Usage:
 *   node scripts/ops/account-index-preflight.mjs
 *   MONGODB_URI=mongodb://127.0.0.1:27017 MONGODB_DB=coderzclub_institution_e2e node scripts/ops/account-index-preflight.mjs
 *
 * Exit 0 = no blockers; exit 1 = collisions or missing expected indexes; exit 2 = tooling error.
 */
import { spawnSync } from "node:child_process";
import { writeFileSync, unlinkSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const uri = process.env.MONGODB_URI || process.env.SPRING_DATA_MONGODB_URI || "mongodb://127.0.0.1:27017";
const dbName =
  process.env.MONGODB_DB ||
  process.env.SPRING_DATA_MONGODB_DATABASE ||
  inferDbName(uri) ||
  "coderzclub";

function inferDbName(connectionUri) {
  try {
    const u = new URL(connectionUri);
    const path = (u.pathname || "").replace(/^\//, "");
    return path.split("?")[0] || null;
  } catch {
    return null;
  }
}

function safeHost(connectionUri) {
  try {
    const u = new URL(connectionUri);
    return `${u.protocol}//${u.hostname}:${u.port || ""}`;
  } catch {
    return "(unparsed)";
  }
}

const scanJs = `
const dbName = ${JSON.stringify(dbName)};
const expected = ["username", "email"];
const dbx = db.getSiblingDB(dbName);
const indexes = dbx.users.getIndexes();
const uniqueFields = [];
indexes.forEach((idx) => {
  if (!idx.unique) return;
  Object.keys(idx.key || {}).forEach((k) => uniqueFields.push(k));
});
const missingUnique = expected.filter((f) => !uniqueFields.includes(f));

const usernameFold = {};
const emailFold = {};
const usernameWhitespace = [];
const emailNotLower = [];
let scanned = 0;
dbx.users.find({}, { username: 1, email: 1 }).forEach((doc) => {
  scanned += 1;
  const uname = doc.username;
  const email = doc.email;
  if (typeof uname === "string" && uname !== uname.trim()) {
    usernameWhitespace.push({ id: String(doc._id), username: uname });
  }
  if (typeof email === "string" && email !== email.trim().toLowerCase()) {
    emailNotLower.push({ id: String(doc._id), email: email });
  }
  if (typeof uname === "string" && uname.trim()) {
    const key = uname.trim().toLowerCase();
    if (!usernameFold[key]) usernameFold[key] = [];
    usernameFold[key].push({ id: String(doc._id), username: uname });
  }
  if (typeof email === "string" && email.trim()) {
    const key = email.trim().toLowerCase();
    if (!emailFold[key]) emailFold[key] = [];
    emailFold[key].push({ id: String(doc._id), email: email });
  }
});

function collisions(map) {
  return Object.keys(map)
    .map((k) => map[k])
    .filter((g) => g.length > 1);
}

const usernameCollisions = collisions(usernameFold);
const emailCollisions = collisions(emailFold);
const total = dbx.users.countDocuments({});

print(JSON.stringify({
  database: dbName,
  indexes: { names: indexes.map((i) => i.name), uniqueFields: uniqueFields, missingExpectedUnique: missingUnique },
  scanned: scanned,
  totalDocuments: total,
  usernameLeadingTrailingWhitespace: usernameWhitespace.length,
  emailNotNormalizedLowercase: emailNotLower.length,
  caseInsensitiveUsernameCollisions: usernameCollisions.length,
  caseInsensitiveEmailCollisions: emailCollisions.length,
  samples: {
    usernameWhitespace: usernameWhitespace.slice(0, 5),
    emailNotLower: emailNotLower.slice(0, 5),
    usernameCollisions: usernameCollisions.slice(0, 5),
    emailCollisions: emailCollisions.slice(0, 5)
  }
}));
`;

const dir = mkdtempSync(join(tmpdir(), "cc-preflight-"));
const scriptPath = join(dir, "scan.js");
writeFileSync(scriptPath, scanJs, "utf8");

console.log(JSON.stringify({ mode: "read-only", uriHost: safeHost(uri), database: dbName }, null, 2));

const result = spawnSync("mongosh", ["--quiet", uri, "--file", scriptPath], {
  encoding: "utf8",
  maxBuffer: 32 * 1024 * 1024,
});

try {
  unlinkSync(scriptPath);
} catch {
  /* ignore */
}

if (result.status !== 0) {
  console.error("preflight_failed", result.stderr || result.stdout || result.error);
  process.exit(2);
}

const lines = (result.stdout || "").trim().split(/\r?\n/).filter(Boolean);
const payloadLine = lines[lines.length - 1];
let report;
try {
  report = JSON.parse(payloadLine);
} catch (err) {
  console.error("preflight_parse_failed", payloadLine);
  process.exit(2);
}

console.log("\n## Indexes");
console.log(JSON.stringify(report.indexes, null, 2));
console.log("\n## Normalization / collision scan");
console.log(
  JSON.stringify(
    {
      scanned: report.scanned,
      totalDocuments: report.totalDocuments,
      usernameLeadingTrailingWhitespace: report.usernameLeadingTrailingWhitespace,
      emailNotNormalizedLowercase: report.emailNotNormalizedLowercase,
      caseInsensitiveUsernameCollisions: report.caseInsensitiveUsernameCollisions,
      caseInsensitiveEmailCollisions: report.caseInsensitiveEmailCollisions,
      samples: report.samples,
    },
    null,
    2
  )
);

const blockers = [];
if ((report.indexes.missingExpectedUnique || []).length) {
  blockers.push(`Missing unique indexes on: ${report.indexes.missingExpectedUnique.join(", ")}`);
}
if (report.caseInsensitiveUsernameCollisions > 0) {
  blockers.push(
    `${report.caseInsensitiveUsernameCollisions} case-insensitive username collision group(s) — unique index create/migrate unsafe until resolved`
  );
}
if (report.caseInsensitiveEmailCollisions > 0) {
  blockers.push(
    `${report.caseInsensitiveEmailCollisions} case-insensitive email collision group(s) — unique index create/migrate unsafe until resolved`
  );
}

console.log("\n## Verdict");
if (blockers.length === 0) {
  console.log(
    JSON.stringify(
      {
        ok: true,
        note:
          report.emailNotNormalizedLowercase || report.usernameLeadingTrailingWhitespace
            ? "No collision blockers; legacy unnormalized rows may still need login fallback until rewritten."
            : "Indexes and collision scan look clear for this database.",
      },
      null,
      2
    )
  );
  process.exit(0);
}

console.log(JSON.stringify({ ok: false, blockers }, null, 2));
process.exit(1);
