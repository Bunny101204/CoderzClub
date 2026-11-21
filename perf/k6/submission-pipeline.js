/**
 * Mode A — mock/admission submission pipeline capacity (enqueue + poll).
 * Not a claim of N simultaneous Judge0 executions.
 *
 *   k6 run -e BASE_URL=http://127.0.0.1:8082 -e VUS=20 -e DURATION=30s perf/k6/submission-pipeline.js
 */
import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend, Counter } from "k6/metrics";
import { assertLoopbackTarget, DEFAULT_BASE_URL } from "./submission-pipeline-guard.js";

const BASE_URL = assertLoopbackTarget(__ENV.BASE_URL || DEFAULT_BASE_URL);
const VUS = Number(__ENV.VUS || 20);
const DURATION = __ENV.DURATION || "30s";
const PASSWORD = __ENV.PERF_PASSWORD;

if (!PASSWORD) {
  throw new Error("PERF_PASSWORD must be supplied through the environment; no default credential is used");
}

const admissionSuccessRate = new Rate("admission_success_rate");
const enqueueLatency = new Trend("enqueue_latency", true);
const admittedJobs = new Counter("admitted_jobs");
const admissionAttempts = new Counter("admission_attempts");
const status202 = new Counter("status_202");
const status409 = new Counter("status_409");
const status429 = new Counter("status_429");
const unexpectedResponses = new Counter("unexpected_responses");
const authenticationFailures = new Counter("authentication_failures");
const transportErrors = new Counter("transport_errors");
const httpStatusCounts = new Counter("http_status_count");

function recordHttpStatus(response) {
  if (response.status > 0) httpStatusCounts.add(1, { status: String(response.status) });
}

export const options = {
  scenarios: {
    enqueue: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
      gracefulStop: "20s",
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<5000"],
    admission_success_rate: ["rate>0.7"],
    enqueue_latency: ["p(95)<5000"],
  },
};

export function setup() {
  // Keep setup_data non-sensitive: credentials and tokens never leave the VU.
  const ping = http.get(`${BASE_URL}/api/test`);
  if (ping.status !== 200) {
    throw new Error(`perf API not ready at ${BASE_URL}/api/test status=${ping.status}`);
  }
  return { apiReady: true };
}

export default function () {
  // One non-retried job per VU bounds generated work to VUS and avoids a
  // constant-vus test creating an unbounded queue or repeated solve attempts.
  if (__ITER > 0) {
    sleep(1);
    return;
  }

  http.setResponseCallback(http.expectedStatuses(200, 201, 202, 401, 403, 409, 429));
  const userIndex = (__VU - 1 + Number(__ENV.PERF_USER_OFFSET || 0)) % 400;
  const perOrg = Math.floor(19990 / 10);
  const orgIndex = Math.min(9, Math.floor(userIndex / perOrg));
  const username = `perf_user_${userIndex}`;
  const orgId = `perf-org-${orgIndex}`;
  const login = http.post(
    `${BASE_URL}/api/login`,
    JSON.stringify({ username, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" }, tags: { name: "login" } }
  );
  recordHttpStatus(login);
  if (login.status !== 200) {
    if (login.status === 401 || login.status === 403) authenticationFailures.add(1);
    else if (login.status === 0) transportErrors.add(1);
    else unexpectedResponses.add(1);
    sleep(1);
    return;
  }
  const token = login.json("token");
  const opKey = `pipe-${__VU}-${__ITER}-${Date.now()}`;
  const headers = {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
    "X-Organization-Id": orgId,
    "X-Idempotency-Key": opKey,
  };
  const started = Date.now();
  admissionAttempts.add(1);
  const submit = http.post(
    `${BASE_URL}/api/submission-jobs`,
    JSON.stringify({
      problemId: `perf-prob-${orgIndex}-0`,
      languageId: 71,
      code: "print(1)\n",
      operationKey: opKey,
    }),
    { headers, tags: { name: "enqueue" } }
  );
  recordHttpStatus(submit);
  enqueueLatency.add(Date.now() - started);
  // Only HTTP 202 is an admitted job.  409/429 are control outcomes, not success.
  const admitted = submit.status === 202;
  admissionSuccessRate.add(admitted);
  if (admitted) {
    admittedJobs.add(1);
    status202.add(1);
  } else if (submit.status === 409) status409.add(1);
  else if (submit.status === 429) status429.add(1);
  else if (submit.status === 0) transportErrors.add(1);
  else unexpectedResponses.add(1);

  let jobId = null;
  try {
    jobId = submit.json("id") || submit.json("jobId");
  } catch (e) {
    jobId = null;
  }
  if (jobId) {
    const status = http.get(`${BASE_URL}/api/submission-jobs/${jobId}`, {
      headers,
      tags: { name: "poll" },
    });
    recordHttpStatus(status);
    if (status.status === 0) transportErrors.add(1);
    check(status, { "poll ok": (r) => r.status === 200 });
  }
  sleep(0.3);
}
