/**
 * Phase 7 local load against cz-pb-staging only (127.0.0.1:18090).
 *
 *   k6 run -e BASE_URL=http://127.0.0.1:18090 -e VUS=100 -e DURATION=2m perf/k6/pb-staging-load.js
 *
 * Proves HTTP login/browse/enqueue admission under concurrency.
 * Does NOT claim N concurrent Judge0 sandboxes.
 */
import http from "k6/http";
import { check, sleep, group } from "k6";
import { Rate, Trend, Counter } from "k6/metrics";
import { assertLocalPbStagingTarget } from "./pb-staging-local-guard.js";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:18090";
const VUS = Number(__ENV.VUS || 100);
const DURATION = __ENV.DURATION || "2m";
const PASSWORD = __ENV.PERF_PASSWORD || "Password1!";
const USER_COUNT = Number(__ENV.PB_LOAD_USER_COUNT || 220);
const ENQUEUE = String(__ENV.PB_LOAD_ENQUEUE || "1") === "1";
const PROBLEM_ID = __ENV.PB_LOAD_PROBLEM_ID || "";

assertLocalPbStagingTarget(BASE_URL);

const loginFail = new Rate("login_fail");
const enqueueFail = new Rate("enqueue_fail");
const loginLatency = new Trend("login_latency", true);
const problemsLatency = new Trend("problems_latency", true);
const bundlesLatency = new Trend("bundles_latency", true);
const enqueueLatency = new Trend("enqueue_latency", true);
const transportErrors = new Counter("transport_errors");

export const options = {
  scenarios: {
    pb_staging_load: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
      gracefulStop: "20s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.15"],
    http_req_duration: ["p(95)<5000"],
    login_fail: ["rate<0.05"],
  },
};

export function setup() {
  const ping = http.get(`${BASE_URL}/api/problems?page=0&size=1`);
  if (ping.status !== 200) {
    throw new Error(`Staging API not ready: status=${ping.status}`);
  }
  let problemId = PROBLEM_ID;
  if (!problemId) {
    try {
      const body = ping.json();
      const rows = body.content || body.problems || (Array.isArray(body) ? body : []);
      problemId = rows[0]?.id || "";
    } catch {
      problemId = "";
    }
  }
  return { problemId };
}

export default function (data) {
  http.setResponseCallback(http.expectedStatuses(200, 201, 202, 400, 401, 403, 404, 409, 429));

  const userIndex = (__VU - 1 + __ITER) % USER_COUNT;
  const username = `pbload_user_${userIndex}`;

  const loginStarted = Date.now();
  const login = http.post(
    `${BASE_URL}/api/login`,
    JSON.stringify({ username, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" }, tags: { name: "login" } }
  );
  loginLatency.add(Date.now() - loginStarted);
  if (login.status === 0) transportErrors.add(1);
  const loginOk = check(login, { "login 200": (r) => r.status === 200 });
  loginFail.add(!loginOk);
  if (!loginOk) {
    sleep(1);
    return;
  }

  let token = null;
  try {
    token = login.json("token");
  } catch {
    loginFail.add(1);
    return;
  }
  const headers = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };

  group("browse", () => {
    const p0 = Date.now();
    const problems = http.get(`${BASE_URL}/api/problems?page=0&size=20`, {
      headers,
      tags: { name: "problems" },
    });
    problemsLatency.add(Date.now() - p0);
    check(problems, { "problems 200": (r) => r.status === 200 });

    const b0 = Date.now();
    const bundles = http.get(`${BASE_URL}/api/bundles`, {
      headers,
      tags: { name: "bundles" },
    });
    bundlesLatency.add(Date.now() - b0);
    check(bundles, { "bundles 200": (r) => r.status === 200 });

    // Hidden-test leak spot-check on first problem if present
    if (data.problemId) {
      const detail = http.get(`${BASE_URL}/api/problems/${data.problemId}`, {
        headers,
        tags: { name: "problem_detail" },
      });
      if (detail.status === 200) {
        const text = detail.body || "";
        check(null, {
          "no hiddenTestCases array content exposed": () =>
            !/"hiddenTestCases"\s*:\s*\[[^\]]*"input"/.test(text),
        });
      }
    }
  });

  if (ENQUEUE && data.problemId) {
    const e0 = Date.now();
    const key = `pb-load-${__VU}-${__ITER}-${Date.now()}`;
    const submit = http.post(
      `${BASE_URL}/api/submission-jobs`,
      JSON.stringify({
        problemId: data.problemId,
        languageId: 71,
        language: "python",
        code: "print(0)\n",
        idempotencyKey: key,
      }),
      { headers, tags: { name: "enqueue" } }
    );
    enqueueLatency.add(Date.now() - e0);
    const ok = check(submit, {
      "enqueue accepted-or-limited": (r) => [202, 200, 429, 409].includes(r.status),
    });
    enqueueFail.add(!ok);
  }

  sleep(0.5);
}
