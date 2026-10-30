import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));

test("contest student and admin routes are registered", () => {
  const app = readFileSync(join(root, "../App.jsx"), "utf8");
  assert.match(app, /path="\/contests"/);
  assert.match(app, /path="\/contests\/:id"/);
  assert.match(app, /path="\/contests\/:contestId\/problems\/:problemId"/);
  assert.match(app, /path="\/admin\/contests\/new"/);
  assert.match(app, /path="\/admin\/contests\/:id"/);
  const header = readFileSync(join(root, "../Components/Header.jsx"), "utf8");
  const nav = readFileSync(join(root, "../navigation/mainNav.js"), "utf8");
  assert.match(nav, /to: "\/contests"/);
  assert.match(header, /navigationItems/);
  const client = readFileSync(join(root, "../apiClient.js"), "utf8");
  assert.match(client, /adminContests:/);
  assert.match(client, /contests:/);
  assert.match(client, /submit: \(contestId, problemId, payload, operationId\)/);
  assert.match(client, /getLeaderboard: \(contestId, params\)/);
  assert.match(client, /\/api\/contests\/\$\{contestId\}\/problems\/\$\{problemId\}\/submission-jobs/);
  assert.match(app, /isAuthenticated \? /);
  assert.match(app, /user\.role === "ADMIN"/);
  assert.match(client, /isSafeBusinessMessage/);
  assert.match(client, /data\?\.error/);
});
