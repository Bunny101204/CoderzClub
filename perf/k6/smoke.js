/**
 * Capacity smoke for isolated stacks. Does not prove Judge0 execution capacity.
 *
 *   k6 run -e BASE_URL=http://127.0.0.1:8080 -e VUS=20 -e DURATION=30s perf/k6/smoke.js
 */
import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";
const VUS = Number(__ENV.VUS || 20);
const DURATION = __ENV.DURATION || "30s";

export const options = {
  scenarios: {
    smoke: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
      gracefulStop: "10s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<2000"],
  },
  abortOnFail: true,
};

export default function () {
  // Prefer public/test endpoints so readiness probes that return 503 do not
  // inflate http_req_failed when the API process is up for capacity smoke.
  const ping = http.get(`${BASE_URL}/api/test`);
  check(ping, { "api test endpoint": (r) => r.status === 200 });

  const problems = http.get(`${BASE_URL}/api/problems?page=0&size=10`);
  check(problems, { "problems reachable": (r) => r.status === 200 });

  sleep(1);
}
