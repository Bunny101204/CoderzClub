import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const launcher = readFileSync(new URL("./start-perf-local.ps1", import.meta.url), "utf8");
const verifier = readFileSync(new URL("./verify-perf-local.ps1", import.meta.url), "utf8");

test("perf launcher is dry-run by default and has explicit worker/Judge0 gates", () => {
  assert.match(launcher, /\[switch\]\$Apply/);
  assert.match(launcher, /if \(-not \$Apply\)/);
  assert.match(launcher, /-AllowJudge0Worker/);
  assert.match(launcher, /PERF_JUDGE0_SANDBOX_VERIFIED/);
});

test("perf launcher hard-pins isolated local dependencies and a private vhost", () => {
  for (const text of [launcher, verifier]) {
    assert.match(text, /27027/);
    assert.match(text, /coderzclub_perf/);
    assert.match(text, /6389/);
    assert.match(text, /5682/);
    assert.match(text, /coderzclub-perf/);
    assert.match(text, /2358/);
    assert.doesNotMatch(text, /docker compose .*down -v|FLUSHALL|purge/i);
  }
});

test("shutdown is PID-scoped and does not perform queue or database cleanup", () => {
  const stop = readFileSync(new URL("./stop-perf-local.ps1", import.meta.url), "utf8");
  assert.match(stop, /coderzclub-perf-local-pids/);
  assert.match(stop, /Stop-Process/);
  assert.doesNotMatch(stop, /docker|rabbitmqctl|mongosh|redis-cli|purge|flush/i);
});
