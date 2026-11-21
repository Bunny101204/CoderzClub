/**
 * Deterministic ~20k-user institutional seed for the isolated perf Mongo only.
 *
 *   node scripts/perf/seed-institutional.mjs
 *   node scripts/perf/seed-institutional.mjs --cleanup
 *
 * Default URI: mongodb://127.0.0.1:27027  DB: coderzclub_perf
 * Never targets developer 27017 or Atlas.
 */
import { spawnSync } from "node:child_process";
import { writeFileSync, unlinkSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const MONGO_URI = process.env.PERF_MONGO_URI || "mongodb://127.0.0.1:27027";
const DB = process.env.PERF_MONGO_DB || "coderzclub_perf";
const TARGET_ENV = process.env.PERF_TARGET_ENV || "local";
const SEED_VERSION = "perf-v6-20k";
const ORG_COUNT = Number(process.env.PERF_ORG_COUNT || 10);
const USERS_TOTAL = Number(process.env.PERF_USERS_TOTAL || 20000);
const PASSWORD_HASH =
  process.env.E2E_PASSWORD_HASH ||
  "$2b$10$F1pyWj8QaeBYPR3O56qhs.leCoxbmi6aW7iQHhSkqk3hyMTKHpa4O"; // Password1!
const cleanupOnly = process.argv.includes("--cleanup");
const REDACTED_MONGO_URI = (() => {
  try {
    const uri = new URL(MONGO_URI);
    uri.username = "";
    uri.password = "";
    return uri.toString();
  } catch {
    return "<redacted>";
  }
})();

if (TARGET_ENV === "staging") {
  if (DB !== "coderzclub_staging") {
    console.error("Refusing staging seed: PERF_MONGO_DB must be coderzclub_staging.");
    process.exit(1);
  }
  if (/(atlas|production|prod[-_.]|staging\.coderzclub\.site|gcp)/i.test(MONGO_URI)) {
    console.error("Refusing staging seed: Mongo URI looks production/cloud-attached.");
    process.exit(1);
  }
} else if (!MONGO_URI.includes("27027") && process.env.PERF_ALLOW_NON_ISOLATED !== "1") {
  console.error("Refusing to seed: PERF_MONGO_URI must target isolated port 27027 (or set PERF_ALLOW_NON_ISOLATED=1).");
  process.exit(2);
}

const outDir = process.env.PERF_FIXTURE_DIR || join(__dirname, "../../perf/.fixtures");
mkdirSync(outDir, { recursive: true });
const scriptPath = join(outDir, `_seed_${Date.now()}.js`);

const mongoBody = `
const uriHint = ${JSON.stringify(REDACTED_MONGO_URI)};
const dbName = ${JSON.stringify(DB)};
const seedVersion = ${JSON.stringify(SEED_VERSION)};
const orgCount = ${ORG_COUNT};
const usersTotal = ${USERS_TOTAL};
const passwordHash = ${JSON.stringify(PASSWORD_HASH)};
const cleanupOnly = ${cleanupOnly ? "true" : "false"};
const target = db.getSiblingDB(dbName);

function cleanupPerf() {
  const collections = [
    "users", "organizations", "organization_members", "batches", "batch_members",
    "batch_coordinators", "batch_memberships",
    "problems", "contests", "contest_registrations", "contest_scores", "submissions", "user_stats",
    "perf_seed_meta"
  ];
  for (const name of collections) {
    target[name].drop();
  }
  print(JSON.stringify({ cleaned: true, database: dbName }));
}

if (cleanupOnly) {
  cleanupPerf();
  quit(0);
}

const existing = target.perf_seed_meta.findOne({ _id: "meta" });
if (existing && existing.version === seedVersion && existing.users === usersTotal && existing.orgs === orgCount) {
  print(JSON.stringify({ skipped: true, reason: "already-seeded", version: seedVersion, users: usersTotal, orgs: orgCount }));
  quit(0);
}

cleanupPerf();

const now = new Date();
const orgs = [];
const members = [];
const batches = [];
const batchMembers = [];
const batchCoordinators = [];
const problems = [];
const contests = [];
const contestScores = [];
const users = [];
const submissions = [];

for (let o = 0; o < orgCount; o++) {
  const orgId = "perf-org-" + o;
  orgs.push({
    _id: orgId,
    slug: "perf-org-" + o,
    name: "Perf College " + o,
    displayName: "Perf College " + o,
    status: "ACTIVE",
    branding: { displayName: "Perf College " + o, primaryColor: "#0F766E", secondaryColor: "#134E4A" },
    createdAt: now,
    updatedAt: now
  });
  const ownerId = "perf-owner-" + o;
  users.push({
    _id: ownerId,
    username: "perf_owner_" + o,
    email: "perf_owner_" + o + "@perf.local",
    passwordHash: passwordHash,
    role: "USER",
    emailVerified: true,
    accountStatus: "ACTIVE",
    profileComplete: true,
    totalPoints: 0,
    problemsSolved: 0,
    solvedProblemIds: [],
    createdAt: now
  });
  members.push({
    _id: "perf-mem-owner-" + o,
    organizationId: orgId,
    userId: ownerId,
    roles: ["ORG_OWNER"],
    status: "ACTIVE",
    addedAt: now
  });
  const batchId = "perf-batch-" + o;
  batches.push({
    _id: batchId,
    name: "Perf Batch " + o,
    description: "Synthetic institutional batch",
    organizationId: orgId,
    active: true,
    createdBy: ownerId,
    createdAt: now,
    updatedAt: now
  });
  for (let p = 0; p < 5; p++) {
    const problemId = "perf-prob-" + o + "-" + p;
    problems.push({
      _id: problemId,
      numericId: (o * 10) + p + 1,
      title: "Perf Problem " + o + "-" + p,
      difficulty: p % 2 === 0 ? "EASY" : "MEDIUM",
      availability: p === 0 ? "PUBLIC" : "CONTEST_ONLY",
      organizationId: p === 0 ? null : orgId,
      statement: "Synthetic perf problem",
      executionMode: "STANDARD_PER_CASE",
      publicTestCases: [{ input: "1", expectedOutput: "1" }],
      hiddenTestCases: [{ input: "2", expectedOutput: "2" }],
      points: 100,
      createdBy: ownerId,
      createdAt: now
    });
  }
  const contestId = "perf-contest-" + o;
  contests.push({
    _id: contestId,
    title: "Perf Contest " + o,
    description: "Synthetic live contest",
    organizationId: orgId,
    published: true,
    audienceType: "BATCHES",
    batchIds: [batchId],
    problemIds: ["perf-prob-" + o + "-1", "perf-prob-" + o + "-2"],
    startAt: new Date(now.getTime() - 3600000),
    endAt: new Date(now.getTime() + 86400000),
    createdBy: ownerId,
    createdAt: now,
    updatedAt: now
  });
}

const ownersReserved = orgCount;
const remaining = Math.max(0, usersTotal - ownersReserved);
const perOrg = Math.floor(remaining / orgCount);
let userSeq = 0;

for (let o = 0; o < orgCount; o++) {
  const orgId = "perf-org-" + o;
  const batchId = "perf-batch-" + o;
  const contestId = "perf-contest-" + o;
  const count = o === orgCount - 1 ? remaining - perOrg * (orgCount - 1) : perOrg;
  for (let i = 0; i < count; i++) {
    const id = "perf-user-" + userSeq;
    const username = "perf_user_" + userSeq;
    const isInstructor = i < 3;
    users.push({
      _id: id,
      username: username,
      email: username + "@perf.local",
      passwordHash: passwordHash,
      role: "USER",
      emailVerified: true,
      accountStatus: "ACTIVE",
      profileComplete: true,
      totalPoints: (userSeq % 50) * 10,
      problemsSolved: userSeq % 7,
      solvedProblemIds: [],
      createdAt: now
    });
    members.push({
      _id: "perf-mem-" + userSeq,
      organizationId: orgId,
      userId: id,
      roles: isInstructor ? ["INSTRUCTOR"] : ["STUDENT"],
      status: "ACTIVE",
      addedAt: now
    });
    batchMembers.push({
      _id: "perf-bm-" + userSeq,
      batchId: batchId,
      userId: id,
      addedAt: now
    });
    if (isInstructor) {
      batchCoordinators.push({
        _id: "perf-coord-" + userSeq,
        batchId: batchId,
        userId: id,
        institution: "Perf College " + o,
        addedAt: now,
        addedBy: "perf-owner-" + o
      });
    }
    if (!isInstructor && userSeq % 5 === 0) {
      contestScores.push({
        _id: "perf-score-" + userSeq,
        contestId: contestId,
        userId: id,
        organizationId: orgId,
        totalPoints: (userSeq % 10) * 100,
        solvedCount: userSeq % 3,
        createdAt: now,
        updatedAt: now
      });
    }
    if (userSeq % 20 === 0) {
      submissions.push({
        _id: "perf-sub-" + userSeq,
        userId: id,
        problemId: "perf-prob-" + o + "-0",
        organizationId: orgId,
        language: "Python",
        languageId: 71,
        code: "print(1)\\n",
        result: "ACCEPTED",
        verdict: "ACCEPTED",
        passedTestCases: 2,
        totalTestCases: 2,
        createdAt: now
      });
    }
    userSeq++;
  }
}

function insertBatched(coll, docs, batchSize) {
  for (let i = 0; i < docs.length; i += batchSize) {
    const slice = docs.slice(i, i + batchSize);
    if (slice.length) coll.insertMany(slice, { ordered: false });
  }
}

insertBatched(target.organizations, orgs, 100);
insertBatched(target.users, users, 500);
insertBatched(target.organization_members, members, 500);
insertBatched(target.batches, batches, 100);
insertBatched(target.batch_members, batchMembers, 500);
insertBatched(target.batch_coordinators, batchCoordinators, 200);
insertBatched(target.problems, problems, 100);
insertBatched(target.contests, contests, 100);
insertBatched(target.contest_scores, contestScores, 500);
insertBatched(target.submissions, submissions, 500);

// Use Spring Data @Indexed names so startup auto-index creation does not conflict.
target.users.createIndex({ username: 1 }, { unique: true, name: "username" });
target.users.createIndex({ email: 1 }, { unique: true, name: "email" });
target.organization_members.createIndex(
  { organizationId: 1, userId: 1 },
  { unique: true, name: "organizationId_userId_unique_idx" }
);
target.organization_members.createIndex(
  { userId: 1, organizationId: 1 },
  { name: "userId_organizationId_idx" }
);
target.organizations.createIndex({ slug: 1 }, { unique: true, name: "slug_unique_idx" });
target.organizations.createIndex({ status: 1, name: 1 }, { name: "status_name_idx" });
target.batch_members.createIndex(
  { batchId: 1, userId: 1 },
  { unique: true, name: "batchId_userId_unique_idx" }
);
target.batch_members.createIndex({ userId: 1, batchId: 1 }, { name: "userId_batchId_idx" });
target.batch_coordinators.createIndex(
  { batchId: 1, userId: 1 },
  { unique: true, name: "batchId_userId_unique_idx" }
);
target.batch_coordinators.createIndex({ userId: 1 }, { name: "userId" });
target.problems.createIndex({ numericId: 1 }, { unique: true, name: "numericId_unique_idx" });
target.contests.createIndex({ createdAt: -1 }, { name: "createdAt_desc_idx" });
target.contests.createIndex(
  { published: 1, audienceType: 1, batchIds: 1, startAt: 1 },
  { name: "published_audienceType_batchIds_startAt_idx" }
);
target.contests.createIndex({ published: 1, problemIds: 1 }, { name: "published_problemIds_idx" });
target.contest_scores.createIndex(
  { contestId: 1, userId: 1 },
  { unique: true, name: "contestId_userId_unique_idx" }
);
target.contest_scores.createIndex(
  { contestId: 1, totalPoints: -1, solvedCount: -1, userId: 1 },
  { name: "contestId_totalPoints_solvedCount_userId_idx" }
);
target.submissions.createIndex({ userId: 1, createdAt: -1 }, { name: "userId_createdAt_desc_idx" });

target.perf_seed_meta.insertOne({
  _id: "meta",
  version: seedVersion,
  users: target.users.countDocuments(),
  usersTotal: target.users.countDocuments(),
  orgs: target.organizations.countDocuments(),
  members: target.organization_members.countDocuments(),
  batches: target.batches.countDocuments(),
  batchMembers: target.batch_members.countDocuments(),
  problems: target.problems.countDocuments(),
  contests: target.contests.countDocuments(),
  contestScores: target.contest_scores.countDocuments(),
  submissions: target.submissions.countDocuments(),
  createdAt: now,
  mongoUriHint: uriHint
});

print(JSON.stringify(target.perf_seed_meta.findOne({ _id: "meta" }), null, 2));
`;

writeFileSync(scriptPath, mongoBody, "utf8");
const result = spawnSync("mongosh", ["--quiet", MONGO_URI, "--file", scriptPath], {
  encoding: "utf8",
  maxBuffer: 20 * 1024 * 1024,
});
try {
  unlinkSync(scriptPath);
} catch {
  /* ignore */
}

if (result.stdout) process.stdout.write(result.stdout);
if (result.stderr) process.stderr.write(result.stderr);
if (result.status !== 0) {
  process.exit(result.status || 1);
}

const fixture = {
  mongoUri: REDACTED_MONGO_URI,
  database: DB,
  seedVersion: SEED_VERSION,
  orgCount: ORG_COUNT,
  usersTotal: USERS_TOTAL,
  sampleLogin: { username: "perf_owner_0" },
  sampleStudent: { username: "perf_user_0" },
};
writeFileSync(join(outDir, "seed-meta.json"), JSON.stringify(fixture, null, 2));
console.log(`Wrote ${join(outDir, "seed-meta.json")}`);
