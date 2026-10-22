import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PROBLEM_REF = __ENV.PROBLEM_REF || '8';

const admissionMs = new Trend('submission_admission_ms', true);
const completionMs = new Trend('submission_completion_ms', true);
const queueWaitMs = new Trend('submission_queue_wait_ms', true);
const executionMs = new Trend('submission_execution_ms', true);

const admittedJobs = new Counter('submission_jobs_admitted');
const completedJobs = new Counter('submission_jobs_completed');
const failedJobs = new Counter('submission_jobs_failed');
const response429 = new Counter('submission_http_429');
const response503 = new Counter('submission_http_503');
const internalErrors = new Counter('submission_internal_errors');

export const options = {
  scenarios: {
    five_concurrent_submitters: {
      executor: 'per-vu-iterations',
      vus: 5,
      iterations: 1,
      maxDuration: '4m',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    submission_admission_ms: ['p(95)<1000'],
    submission_completion_ms: ['p(95)<60000'],
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

function credentials() {
  const users = [];

  for (let i = 1; i <= 5; i++) {
    const username = __ENV[`K6_USERNAME_${i}`];
    const password = __ENV[`K6_PASSWORD_${i}`];

    if (!username || !password) {
      fail(
        `Missing K6_USERNAME_${i} or K6_PASSWORD_${i}`
      );
    }

    users.push({
      username,
      password,
    });
  }

  return users;
}

export function setup() {
  const rawUsers = credentials();

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
      `Problem lookup failed: ${problemResponse.status} ${problemResponse.body}`
    );
  }

  const users = [];

  for (let i = 0; i < rawUsers.length; i++) {
    const login = http.post(
      `${BASE_URL}/api/login`,
      JSON.stringify({
        username: rawUsers[i].username,
        password: rawUsers[i].password,
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

    const loginOk = check(login, {
      [`user ${i + 1} login returned 200`]:
        (r) => r.status === 200,
      [`user ${i + 1} login returned JWT`]:
        (r) => Boolean(r.json('token')),
    });

    if (!loginOk) {
      fail(
        `Login failed for load-test user ${i + 1}: HTTP ${login.status}`
      );
    }

    users.push({
      username: rawUsers[i].username,
      token: login.json('token'),
    });
  }

  return {
    users,
    problemId: problemResponse.json('id'),
  };
}

function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

export default function (data) {
  const userIndex = __VU - 1;

  if (!data.users[userIndex]) {
    fail(`No load-test account mapped to VU ${__VU}`);
  }

  const user = data.users[userIndex];
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
        body: create.body,
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

  for (let poll = 0; poll < 60; poll++) {
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

    const pollOk = check(response, {
      'job poll returned 200': (r) => r.status === 200,
    });

    if (!pollOk) {
      console.error(
        JSON.stringify({
          vu: __VU,
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
          jobId,
          transition: job.status,
          elapsedMs: Date.now() - startedAt,
        })
      );

      previousStatus = job.status;
    }

    if (job.status === 'RUNNING' && firstRunningAt === null) {
      firstRunningAt = Date.now();
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
  const elapsed = finishedAt - startedAt;
  completionMs.add(elapsed);

  if (firstRunningAt !== null) {
    executionMs.add(
      finishedAt - firstRunningAt
    );
  }

  if (!finalJob) {
    failedJobs.add(1);
    fail(
      `Job ${jobId} did not reach terminal state`
    );
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
      queueWaitMs:
        firstRunningAt === null
          ? null
          : firstRunningAt - startedAt,
      completionMs: elapsed,
    })
  );
}
