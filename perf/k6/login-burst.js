/**
 * Login-burst diagnostic. Captures status distribution for /api/login.
 *
 *   k6 run -e BASE_URL=http://127.0.0.1:8082 -e VUS=150 -e DURATION=25s perf/k6/login-burst.js
 */
import http from "k6/http";
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";
import { Counter, Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8082";
const VUS = Number(__ENV.VUS || 150);
const DURATION = __ENV.DURATION || "25s";
const PASSWORD = __ENV.PERF_PASSWORD || "Password1!";

const statusCounters = {
  s200: new Counter("login_status_200"),
  s401: new Counter("login_status_401"),
  s403: new Counter("login_status_403"),
  s429: new Counter("login_status_429"),
  s5xx: new Counter("login_status_5xx"),
  s0: new Counter("login_status_0"),
  sOther: new Counter("login_status_other"),
};
const loginFail = new Rate("login_fail");
const loginLatency = new Trend("login_latency", true);

const accounts = new SharedArray("accounts", () => {
  const list = [];
  for (let i = 0; i < 5000; i++) list.push(`perf_user_${i}`);
  for (let o = 0; o < 10; o++) list.push(`perf_owner_${o}`);
  return list;
});

export const options = {
  scenarios: {
    login_burst: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
      gracefulStop: "10s",
    },
  },
  thresholds: {
    login_fail: ["rate<0.05"],
    http_req_duration: ["p(95)<5000"],
  },
};

export default function () {
  const username = accounts[(__VU * 31 + __ITER) % accounts.length];
  const started = Date.now();
  const res = http.post(
    `${BASE_URL}/api/login`,
    JSON.stringify({ username, password: PASSWORD }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { name: "login" },
      timeout: "10s",
    }
  );
  loginLatency.add(Date.now() - started);
  const ok = res.status === 200;
  loginFail.add(!ok);
  check(res, { "login 200": (r) => r.status === 200 });

  if (res.status === 200) statusCounters.s200.add(1);
  else if (res.status === 401) statusCounters.s401.add(1);
  else if (res.status === 403) statusCounters.s403.add(1);
  else if (res.status === 429) statusCounters.s429.add(1);
  else if (res.status >= 500) statusCounters.s5xx.add(1);
  else if (res.status === 0) statusCounters.s0.add(1);
  else statusCounters.sOther.add(1);

  sleep(0.05);
}
