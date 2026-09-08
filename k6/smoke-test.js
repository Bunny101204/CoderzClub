import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 75,
    iterations: 75,
};

export default function () {
    const token = 'eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJidW5ueSIsInJvbGUiOiJ1c2VyIiwiaWF0IjoxNzg4ODA2MDkwLCJleHAiOjE3ODg4OTI0OTB9.g8TV7D0JBMe1xUgzcdltuQmmhJjmASmrJHmKpiCLxI5MEunXrw1yAtiIcAZ4Ly7HbS_GQHL8y0gQJ-Y0PXShFA';

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