import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { countdownPresentation, createBoundaryRefresh, nextBoundaryRetry } from "./contestBoundary.js";

const detail = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "../Components/contests/ContestDetail.jsx"),
  "utf8"
);

function fakeClock() {
  let now = 0;
  let nextId = 1;
  const timers = [];
  return {
    setTimeout(fn, ms) {
      const id = nextId++;
      timers.push({ id, at: now + ms, fn, cleared: false });
      return id;
    },
    clearTimeout(id) {
      const timer = timers.find((item) => item.id === id);
      if (timer) timer.cleared = true;
    },
    async advance(ms) {
      now += ms;
      const due = timers.filter((timer) => !timer.cleared && timer.at <= now).sort((a, b) => a.at - b.at);
      for (const timer of due) {
        if (timer.cleared) continue;
        timer.cleared = true;
        await timer.fn();
      }
    },
    pending() {
      return timers.filter((timer) => !timer.cleared).length;
    },
  };
}

function harness(responses) {
  const clock = fakeClock();
  const seen = [];
  const statuses = [];
  const errors = [];
  let pending = false;
  let exhausted = false;
  let cursor = 0;
  const refresh = createBoundaryRefresh({
    fetchContest: async () => {
      const next = responses[cursor++];
      if (!next) throw new Error("no scripted response");
      if (next.error) throw next.error;
      return next;
    },
    onContest: (contest) => {
      seen.push(contest);
      statuses.push(contest.status);
    },
    onPendingChange: (value) => {
      pending = value;
    },
    onExhausted: () => {
      exhausted = true;
    },
    onError: (error) => {
      if (error) errors.push(error);
    },
    setTimeout: clock.setTimeout,
    clearTimeout: clock.clearTimeout,
  });
  return {
    clock,
    refresh,
    seen,
    statuses,
    errors,
    get pending() { return pending; },
    get exhausted() { return exhausted; },
  };
}

test("upcoming countdown reaches zero and retries until the server reports LIVE", async () => {
  const upcoming = { id: "c1", status: "UPCOMING", startAt: "2026-10-23T07:34:00.000Z", endAt: "2026-10-23T07:36:00.000Z" };
  const live = { ...upcoming, status: "LIVE", problems: [{ id: "p1", title: "A" }] };
  assert.equal(nextBoundaryRetry({ previousStatus: "UPCOMING", serverStatus: "UPCOMING", attempt: 0 }).action, "retry");
  const ui = harness([upcoming, upcoming, live]);
  assert.equal(ui.refresh.start("UPCOMING"), true);
  assert.equal(ui.refresh.start("UPCOMING"), false);
  await Promise.resolve();
  assert.deepEqual(ui.statuses, ["UPCOMING"]);
  assert.equal(ui.pending, true);
  await ui.clock.advance(1500);
  await Promise.resolve();
  assert.deepEqual(ui.statuses, ["UPCOMING", "UPCOMING"]);
  await ui.clock.advance(3000);
  await Promise.resolve();
  assert.deepEqual(ui.statuses, ["UPCOMING", "UPCOMING", "LIVE"]);
  assert.equal(ui.pending, false);
  assert.equal(ui.exhausted, false);
  assert.equal(ui.clock.pending(), 0);
  assert.equal(countdownPresentation({
    contest: live,
    remaining: 120000,
    text: "2m 0s",
    label: "Ends in",
    target: live.endAt,
    pending: false,
    exhausted: false,
  }).text, "2m 0s");
});

test("a zero countdown does not stay on 0s and does not grant LIVE before the server does", () => {
  const upcoming = { status: "UPCOMING", startAt: "2026-10-23T07:34:00.000Z" };
  const checking = countdownPresentation({
    contest: upcoming,
    remaining: 0,
    text: "0s",
    label: "Starts in",
    target: upcoming.startAt,
    pending: true,
    exhausted: false,
  });
  assert.equal(checking.text, "Checking status…");
  assert.notEqual(checking.text, "0s");
  const waiting = countdownPresentation({
    contest: upcoming,
    remaining: 0,
    text: "0s",
    label: "Starts in",
    target: upcoming.startAt,
    pending: false,
    exhausted: true,
  });
  assert.equal(waiting.text, "Waiting for the server");
  assert.equal(upcoming.status, "UPCOMING");
});

test("live contest reaches its end and the server ENDED response stops retries", async () => {
  const live = { id: "c1", status: "LIVE", endAt: "2026-10-23T07:36:00.000Z" };
  const ended = { ...live, status: "ENDED" };
  const ui = harness([live, ended]);
  assert.equal(ui.refresh.start("LIVE"), true);
  await Promise.resolve();
  assert.deepEqual(ui.statuses, ["LIVE"]);
  await ui.clock.advance(1500);
  await Promise.resolve();
  assert.deepEqual(ui.statuses, ["LIVE", "ENDED"]);
  assert.equal(ui.clock.pending(), 0);
  assert.equal(countdownPresentation({
    contest: ended,
    remaining: null,
    text: "",
    label: "",
    target: null,
    pending: false,
    exhausted: false,
  }), null);
});

test("a temporary API failure retries and then stops without inventing a status", async () => {
  const ui = harness([{ error: new Error("offline") }, { error: new Error("offline") }, { id: "c1", status: "UPCOMING" }, { error: new Error("offline") }]);
  assert.equal(ui.refresh.start("UPCOMING"), true);
  await Promise.resolve();
  assert.equal(ui.errors.length, 1);
  assert.deepEqual(ui.statuses, []);
  await ui.clock.advance(1500);
  await Promise.resolve();
  await ui.clock.advance(3000);
  await Promise.resolve();
  assert.deepEqual(ui.statuses, ["UPCOMING"]);
  await ui.clock.advance(5000);
  await Promise.resolve();
  assert.equal(ui.exhausted, true);
  assert.equal(ui.pending, false);
  assert.equal(ui.clock.pending(), 0);
  assert.ok(ui.statuses.every((status) => status !== "LIVE"));
});

test("dispose clears the pending retry and contest changes do not stack timers", async () => {
  const ui = harness([{ id: "c1", status: "UPCOMING" }, { id: "c1", status: "LIVE" }]);
  ui.refresh.start("UPCOMING");
  await Promise.resolve();
  ui.refresh.dispose();
  await ui.clock.advance(10000);
  assert.deepEqual(ui.statuses, ["UPCOMING"]);
  assert.equal(ui.clock.pending(), 0);
  assert.equal(ui.refresh.start("UPCOMING"), false);
});

test("contest detail keeps server status for problems and cleans up the refresher", () => {
  assert.match(detail, /const showProblems = contest\.status === "LIVE" \|\| contest\.status === "ENDED"/);
  assert.match(detail, /refresh\.dispose\(\)/);
  assert.match(detail, /boundaryRef\.current\?\.start\(contestStatusRef\.current\)/);
  assert.doesNotMatch(detail, /status:\s*["']LIVE["']/);
});
