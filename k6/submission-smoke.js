import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERNAME = __ENV.K6_USERNAME;
const PASSWORD = __ENV.K6_PASSWORD;
const PROBLEM_REF = __ENV.PROBLEM_REF || '8';

const admissionMs = new Trend('submission_admission_ms', true);
const completionMs = new Trend('submission_completion_ms', true);

const admittedJobs = new Counter('submission_jobs_admitted');
const completedJobs = new Counter('submission_jobs_completed');
const failedJobs = new Counter('submission_jobs_failed');

export const options = {
  vus: 1,
  iterations: 1,

  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    submission_admission_ms: ['p(95)<1000'],
    submission_completion_ms: ['p(95)<180000'],
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

function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

export function setup() {
  if (!USERNAME || !PASSWORD) {
    fail(
      'K6_USERNAME and K6_PASSWORD must be supplied through environment variables.'
    );
  }

  const login = http.post(
    `${BASE_URL}/api/login`,
    JSON.stringify({
      username: USERNAME,
      password: PASSWORD,
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
    'login returned 200': (r) => r.status === 200,
    'login returned JWT': (r) => Boolean(r.json('token')),
  });

  if (!loginOk) {
    fail(`Login failed: ${login.status} ${login.body}`);
  }

  const token = login.json('token');

  const problem = http.get(
    `${BASE_URL}/api/problems/${PROBLEM_REF}`,
    {
      tags: {
        name: 'GET /api/problems/:id',
      },
    }
  );

  const problemOk = check(problem, {
    'problem lookup returned 200': (r) => r.status === 200,
    'problem lookup returned Mongo id': (r) => Boolean(r.json('id')),
  });

  if (!problemOk) {
    fail(`Problem lookup failed: ${problem.status} ${problem.body}`);
  }

  return {
    token,
    problemId: problem.json('id'),
  };
}

export default function (data) {
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
      headers: authHeaders(data.token),
      tags: {
        name: 'POST /api/submission-jobs',
      },
    }
  );

  admissionMs.add(create.timings.duration);

  const admitted = check(create, {
    'submission returned 202': (r) => r.status === 202,
    'submission returned job id': (r) => Boolean(r.json('jobId')),
  });

  if (!admitted) {
    console.error(
      `Admission failed: ${create.status} ${create.body}`
    );

    failedJobs.add(1);
    return;
  }

  admittedJobs.add(1);

  const jobId = create.json('jobId');

  let finalJob = null;

  for (let poll = 0; poll < 60; poll++) {
    const response = http.get(
      `${BASE_URL}/api/submission-jobs/${jobId}`,
      {
        headers: {
          Authorization: `Bearer ${data.token}`,
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
        `Job poll failed: ${response.status} ${response.body}`
      );

      failedJobs.add(1);
      return;
    }

    const job = response.json();

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

  const elapsed = Date.now() - startedAt;
  completionMs.add(elapsed);

  if (!finalJob) {
    failedJobs.add(1);
    fail(`Job ${jobId} did not reach a terminal state`);
  }

  const success = check(finalJob, {
    'job completed': (j) => j.status === 'COMPLETED',
    'solution accepted': (j) => j.result === 'ACCEPTED',
  });

  if (success) {
    completedJobs.add(1);
  } else {
    failedJobs.add(1);
  }

  console.log(
    JSON.stringify({
      jobId,
      status: finalJob.status,
      result: finalJob.result,
      progress: finalJob.progress,
      errorCode: finalJob.errorCode,
      admissionMs: create.timings.duration,
      completionMs: elapsed,
    })
  );
}
