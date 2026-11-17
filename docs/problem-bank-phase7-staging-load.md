# Phase 7 — Security, Reliability, Recovery, Load (cz-pb-staging)

Scope: isolated local staging only (`http://127.0.0.1:18090`). No production access.

## Verdict

**PASS** for 100–200 concurrent HTTP users on cz-pb-staging with language gates,
hidden-test redaction, worker restart recovery, and bounded submission enqueue.

This does **not** claim 200 concurrent Judge0 sandboxes. Enqueue traffic is
admission-bounded (202/429/409 accepted); verdict execution capacity remains
worker/Judge0 limited.

## Security

| Check | Result |
| --- | --- |
| Student problem detail hides hidden tests | PASS |
| Java (62) / Node (63) submission rejected | PASS (HTTP 400) |
| Enabled languages | Python 71, C++ 54 only |
| Load script refuses non-`127.0.0.1:18090` targets | PASS |

## Reliability / recovery

| Check | Result |
| --- | --- |
| Restart `worker` container | PASS |
| API catalog reachable after restart | PASS |
| Bundle count remains 17 | PASS |

## Load (k6 `perf/k6/pb-staging-load.js`)

| VUs | Duration | checks | http_req_failed | login_fail | enqueue_fail | p95 http |
| ---: | --- | ---: | ---: | ---: | ---: | ---: |
| 100 | 90s | 13875/13875 | 0% | 0% | 0% | 1.45s |
| 200 | 90s | 14120/14120 | 0% | 0% | 0% | 3.3s |

Artifacts (gitignored): `.local/problem-bank/reports/phase7/`

## How to re-run locally

```powershell
node .local/problem-bank/reports/phase7/seed-load-users.mjs
k6 run --env BASE_URL=http://127.0.0.1:18090 --env VUS=100 --env DURATION=90s --env PB_LOAD_ENQUEUE=1 perf/k6/pb-staging-load.js
k6 run --env BASE_URL=http://127.0.0.1:18090 --env VUS=200 --env DURATION=90s --env PB_LOAD_ENQUEUE=1 perf/k6/pb-staging-load.js
```
