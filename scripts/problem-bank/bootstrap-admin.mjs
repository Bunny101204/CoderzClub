#!/usr/bin/env node
/**
 * Disposable staging ADMIN bootstrap for cz-pb-staging only.
 * Inserts one user document into the isolated Mongo DB via docker exec.
 * Does not alter Spring Security and introduces no auth bypass.
 */
import { spawnSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { randomBytes } from "node:crypto";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const project = "cz-pb-staging";
const mongoService = "mongo";
const db = "coderzclub_pb_staging";
const username = process.env.PB_STAGING_ADMIN_USER || "pb_staging_admin";
const password = process.env.PB_STAGING_ADMIN_PASS || `PbSt@ging-${randomBytes(6).toString("hex")}!`;
// bcryptjs hash for Password1! — only used when password is the known demo password.
const KNOWN = {
  "Password1!": "$2b$10$F1pyWj8QaeBYPR3O56qhs.leCoxbmi6aW7iQHhSkqk3hyMTKHpa4O",
};

function run(cmd, args, opts = {}) {
  const result = spawnSync(cmd, args, { cwd: root, encoding: "utf8", shell: false, ...opts });
  if (result.status !== 0) {
    throw new Error((result.stderr || result.stdout || `${cmd} failed`).slice(0, 1000));
  }
  return result;
}

async function hashPassword(pw) {
  if (KNOWN[pw]) return KNOWN[pw];
  // Prefer bcryptjs if installed in repo; else force Password1! for local disposable admin.
  try {
    const bcrypt = await import("bcryptjs");
    return bcrypt.hashSync(pw, 10);
  } catch {
    if (pw !== "Password1!") {
      console.warn("bcryptjs unavailable; forcing disposable password Password1!");
    }
    return KNOWN["Password1!"];
  }
}

async function main() {
  const effectivePassword = KNOWN[password] ? password : "Password1!";
  const passwordHash = await hashPassword(effectivePassword);
  const id = `pb-staging-admin-${Date.now().toString(36)}`;
  const email = `${username}@pb-staging.local`;

  const js = `
const db = db.getSiblingDB('${db}');
const username = '${username}';
const existing = db.users.findOne({ username });
if (existing) {
  db.users.updateOne({ username }, { $set: { role: 'ADMIN', passwordHash: '${passwordHash}', emailVerified: true, accountStatus: 'ACTIVE' } });
  print('UPDATED ' + existing._id);
} else {
  const doc = {
    _id: '${id}',
    username,
    email: '${email}',
    passwordHash: '${passwordHash}',
    role: 'ADMIN',
    emailVerified: true,
    accountStatus: 'ACTIVE',
    profileComplete: true,
    totalPoints: 0,
    problemsSolved: 0,
    solvedProblemIds: [],
    createdAt: new Date()
  };
  db.users.insertOne(doc);
  print('CREATED ' + doc._id);
}
`;

  const container = run("docker", [
    "compose",
    "-p",
    project,
    "-f",
    "docker-compose.pb-staging.yml",
    "ps",
    "-q",
    mongoService,
  ]).stdout.trim();
  if (!container) throw new Error("Mongo container for cz-pb-staging not found");

  // Wait for replica-set primary
  for (let i = 0; i < 40; i += 1) {
    const status = spawnSync(
      "docker",
      ["exec", container, "mongosh", "--quiet", "--eval", "try { rs.status().myState } catch(e) { 0 }"],
      { encoding: "utf8", shell: false }
    );
    if ((status.stdout || "").trim() === "1") break;
    spawnSync(process.execPath, ["-e", "Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0,2000)"], {
      shell: false,
    });
  }

  const inserted = run("docker", ["exec", "-i", container, "mongosh", "--quiet"], {
    input: js,
  });
  console.log((inserted.stdout || "").trim());

  const credPath = resolve(root, ".local/problem-bank/pb-staging-admin.json");
  mkdirSync(dirname(credPath), { recursive: true });
  writeFileSync(
    credPath,
    JSON.stringify(
      {
        username,
        password: effectivePassword,
        email,
        apiBase: "http://127.0.0.1:18090",
        note: "Disposable local staging admin. Never commit. Not a production account.",
      },
      null,
      2
    )
  );
  console.log(`Wrote credentials to ${credPath}`);
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
