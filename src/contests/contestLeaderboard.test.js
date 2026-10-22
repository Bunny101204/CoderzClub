import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));

test("contest leaderboard uses paginated contest endpoint and does not poll every second", () => {
  const client = readFileSync(join(root, "../apiClient.js"), "utf8");
  const detail = readFileSync(join(root, "../Components/contests/ContestDetail.jsx"), "utf8");
  const board = readFileSync(join(root, "../Components/contests/ContestLeaderboard.jsx"), "utf8");
  assert.match(client, /getLeaderboard: \(contestId, params\)/);
  assert.match(client, /\/api\/contests\/\$\{contestId\}\/leaderboard/);
  assert.match(detail, /ContestLeaderboard/);
  assert.match(board, /LIVE_REFRESH_MS = 12000/);
  assert.match(board, /setInterval\(\(\) => load\(true\), LIVE_REFRESH_MS\)/);
  assert.doesNotMatch(board, /setInterval\([^)]*,\s*1000\)/);
  assert.match(board, /first accepted submission/);
  assert.match(board, /No time or wrong-attempt penalty/);
  assert.match(board, /contestStatus !== "LIVE" && contestStatus !== "ENDED"/);
  assert.match(board, /useAuth/);
  assert.match(board, /isCurrentUserRow/);
});
