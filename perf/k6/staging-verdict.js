/** Separate, low-volume real Judge0 terminal-verdict test. Explicit approval required. */
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";
import { assertApprovedStagingTarget } from "./staging-target-guard.js";

if (__ENV.REAL_VERDICT_TEST_APPROVED !== "1") {
  throw new Error("Set REAL_VERDICT_TEST_APPROVED=1 only for an approved real Judge0 test");
}
const BASE_URL = assertApprovedStagingTarget(__ENV.BASE_URL);
const VUS = Number(__ENV.VUS || 2);
const DURATION = __ENV.DURATION || "30s";
const PASSWORD = __ENV.PERF_PASSWORD;
if (!PASSWORD) throw new Error("PERF_PASSWORD is required and is never included in setup data");

const accepted = new Rate("verdict_accepted");
const terminal = new Counter("terminal_verdicts");
const transport = new Counter("transport_errors");
const unexpected = new Counter("unexpected_responses");
const verdictLatency = new Trend("terminal_verdict_latency", true);

export const options = {
  scenarios: { verdict: { executor: "constant-vus", vus: VUS, duration: DURATION, gracefulStop: "180s" } },
  thresholds: { http_req_duration: ["p(95)<10000"], terminal_verdict_latency: ["p(95)<180000"], transport_errors: ["count==0"] },
};

export function setup() {
  const response = http.get(`${BASE_URL}/api/test`);
  if (response.status !== 200) throw new Error(`Staging API is not ready: status=${response.status}`);
  return { apiReady: true };
}

export default function () {
  if (__ITER > 0) { sleep(1); return; }
  const userIndex = (__VU - 1 + Number(__ENV.PERF_VERDICT_USER_OFFSET || 0)) % 20000;
  const orgIndex = Math.min(9, Math.floor(userIndex / 1999));
  const login = http.post(`${BASE_URL}/api/login`, JSON.stringify({ username: `perf_user_${userIndex}`, password: PASSWORD }), { headers: { "Content-Type": "application/json" } });
  if (login.status !== 200) { if (login.status === 0) transport.add(1); else unexpected.add(1); return; }
  const token = login.json("token");
  const key = `staging-verdict-${__VU}-${__ITER}-${Date.now()}`;
  const headers = { Authorization: `Bearer ${token}`, "Content-Type": "application/json", "X-Organization-Id": `perf-org-${orgIndex}`, "X-Idempotency-Key": key };
  const started = Date.now();
  const submit = http.post(`${BASE_URL}/api/submission-jobs`, JSON.stringify({ problemId: `perf-prob-${orgIndex}-0`, languageId: 71, code: "print(1)\n", operationKey: key }), { headers });
  if (submit.status !== 202) { if (submit.status === 0) transport.add(1); else unexpected.add(1); return; }
  const jobId = submit.json("jobId") || submit.json("id");
  if (!jobId) { unexpected.add(1); return; }
  let final = null;
  for (let attempt = 0; attempt < 120; attempt += 1) {
    const response = http.get(`${BASE_URL}/api/submission-jobs/${jobId}`, { headers });
    if (response.status === 0) { transport.add(1); return; }
    if (response.status !== 200) { unexpected.add(1); return; }
    const body = response.json();
    if (body.status === "COMPLETED" || body.status === "FAILED") { final = body; break; }
    sleep(1);
  }
  if (!final) { unexpected.add(1); return; }
  terminal.add(1);
  verdictLatency.add(Date.now() - started);
  accepted.add(final.finalResult === "ACCEPTED");
  check(final, { "terminal verdict present": (body) => body.status === "COMPLETED" || body.status === "FAILED" });
}
