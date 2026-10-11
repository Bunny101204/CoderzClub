import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CONTEST_ID = __ENV.CONTEST_ID;
const PROBLEM_ID = __ENV.PROBLEM_ID;
const USER_PREFIX = __ENV.K6_USER_PREFIX || 'loadtest';
const USER_START = Number(__ENV.K6_USER_START || '1');
const USER_COUNT = Number(__ENV.K6_USER_COUNT || '10');
const LOAD_PASSWORD = __ENV.K6_LOAD_PASSWORD;

if (!CONTEST_ID || !PROBLEM_ID) {
  throw new Error('CONTEST_ID and PROBLEM_ID must identify an already-created LIVE contest problem');
}

if (!Number.isInteger(USER_START) || USER_START < 1) {
  throw new Error('K6_USER_START must be a positive integer');
}

if (!Number.isInteger(USER_COUNT) || USER_COUNT < 1) {
  throw new Error('K6_USER_COUNT must be a positive integer');
}

const admissionMs = new Trend('contest_run_admission_ms', true);
const completionMs = new Trend('contest_run_completion_ms', true);
const admittedRuns = new Counter('contest_runs_admitted');
const completedRuns = new Counter('contest_runs_completed');
const failedRuns = new Counter('contest_runs_failed');
const response429 = new Counter('contest_run_http_429');
const response503 = new Counter('contest_run_http_503');

export const options = {
  scenarios: {
    contest_run_burst: {
      executor: 'per-vu-iterations',
      vus: USER_COUNT,
      iterations: 1,
      maxDuration: '8m',
    },
  },
  thresholds: {
    contest_run_admission_ms: ['p(95)<2000'],
  },
};

const sourceCode = `print(1)`;

function usernameFor(index) {
  return `${USER_PREFIX}${String(index).padStart(3, '0')}`;
}

function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

function pollDelaySeconds(pollNumber) {
  if (pollNumber <= 0) return 1;
  if (pollNumber === 1) return 1.5;
  return 2;
}

export function setup() {
  if (!LOAD_PASSWORD) {
    fail('K6_LOAD_PASSWORD must contain the existing load-test account password.');
  }

  const users = [];
  for (let offset = 0; offset < USER_COUNT; offset++) {
    const username = usernameFor(USER_START + offset);
    const login = http.post(
      `${BASE_URL}/api/login`,
      JSON.stringify({ username, password: LOAD_PASSWORD }),
      {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'POST /api/login' },
      }
    );
    const ok = check(login, {
      'load-test login returned 200': (r) => r.status === 200,
      'load-test login returned JWT': (r) => Boolean(r.json('token')),
    });
    if (!ok) {
      fail(`Login failed for ${username}: HTTP ${login.status}. Do not create a replacement account.`);
    }
    users.push({ username, token: login.json('token') });
  }

  return { users };
}

export default function (data) {
  const user = data.users[__VU - 1];
  if (!user) {
    fail(`No existing load-test account mapped to VU ${__VU}`);
  }

  const startedAt = Date.now();
  const create = http.post(
    `${BASE_URL}/api/contests/${CONTEST_ID}/problems/${PROBLEM_ID}/runs`,
    JSON.stringify({
      code: sourceCode,
      language: 'Python',
      languageId: 71,
    }),
    {
      headers: authHeaders(user.token),
      tags: { name: 'POST /api/contests/:id/problems/:id/runs' },
    }
  );

  admissionMs.add(create.timings.duration);
  if (create.status === 429) {
    response429.add(1);
    return;
  }
  if (create.status === 503) {
    response503.add(1);
    return;
  }

  const admitted = check(create, {
    'contest run returned 202': (r) => r.status === 202,
    'contest run returned runId': (r) => Boolean(r.json('runId')),
  });
  if (!admitted) {
    failedRuns.add(1);
    return;
  }

  admittedRuns.add(1);
  const runId = create.json('runId');

  for (let poll = 0; poll < 90; poll++) {
    sleep(pollDelaySeconds(poll));
    const response = http.get(`${BASE_URL}/api/contest-runs/${runId}`, {
      headers: { Authorization: `Bearer ${user.token}` },
      tags: { name: 'GET /api/contest-runs/:id' },
    });
    if (response.status !== 200) {
      failedRuns.add(1);
      return;
    }
    const job = response.json();
    if (job.status === 'COMPLETED') {
      completedRuns.add(1);
      completionMs.add(Date.now() - startedAt);
      return;
    }
    if (job.status === 'FAILED') {
      failedRuns.add(1);
      completionMs.add(Date.now() - startedAt);
      return;
    }
  }

  failedRuns.add(1);
}
