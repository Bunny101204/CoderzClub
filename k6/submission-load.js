import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PROBLEM_REF = __ENV.PROBLEM_REF || '8';
const USER_PREFIX = __ENV.K6_USER_PREFIX || 'loadtest';
const USER_START = Number(__ENV.K6_USER_START || '1');
const USER_COUNT = Number(__ENV.K6_USER_COUNT || '10');
const LOAD_PASSWORD = __ENV.K6_LOAD_PASSWORD;

if (!Number.isInteger(USER_START) || USER_START < 1) {
  throw new Error('K6_USER_START must be a positive integer');
}

if (!Number.isInteger(USER_COUNT) || USER_COUNT < 1) {
  throw new Error('K6_USER_COUNT must be a positive integer');
}

if (USER_COUNT > 25) {
  throw new Error(
    'Phase 4 safety guard: K6_USER_COUNT must not exceed 25. ' +
    'Do not remove this guard until the 25-user results are reviewed.'
  );
}

const admissionMs = new Trend('submission_admission_ms', true);
// CLIENT OBSERVED / POLL BASED — first poll that sees RUNNING/COMPLETED vs client clocks.
const completionMs = new Trend('submission_completion_ms', true);
const queueWaitMs = new Trend('submission_queue_wait_ms', true);
const executionObservedMs = new Trend('submission_execution_observed_ms', true);
// Server-side timestamps from the job document (createdAt/startedAt/completedAt).
const serverQueueMs = new Trend('submission_server_queue_ms', true);
const serverExecutionMs = new Trend('submission_server_execution_ms', true);
const serverLifecycleMs = new Trend('submission_server_lifecycle_ms', true);

const admittedJobs = new Counter('submission_jobs_admitted');
const completedJobs = new Counter('submission_jobs_completed');
const failedJobs = new Counter('submission_jobs_failed');
const response429 = new Counter('submission_http_429');
const response503 = new Counter('submission_http_503');
const response403 = new Counter('submission_http_403');
const internalErrors = new Counter('submission_internal_errors');

export const options = {
  scenarios: {
    existing_loadtest_users: {
      executor: 'per-vu-iterations',
      vus: USER_COUNT,
      iterations: 1,
      maxDuration: '8m',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    // API admission should remain fast even while work queues.
    submission_admission_ms: ['p(95)<1000'],
    // With two execution slots, 25 submissions may form ~13 waves.
    // Generous Phase 4 baseline only; not a production SLO.
    submission_completion_ms: ['p(95)<300000'],
  },
};

const sourceCode = `
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        if (!sc.hasNextInt()) return;

        int n = sc.nextInt();
        int m = sc.nextInt();

        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = sc.nextInt();
        }

        int[] b = new int[m];
        for (int i = 0; i < m; i++) {
            b[i] = sc.nextInt();
        }

        int[] result = new int[n + m];

        int i = 0;
        int j = 0;
        int k = 0;

        while (i < n && j < m) {
            if (a[i] <= b[j]) {
                result[k++] = a[i++];
            } else {
                result[k++] = b[j++];
            }
        }

        while (i < n) {
            result[k++] = a[i++];
        }

        while (j < m) {
            result[k++] = b[j++];
        }

        StringBuilder sb = new StringBuilder();

        for (int x = 0; x < result.length; x++) {
            if (x > 0) sb.append(" ");
            sb.append(result[x]);
        }

        System.out.println(sb);
    }
}
`;

function usernameFor(index) {
  return `${USER_PREFIX}${String(index).padStart(3, '0')}`;
}

function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

export function setup() {
  if (!LOAD_PASSWORD) {
    fail(
      'K6_LOAD_PASSWORD must contain the existing load-test account password.'
    );
  }

  const problemResponse = http.get(
    `${BASE_URL}/api/problems/${PROBLEM_REF}`,
    {
      tags: {
        name: 'GET /api/problems/:id',
      },
    }
  );

  const problemOk = check(problemResponse, {
    'problem lookup returned 200': (r) => r.status === 200,
    'problem lookup returned id': (r) => Boolean(r.json('id')),
  });

  if (!problemOk) {
    fail(
      `Problem lookup failed: HTTP ${problemResponse.status} ${problemResponse.body}`
    );
  }

  const users = [];

  for (let offset = 0; offset < USER_COUNT; offset++) {
    const accountNumber = USER_START + offset;
    const username = usernameFor(accountNumber);

    const login = http.post(
      `${BASE_URL}/api/login`,
      JSON.stringify({
        username,
        password: LOAD_PASSWORD,
      }),
      {
        headers: {
          'Content-Type': 'application/json',
        },
        tags: {
          name: 'POST /api/login',
        },
      }
    );

    const ok = check(login, {
      'load-test login returned 200': (r) => r.status === 200,
      'load-test login returned JWT': (r) => Boolean(r.json('token')),
    });

    if (!ok) {
      fail(
        `Login failed for ${username}: HTTP ${login.status}. ` +
        'Do not create a replacement account.'
      );
    }

    users.push({
      username,
      token: login.json('token'),
    });
  }

  return {
    users,
    problemId: problemResponse.json('id'),
  };
}

export default function (data) {
  const userIndex = __VU - 1;
  const user = data.users[userIndex];

  if (!user) {
    fail(`No existing load-test account mapped to VU ${__VU}`);
  }

  const startedAt = Date.now();

  const create = http.post(
    `${BASE_URL}/api/submission-jobs`,
    JSON.stringify({
      problemId: data.problemId,
      code: sourceCode,
      language: 'Java',
      languageId: 62,
      codingDurationSeconds: 10,
    }),
    {
      headers: authHeaders(user.token),
      tags: {
        name: 'POST /api/submission-jobs',
      },
    }
  );

  admissionMs.add(create.timings.duration);

  if (create.status === 429) {
    response429.add(1);
  }

  if (create.status === 503) {
    response503.add(1);
  }

  if (create.status === 403) {
    response403.add(1);
  }

  const admitted = check(create, {
    'submission returned 202': (r) => r.status === 202,
    'submission returned job id': (r) => Boolean(r.json('jobId')),
  });

  if (!admitted) {
    console.error(
      JSON.stringify({
        vu: __VU,
        username: user.username,
        stage: 'admission',
        status: create.status,
      })
    );

    failedJobs.add(1);
    return;
  }

  admittedJobs.add(1);

  const jobId = create.json('jobId');
  let finalJob = null;
  let firstRunningAt = null;
  let previousStatus = null;

  for (let poll = 0; poll < 80; poll++) {
    const response = http.get(
      `${BASE_URL}/api/submission-jobs/${jobId}`,
      {
        headers: {
          Authorization: `Bearer ${user.token}`,
        },
        tags: {
          name: 'GET /api/submission-jobs/:id',
        },
      }
    );

    if (response.status === 403) {
      response403.add(1);
    }

    const pollOk = check(response, {
      'job poll returned 200': (r) => r.status === 200,
    });

    if (!pollOk) {
      console.error(
        JSON.stringify({
          vu: __VU,
          username: user.username,
          jobId,
          stage: 'poll',
          status: response.status,
        })
      );

      failedJobs.add(1);
      return;
    }

    const job = response.json();

    if (job.status !== previousStatus) {
      console.log(
        JSON.stringify({
          vu: __VU,
          username: user.username,
          jobId,
          transition: job.status,
          elapsedMs: Date.now() - startedAt,
        })
      );

      previousStatus = job.status;
    }

    if (job.status === 'RUNNING' && firstRunningAt === null) {
      firstRunningAt = Date.now();
      // CLIENT OBSERVED / POLL BASED
      queueWaitMs.add(
        firstRunningAt - startedAt
      );
    }

    if (
      ['COMPLETED', 'FAILED', 'TIMEOUT', 'CANCELLED'].includes(job.status)
    ) {
      finalJob = job;
      break;
    }

    if (poll < 5) {
      sleep(1);
    } else if (poll < 15) {
      sleep(2);
    } else {
      sleep(5);
    }
  }

  const finishedAt = Date.now();
  const totalElapsed = finishedAt - startedAt;
  // CLIENT OBSERVED / POLL BASED
  completionMs.add(totalElapsed);

  if (firstRunningAt !== null) {
    // CLIENT OBSERVED / POLL BASED
    executionObservedMs.add(
      finishedAt - firstRunningAt
    );
  }

  if (!finalJob) {
    failedJobs.add(1);
    fail(
      `Job ${jobId} for ${user.username} did not reach terminal state`
    );
  }

  const createdAtMs = Date.parse(finalJob.createdAt);
  const startedAtMs = Date.parse(finalJob.startedAt);
  const completedAtMs = Date.parse(finalJob.completedAt);
  const serverQueue =
    Number.isFinite(createdAtMs) && Number.isFinite(startedAtMs)
      ? startedAtMs - createdAtMs
      : null;
  const serverExecution =
    Number.isFinite(startedAtMs) && Number.isFinite(completedAtMs)
      ? completedAtMs - startedAtMs
      : null;
  const serverLifecycle =
    Number.isFinite(createdAtMs) && Number.isFinite(completedAtMs)
      ? completedAtMs - createdAtMs
      : null;

  if (serverQueue !== null) {
    serverQueueMs.add(serverQueue);
  }
  if (serverExecution !== null) {
    serverExecutionMs.add(serverExecution);
  }
  if (serverLifecycle !== null) {
    serverLifecycleMs.add(serverLifecycle);
  }

  if (
    finalJob.result === 'INTERNAL_ERROR' ||
    finalJob.errorCode === 'JUDGE0_PROVIDER_ERROR'
  ) {
    internalErrors.add(1);
  }

  const successful = check(finalJob, {
    'job completed': (j) =>
      j.status === 'COMPLETED',
    'solution accepted': (j) =>
      j.result === 'ACCEPTED',
    'all four tests completed': (j) =>
      j.progress?.completed === 4 &&
      j.progress?.total === 4,
  });

  if (successful) {
    completedJobs.add(1);
  } else {
    failedJobs.add(1);
  }

  console.log(
    JSON.stringify({
      vu: __VU,
      username: user.username,
      jobId,
      status: finalJob.status,
      result: finalJob.result,
      progress: finalJob.progress,
      errorCode: finalJob.errorCode,
      admissionMs: create.timings.duration,
      queueWaitObservedMs:
        firstRunningAt === null
          ? null
          : firstRunningAt - startedAt,
      completionMs: totalElapsed,
      serverQueueMs: serverQueue,
      serverExecutionMs: serverExecution,
      serverLifecycleMs: serverLifecycle,
    })
  );
}
