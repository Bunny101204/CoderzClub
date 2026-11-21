/** Bounded staging admission test. One submission per VU; no verdict execution is awaited. */
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";
import { assertApprovedStagingTarget } from "./staging-target-guard.js";

const BASE_URL = assertApprovedStagingTarget(__ENV.BASE_URL);
const VUS = Number(__ENV.VUS || 50);
const DURATION = __ENV.DURATION || "30s";
const PASSWORD = __ENV.PERF_PASSWORD;
if (!PASSWORD) throw new Error("PERF_PASSWORD is required and is never included in setup data");

const admittedJobs = new Counter("admitted_jobs");
const statusCounts = new Counter("http_status_count");
const status409 = new Counter("status_409");
const status429 = new Counter("status_429");
const authFailures = new Counter("authentication_failures");
const unexpected = new Counter("unexpected_responses");
const transport = new Counter("transport_errors");
const admissionRate = new Rate("admission_success_rate");
const enqueueLatency = new Trend("enqueue_latency", true);

function record(response) {
  if (response.status > 0) statusCounts.add(1, { status: String(response.status) });
}

export const options = {
  scenarios: { admission: { executor: "constant-vus", vus: VUS, duration: DURATION, gracefulStop: "20s" } },
  thresholds: {
    admission_success_rate: ["rate>0.7"],
    http_req_duration: ["p(95)<5000"],
    enqueue_latency: ["p(95)<5000"],
  },
};

export function setup() {
  const response = http.get(`${BASE_URL}/api/test`);
  if (response.status !== 200) throw new Error(`Staging API is not ready: status=${response.status}`);
  return { apiReady: true };
}

export default function () {
  if (__ITER > 0) { sleep(1); return; }
  http.setResponseCallback(http.expectedStatuses(200, 202, 401, 403, 409, 429));
  const userIndex = (__VU - 1 + Number(__ENV.PERF_USER_OFFSET || 0)) % 20000;
  const orgIndex = Math.min(9, Math.floor(userIndex / 1999));
  const username = `perf_user_${userIndex}`;
  const login = http.post(`${BASE_URL}/api/login`, JSON.stringify({ username, password: PASSWORD }), {
    headers: { "Content-Type": "application/json" }, tags: { name: "login" },
  });
  record(login);
  if (login.status !== 200) {
    if (login.status === 401 || login.status === 403) authFailures.add(1);
    else if (login.status === 0) transport.add(1);
    else unexpected.add(1);
    return;
  }
  const token = login.json("token");
  const key = `staging-admission-${__VU}-${__ITER}-${Date.now()}`;
  const headers = {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
    "X-Organization-Id": `perf-org-${orgIndex}`,
    "X-Idempotency-Key": key,
  };
  const started = Date.now();
  const submit = http.post(`${BASE_URL}/api/submission-jobs`, JSON.stringify({
    problemId: `perf-prob-${orgIndex}-0`, languageId: 71, code: "print(1)\n", operationKey: key,
  }), { headers, tags: { name: "admission" } });
  record(submit);
  enqueueLatency.add(Date.now() - started);
  admissionRate.add(submit.status === 202);
  if (submit.status === 202) admittedJobs.add(1);
  else if (submit.status === 409) status409.add(1);
  else if (submit.status === 429) status429.add(1);
  else if (submit.status === 0) transport.add(1);
  else unexpected.add(1);
}
