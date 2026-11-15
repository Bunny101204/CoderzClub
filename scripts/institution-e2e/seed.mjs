/**
 * Seeds an isolated local Mongo database for two-organization Playwright E2E.
 * Default DB: coderzclub_institution_e2e — never Atlas / production / perf (:27027).
 *
 * Writes gitignored credentials under e2e/.fixtures/*.env (synthetic Password1! hash only).
 * Do not publish that file. Re-running this script drops and recreates the e2e DB only.
 *
 *   node scripts/institution-e2e/seed.mjs
 */
import { spawnSync } from "node:child_process";
import { writeFileSync, mkdirSync, unlinkSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash, randomBytes } from "node:crypto";

const __dirname = dirname(fileURLToPath(import.meta.url));
const MONGO_URI = process.env.E2E_MONGO_URI || "mongodb://127.0.0.1:27017";
const DB = process.env.E2E_MONGO_DB || "coderzclub_institution_e2e";
const PASSWORD = "Password1!";
// bcryptjs hash for Password1! (verified with compareSync). Spring BCrypt accepts $2b$.
const PASSWORD_HASH = process.env.E2E_PASSWORD_HASH
  || "$2b$10$F1pyWj8QaeBYPR3O56qhs.leCoxbmi6aW7iQHhSkqk3hyMTKHpa4O";

const stamp = Date.now().toString(36);
const ids = {
  platformAdmin: `e2e-padmin-${stamp}`,
  ownerA: `e2e-owner-a-${stamp}`,
  ownerB: `e2e-owner-b-${stamp}`,
  adminA2: `e2e-admin-a2-${stamp}`,
  instructorA: `e2e-instr-a-${stamp}`,
  studentA1: `e2e-stu-a1-${stamp}`,
  studentA2: `e2e-stu-a2-${stamp}`,
  studentB1: `e2e-stu-b1-${stamp}`,
  multi: `e2e-multi-${stamp}`,
  outsider: `e2e-out-${stamp}`,
  orgA: `e2e-org-a-${stamp}`,
  orgB: `e2e-org-b-${stamp}`,
  problemPublic: `e2e-prob-public-${stamp}`,
  problemOrgA: `e2e-prob-orga-${stamp}`,
  problemOrgB: `e2e-prob-orgb-${stamp}`,
};

function userDoc(id, username, role = "USER") {
  return {
    _id: id,
    username,
    email: `${username}@e2e.local`,
    passwordHash: PASSWORD_HASH,
    role,
    emailVerified: true,
    accountStatus: "ACTIVE",
    profileComplete: true,
    totalPoints: 0,
    problemsSolved: 0,
    solvedProblemIds: [],
    createdAt: { $date: new Date().toISOString() },
  };
}

const users = [
  userDoc(ids.platformAdmin, `e2e_padmin_${stamp}`, "ADMIN"),
  userDoc(ids.ownerA, `e2e_owner_a_${stamp}`),
  userDoc(ids.ownerB, `e2e_owner_b_${stamp}`),
  userDoc(ids.adminA2, `e2e_admin_a2_${stamp}`),
  userDoc(ids.instructorA, `e2e_instr_a_${stamp}`),
  userDoc(ids.studentA1, `e2e_stu_a1_${stamp}`),
  userDoc(ids.studentA2, `e2e_stu_a2_${stamp}`),
  userDoc(ids.studentB1, `e2e_stu_b1_${stamp}`),
  userDoc(ids.multi, `e2e_multi_${stamp}`),
  userDoc(ids.outsider, `e2e_out_${stamp}`),
];

const nowIso = new Date().toISOString();
const orgs = [
  {
    _id: ids.orgA,
    name: `E2E Alpha College ${stamp}`,
    slug: `e2e-alpha-${stamp}`,
    category: "college",
    status: "ACTIVE",
    createdBy: ids.platformAdmin,
    createdAt: { $date: nowIso },
    updatedAt: { $date: nowIso },
    branding: { displayName: `Alpha E2E ${stamp}` },
    entitlements: {},
  },
  {
    _id: ids.orgB,
    name: `E2E Beta Institute ${stamp}`,
    slug: `e2e-beta-${stamp}`,
    category: "college",
    status: "ACTIVE",
    createdBy: ids.platformAdmin,
    createdAt: { $date: nowIso },
    updatedAt: { $date: nowIso },
    branding: { displayName: `Beta E2E ${stamp}` },
    entitlements: {},
  },
];

function membership(organizationId, userId, roles) {
  return {
    _id: `e2e-mem-${randomBytes(6).toString("hex")}`,
    organizationId,
    userId,
    roles,
    status: "ACTIVE",
    addedAt: { $date: nowIso },
    addedBy: ids.platformAdmin,
  };
}

const memberships = [
  membership(ids.orgA, ids.ownerA, ["ORG_OWNER"]),
  membership(ids.orgB, ids.ownerB, ["ORG_OWNER"]),
  membership(ids.orgA, ids.adminA2, ["ORG_ADMIN"]),
  membership(ids.orgA, ids.instructorA, ["INSTRUCTOR"]),
  membership(ids.orgA, ids.studentA1, ["STUDENT"]),
  membership(ids.orgA, ids.studentA2, ["STUDENT"]),
  membership(ids.orgB, ids.studentB1, ["STUDENT"]),
  membership(ids.orgA, ids.multi, ["STUDENT"]),
  membership(ids.orgB, ids.multi, ["STUDENT"]),
];

const problems = [
  {
    _id: ids.problemPublic,
    numericId: 900001,
    title: `E2E Public Warmup ${stamp}`,
    statement: "Return 1",
    difficulty: "EASY",
    points: 10,
    availability: "PUBLIC",
    executionMode: "STANDARD_PER_CASE",
    publicTestCases: [{ input: "", output: "1", isSample: true }],
    hiddenTestCases: [{ input: "", output: "1" }],
    createdBy: ids.platformAdmin,
    createdAt: { $date: nowIso },
    tags: ["e2e", "public"],
  },
  {
    _id: ids.problemOrgA,
    numericId: 900002,
    title: `E2E OrgA Contest Problem ${stamp}`,
    statement: "Org A private contest problem",
    difficulty: "EASY",
    points: 50,
    availability: "CONTEST_ONLY",
    organizationId: ids.orgA,
    executionMode: "STANDARD_PER_CASE",
    publicTestCases: [{ input: "", output: "2", isSample: true }],
    hiddenTestCases: [{ input: "", output: "2" }],
    createdBy: ids.ownerA,
    createdAt: { $date: nowIso },
    tags: ["e2e", "org-a"],
  },
  {
    _id: ids.problemOrgB,
    numericId: 900003,
    title: `E2E OrgB Contest Problem ${stamp}`,
    statement: "Org B private contest problem",
    difficulty: "EASY",
    points: 50,
    availability: "CONTEST_ONLY",
    organizationId: ids.orgB,
    executionMode: "STANDARD_PER_CASE",
    publicTestCases: [{ input: "", output: "3", isSample: true }],
    hiddenTestCases: [{ input: "", output: "3" }],
    createdBy: ids.ownerB,
    createdAt: { $date: nowIso },
    tags: ["e2e", "org-b"],
  },
];

const fixture = {
  generatedAt: new Date().toISOString(),
  mongoUri: MONGO_URI,
  database: DB,
  password: PASSWORD,
  stamp,
  organizations: {
    a: { id: ids.orgA, slug: orgs[0].slug, name: orgs[0].name, displayName: orgs[0].branding.displayName },
    b: { id: ids.orgB, slug: orgs[1].slug, name: orgs[1].name, displayName: orgs[1].branding.displayName },
  },
  problems: {
    public: { id: ids.problemPublic, title: problems[0].title, numericId: 900001 },
    orgA: { id: ids.problemOrgA, title: problems[1].title, numericId: 900002 },
    orgB: { id: ids.problemOrgB, title: problems[2].title, numericId: 900003 },
  },
  accounts: {
    platformAdmin: { username: users[0].username, password: PASSWORD, id: ids.platformAdmin },
    ownerA: { username: users[1].username, password: PASSWORD, id: ids.ownerA },
    ownerB: { username: users[2].username, password: PASSWORD, id: ids.ownerB },
    adminA2: { username: users[3].username, password: PASSWORD, id: ids.adminA2 },
    instructorA: { username: users[4].username, password: PASSWORD, id: ids.instructorA },
    studentA1: { username: users[5].username, password: PASSWORD, id: ids.studentA1 },
    studentA2: { username: users[6].username, password: PASSWORD, id: ids.studentA2 },
    studentB1: { username: users[7].username, password: PASSWORD, id: ids.studentB1 },
    multi: { username: users[8].username, password: PASSWORD, id: ids.multi },
    outsider: { username: users[9].username, password: PASSWORD, id: ids.outsider },
  },
};

const outDir = join(__dirname, "../../e2e/.fixtures");
mkdirSync(outDir, { recursive: true });
const mongoScript = join(outDir, `_seed_mongo_${stamp}.js`);
writeFileSync(mongoScript, `
const dbName = ${JSON.stringify(DB)};
const target = db.getSiblingDB(dbName);
target.dropDatabase();
const users = ${JSON.stringify(users)};
const orgs = ${JSON.stringify(orgs)};
const memberships = ${JSON.stringify(memberships)};
const problems = ${JSON.stringify(problems)};
target.users.insertMany(users.map(u => ({
  ...u,
  createdAt: new Date(u.createdAt.$date)
})));
target.organizations.insertMany(orgs.map(o => ({
  ...o,
  createdAt: new Date(o.createdAt.$date),
  updatedAt: new Date(o.updatedAt.$date)
})));
target.organization_members.insertMany(memberships.map(m => ({
  ...m,
  addedAt: new Date(m.addedAt.$date)
})));
target.problems.insertMany(problems.map(p => ({
  ...p,
  createdAt: new Date(p.createdAt.$date)
})));
// Spring-compatible unique index names for local e2e only (never run against Atlas/prod).
try { target.users.createIndex({ username: 1 }, { unique: true, name: "username" }); } catch (e) { print("username_index: " + e); }
try { target.users.createIndex({ email: 1 }, { unique: true, name: "email" }); } catch (e) { print("email_index: " + e); }
print(JSON.stringify({
  database: dbName,
  users: target.users.countDocuments(),
  organizations: target.organizations.countDocuments(),
  memberships: target.organization_members.countDocuments(),
  problems: target.problems.countDocuments(),
  userIndexes: target.users.getIndexes().map(i => i.name)
}));
`);

const result = spawnSync("mongosh", ["--quiet", MONGO_URI, "--file", mongoScript], { encoding: "utf8" });
try { unlinkSync(mongoScript); } catch { /* ignore */ }

if (result.status !== 0) {
  console.error(result.stdout || "");
  console.error(result.stderr || "");
  process.exit(result.status || 1);
}

const fixturePath = join(outDir, "institution-two-org.json");
writeFileSync(fixturePath, JSON.stringify(fixture, null, 2));
writeFileSync(join(outDir, "institution-two-org.env"), [
  `# SYNTHETIC LOCAL FIXTURE ONLY — not production. Do not commit or publish.`,
  `# Database: ${DB} on loopback (never Atlas / never perf :27027).`,
  `E2E_MONGO_URI=${MONGO_URI}`,
  `E2E_MONGO_DB=${DB}`,
  `MONGODB_URI=${MONGO_URI}`,
  `SPRING_DATA_MONGODB_DATABASE=${DB}`,
  `JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef`,
  `RABBITMQ_USERNAME=guest`,
  `RABBITMQ_PASSWORD=guest`,
  `SPRING_REDIS_HOST=127.0.0.1`,
  `RABBITMQ_HOST=127.0.0.1`,
  `APP_CORS_ALLOWED_ORIGINS=http://127.0.0.1:4179,http://localhost:4179,http://127.0.0.1:5173,http://localhost:5173`,
  `E2E_API_BASE=http://127.0.0.1:8080`,
  `E2E_ORG_OWNER_USER=${fixture.accounts.ownerA.username}`,
  `E2E_ORG_OWNER_PASS=${PASSWORD}`,
  `E2E_ORG_B_OWNER_USER=${fixture.accounts.ownerB.username}`,
  `E2E_ORG_B_OWNER_PASS=${PASSWORD}`,
  `E2E_ORG_A_ID=${ids.orgA}`,
  `E2E_ORG_B_ID=${ids.orgB}`,
  `E2E_ORG_A_NAME=${fixture.organizations.a.displayName}`,
  `E2E_ORG_B_NAME=${fixture.organizations.b.displayName}`,
  `E2E_PLATFORM_ADMIN_USER=${fixture.accounts.platformAdmin.username}`,
  `E2E_PLATFORM_ADMIN_PASS=${PASSWORD}`,
  `E2E_INSTRUCTOR_A_USER=${fixture.accounts.instructorA.username}`,
  `E2E_INSTRUCTOR_A_PASS=${PASSWORD}`,
  `E2E_INSTRUCTOR_A_ID=${ids.instructorA}`,
  `E2E_STUDENT_A1_USER=${fixture.accounts.studentA1.username}`,
  `E2E_STUDENT_A1_PASS=${PASSWORD}`,
  `E2E_STUDENT_A1_ID=${ids.studentA1}`,
  `E2E_STUDENT_A2_USER=${fixture.accounts.studentA2.username}`,
  `E2E_STUDENT_A2_PASS=${PASSWORD}`,
  `E2E_STUDENT_A2_ID=${ids.studentA2}`,
  `E2E_STUDENT_B1_USER=${fixture.accounts.studentB1.username}`,
  `E2E_STUDENT_B1_PASS=${PASSWORD}`,
  `E2E_MULTI_USER=${fixture.accounts.multi.username}`,
  `E2E_MULTI_PASS=${PASSWORD}`,
  `E2E_OUTSIDER_USER=${fixture.accounts.outsider.username}`,
  `E2E_OUTSIDER_PASS=${PASSWORD}`,
  `E2E_ADMIN_A2_USER=${fixture.accounts.adminA2.username}`,
  `E2E_ADMIN_A2_PASS=${PASSWORD}`,
  `E2E_PROBLEM_PUBLIC_ID=${ids.problemPublic}`,
  `E2E_PROBLEM_ORG_A_ID=${ids.problemOrgA}`,
  `E2E_PROBLEM_ORG_A_TITLE=${problems[1].title}`,
  `E2E_PROBLEM_ORG_B_ID=${ids.problemOrgB}`,
  "",
].join("\n"));

console.log(result.stdout.trim());
console.log(`Wrote ${fixturePath}`);
console.log(`sha ${createHash("sha256").update(JSON.stringify(fixture.accounts)).digest("hex").slice(0, 12)}`);
