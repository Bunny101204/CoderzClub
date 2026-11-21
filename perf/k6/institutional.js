/**
 * Realistic institutional workload against an isolated API.
 *
 *   k6 run -e BASE_URL=http://127.0.0.1:8081 -e VUS=20 -e DURATION=45s perf/k6/institutional.js
 *
 * Does NOT prove Judge0 execution capacity for N concurrent sandboxes.
 */
import http from "k6/http";
import { check, sleep, group } from "k6";
import { SharedArray } from "k6/data";
import { Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8081";
const VUS = Number(__ENV.VUS || 20);
const DURATION = __ENV.DURATION || "45s";
const PASSWORD = __ENV.PERF_PASSWORD || "Password1!";
const ORG_COUNT = Number(__ENV.PERF_ORG_COUNT || 10);

const loginFail = new Rate("login_fail");
const authzFail = new Rate("authz_fail");
const loginLatency = new Trend("login_latency", true);
const problemsLatency = new Trend("problems_latency", true);
const orgLatency = new Trend("org_latency", true);
const submitLatency = new Trend("submit_enqueue_latency", true);

const accounts = new SharedArray("accounts", () => {
  // Seed assigns ~1999 students per org sequentially (see scripts/perf/seed-institutional.mjs).
  const perOrg = Math.floor(19990 / ORG_COUNT);
  const list = [];
  for (let o = 0; o < ORG_COUNT; o++) {
    list.push({ username: `perf_owner_${o}`, orgId: `perf-org-${o}`, role: "owner" });
  }
  for (let i = 0; i < 2000; i++) {
    const org = Math.min(ORG_COUNT - 1, Math.floor(i / perOrg));
    list.push({ username: `perf_user_${i}`, orgId: `perf-org-${org}`, role: "student" });
  }
  return list;
});

export const options = {
  scenarios: {
    institutional: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
      gracefulStop: "15s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.20"],
    http_req_duration: ["p(95)<3000", "p(99)<8000"],
    login_fail: ["rate<0.05"],
    authz_fail: ["rate<0.10"],
  },
};

function authHeaders(token, orgId) {
  const headers = {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
    "X-Organization-Id": orgId,
  };
  return headers;
}

function login(account) {
  const started = Date.now();
  const res = http.post(
    `${BASE_URL}/api/login`,
    JSON.stringify({ username: account.username, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" }, tags: { name: "login" } }
  );
  loginLatency.add(Date.now() - started);
  const ok = check(res, { "login 200": (r) => r.status === 200 });
  loginFail.add(!ok);
  if (!ok) return null;
  try {
    return res.json("token");
  } catch (e) {
    loginFail.add(1);
    return null;
  }
}

export default function () {
  // Treat expected authz/not-found/limit statuses as non-failures for http_req_failed.
  http.setResponseCallback(http.expectedStatuses(200, 201, 202, 204, 403, 404, 409, 429));

  const account = accounts[(__VU + __ITER) % accounts.length];
  const token = login(account);
  if (!token) {
    sleep(1);
    return;
  }
  const headers = authHeaders(token, account.orgId);

  group("browse", () => {
    const listStarted = Date.now();
    const problems = http.get(`${BASE_URL}/api/problems?limit=20`, {
      headers,
      tags: { name: "problems_list" },
    });
    problemsLatency.add(Date.now() - listStarted);
    check(problems, {
      "problems 200": (r) => r.status === 200,
      "problems not 5xx": (r) => r.status < 500,
    });

    const detail = http.get(`${BASE_URL}/api/problems/perf-prob-${account.orgId.split("-").pop()}-0`, {
      headers,
      tags: { name: "problem_detail" },
    });
    check(detail, { "problem detail ok": (r) => r.status === 200 || r.status === 404 });
  });

  group("institution", () => {
    const orgStarted = Date.now();
    const mine = http.get(`${BASE_URL}/api/organizations`, {
      headers,
      tags: { name: "orgs_list" },
    });
    orgLatency.add(Date.now() - orgStarted);
    const mineOk = check(mine, { "orgs list 200": (r) => r.status === 200 });
    authzFail.add(!mineOk);

    const org = http.get(`${BASE_URL}/api/organizations/${account.orgId}`, {
      headers,
      tags: { name: "org_get" },
    });
    check(org, { "org get authorized": (r) => r.status === 200 || r.status === 403 });

    if (account.role === "owner") {
      const batches = http.get(`${BASE_URL}/api/admin/batches`, {
        headers,
        tags: { name: "batches_list" },
      });
      check(batches, { "batches list": (r) => r.status === 200 || r.status === 403 });

      const members = http.get(`${BASE_URL}/api/organizations/${account.orgId}/members`, {
        headers,
        tags: { name: "org_members" },
      });
      check(members, { "members list": (r) => r.status === 200 || r.status === 403 });
    }
  });

  group("contest", () => {
    const contests = http.get(`${BASE_URL}/api/contests`, {
      headers,
      tags: { name: "contests_list" },
    });
    check(contests, { "contests list": (r) => r.status === 200 || r.status === 403 });

    const contestId = `perf-contest-${account.orgId.split("-").pop()}`;
    const standings = http.get(`${BASE_URL}/api/contests/${contestId}/leaderboard?page=0&size=20`, {
      headers,
      tags: { name: "contest_leaderboard" },
    });
    check(standings, {
      "leaderboard reachable": (r) => r.status === 200 || r.status === 403 || r.status === 404,
    });
  });

  group("submission_enqueue", () => {
    // Bounded enqueue probe — measures admission, not Judge0 completion.
    if (__ITER % 7 !== 0) {
      return;
    }
    const opKey = `perf-k6-${__VU}-${__ITER}-${Date.now()}`;
    const started = Date.now();
    const body = JSON.stringify({
      problemId: `perf-prob-${account.orgId.split("-").pop()}-0`,
      languageId: 71,
      code: "print(1)\n",
      operationKey: opKey,
    });
    const submit = http.post(`${BASE_URL}/api/submission-jobs`, body, {
      headers,
      tags: { name: "submission_enqueue" },
    });
    submitLatency.add(Date.now() - started);
    check(submit, {
      "submit accepted or limited": (r) =>
        r.status === 200 || r.status === 201 || r.status === 202 || r.status === 409 || r.status === 429,
    });
    if (submit.status === 200 || submit.status === 201 || submit.status === 202) {
      let jobId = null;
      try {
        jobId = submit.json("id") || submit.json("jobId");
      } catch (e) {
        jobId = null;
      }
      if (jobId) {
        const status = http.get(`${BASE_URL}/api/submission-jobs/${jobId}`, {
          headers,
          tags: { name: "submission_poll" },
        });
        check(status, { "job poll": (r) => r.status === 200 || r.status === 404 });
      }
    }
  });

  sleep(0.5 + Math.random());
}
