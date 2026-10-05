/**
 * OBSOLETE: this script hits /api/judge0/execute (or Judge0 directly), not
 * POST /api/submission-jobs. Do not use it for capacity testing of the async
 * Submit architecture. Use k6/submission-smoke.js instead.
 *
 * Credentials must come from the environment. Never embed a JWT here.
 */
import http from 'k6/http';
import { check, fail } from 'k6';

export const options = {
    vus: 75,
    iterations: 75,
};

export default function () {
    const token = __ENV.K6_TOKEN;
    if (!token && __ENV.JUDGE0_DIRECT !== 'true') {
        fail('Obsolete script: set K6_TOKEN (or JUDGE0_DIRECT=true). Prefer k6/submission-smoke.js.');
    }

    const judge0Url = __ENV.JUDGE0_DIRECT === 'true'
    ? 'http://127.0.0.1:2358/submissions?base64_encoded=false&wait=true'
    : 'http://localhost:8080/api/judge0/execute';

const judge0Response = http.post(
    judge0Url,
        JSON.stringify({
            language_id: 71,
            source_code: 'print(2+3)',
            stdin: '',
        }),
        {
            headers: {
    'Content-Type': 'application/json',
    ...( __ENV.JUDGE0_DIRECT !== 'true'
        ? { 'Authorization': `Bearer ${token}` }
        : {}
    ),
},
        }
    );

    check(judge0Response, {
        'Judge0 returned success': (r) => r.status === 200 || r.status === 201,
        'Judge0 execution accepted': (r) =>
            r.json('status.description') === 'Accepted',
        'Judge0 returned stdout 5': (r) =>
            r.json('stdout') === '5\n',
    });
}
