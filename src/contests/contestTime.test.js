import test from "node:test";
import assert from "node:assert/strict";
import {
  browserTimeZone,
  countdownTarget,
  datetimeLocalToIso,
  formatCountdown,
  instantToDatetimeLocal,
  msUntil,
} from "./contestTime.js";
import {
  buildContestPatchBody,
  buildContestWriteBody,
  canDelete,
  canPublish,
  canUnpublish,
  compactContestBody,
  validateContestForm,
} from "./contestLifecycle.js";

test("browser timezone is a non-empty IANA-style string", () => {
  const zone = browserTimeZone();
  assert.equal(typeof zone, "string");
  assert.ok(zone.length > 0);
});

test("datetime-local values convert through Date rather than manual offsets", () => {
  const local = "2026-06-01T13:00";
  const iso = datetimeLocalToIso(local);
  assert.match(iso, /Z$/);
  const roundTrip = instantToDatetimeLocal(iso);
  assert.equal(roundTrip, local);
});

test("countdown formatting and LIVE/UPCOMING targets", () => {
  assert.equal(formatCountdown(0), "0s");
  assert.equal(formatCountdown(2500), "2s");
  assert.equal(formatCountdown(65 * 1000), "1m 5s");
  const upcoming = { status: "UPCOMING", startAt: "2026-06-01T13:00:00Z", endAt: "2026-06-01T15:00:00Z" };
  const live = { status: "LIVE", startAt: "2026-06-01T13:00:00Z", endAt: "2026-06-01T15:00:00Z" };
  assert.equal(countdownTarget(upcoming), upcoming.startAt);
  assert.equal(countdownTarget(live), live.endAt);
  assert.ok(msUntil("2026-06-01T13:00:00Z", Date.parse("2026-06-01T12:00:00Z")) > 0);
});

test("lifecycle actions follow backend-visible status", () => {
  assert.equal(canPublish("DRAFT", false), true);
  assert.equal(canPublish("UPCOMING", true), false);
  assert.equal(canUnpublish("UPCOMING"), true);
  assert.equal(canUnpublish("LIVE"), false);
  assert.equal(canDelete("ENDED"), false);
  assert.equal(canDelete("DRAFT"), true);
});

test("LIVE patch omits immutable fields so title-only saves cannot wipe problems", () => {
  const form = {
    title: "Weekly",
    description: "desc",
    startAt: "2026-06-01T13:00:00.000Z",
    endAt: "2026-06-01T15:00:00.000Z",
    audienceType: "BATCHES",
    batchIds: ["b1"],
    problemIds: ["p1", "p2"],
  };
  const live = buildContestPatchBody(form, "LIVE");
  assert.deepEqual(Object.keys(live).sort(), ["description", "endAt", "title"]);
  assert.equal(Object.hasOwn(live, "published"), false);
  assert.equal(Object.hasOwn(live, "startAt"), false);
  assert.equal(Object.hasOwn(live, "batchIds"), false);
  assert.equal(Object.hasOwn(live, "problemIds"), false);
  assert.equal(Object.hasOwn(live, "audienceType"), false);
  const full = buildContestWriteBody(form);
  assert.deepEqual(full.problemIds, ["p1", "p2"]);
  assert.equal(Object.hasOwn(full, "published"), false);
  assert.equal(validateContestForm({ ...form, endAt: form.startAt }), "end must be after start");
  assert.deepEqual(compactContestBody({ title: "A", startAt: null, published: true, problemIds: ["p1"] }), {
    title: "A",
    problemIds: ["p1"],
  });
});
