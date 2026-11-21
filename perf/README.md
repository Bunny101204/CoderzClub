# Isolated capacity testing

## Stack

```bash
docker compose -p coderzclub-perf -f docker-compose.perf.yml up -d --wait
```

Ports (loopback only): Mongo `27027`, Redis `6389`, Rabbit `5682`, Mailpit SMTP `1125` / UI `8125`.

Point a local API at this stack with dedicated env vars. Do **not** reuse developer `27017` or production Atlas.

## Safe local API/worker startup

`docker-compose.perf.yml` supplies dependencies only. The guarded Spring
launcher is `scripts/ops/start-perf-local.ps1`; it never starts anything unless
`-Apply` is supplied. It uses only `PERF_*` variables, requires Mongo
`mongodb://127.0.0.1:27027` / `coderzclub_perf`, Redis `127.0.0.1:6389`, and
RabbitMQ `127.0.0.1:5682` on the dedicated `/coderzclub-perf` vhost. It also
forces perf-specific queue and exchange names.

Set these variables in the local shell without printing the values. The Rabbit
vhost and credentials must already exist; the launcher does not create users,
vhosts, queues, or data:

```powershell
$env:PERF_MONGO_URI = 'mongodb://127.0.0.1:27027'
$env:PERF_MONGO_DB = 'coderzclub_perf'
$env:PERF_REDIS_HOST = '127.0.0.1'; $env:PERF_REDIS_PORT = '6389'; $env:PERF_REDIS_DATABASE = '7'
$env:PERF_RABBIT_HOST = '127.0.0.1'; $env:PERF_RABBIT_PORT = '5682'; $env:PERF_RABBIT_MANAGEMENT_PORT = '15682'; $env:PERF_RABBIT_VHOST = '/coderzclub-perf'
$env:PERF_RABBIT_USERNAME = '<dedicated-local-user>'; $env:PERF_RABBIT_PASSWORD = '<dedicated-local-password>'
$env:PERF_JUDGE0_API_URL = 'http://127.0.0.1:2358/submissions'
$env:PERF_JWT_SECRET = '<unique-local-secret-at-least-32-characters>'
```

Dry-run / validation only:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ops/start-perf-local.ps1
```

Explicit startup starts API only on `127.0.0.1:8082`; it does not invoke
Judge0:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ops/start-perf-local.ps1 -Apply
```

The worker is separately controlled and refuses to start until the local
Judge0 sandbox has been reviewed. Do not set this flag based only on Judge0
being reachable:

```powershell
$env:PERF_JUDGE0_SANDBOX_VERIFIED = '1' # only after documented sandbox evidence
powershell -ExecutionPolicy Bypass -File scripts/ops/start-perf-local.ps1 -Apply -StartWorker -AllowJudge0Worker
```

Worker ports are `127.0.0.1:8092` and management `127.0.0.1:8093`; API
management is `127.0.0.1:8084`. The default API-only mode is the safe mode for
admission testing. A mock Judge0 provider is not present in this repository, so
there is no safe worker-execution mode until the real local sandbox is verified.

Read-only runtime and effective-target checks:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ops/verify-perf-local.ps1
```

This checks the guarded `PERF_*` targets and API/readiness endpoints without
printing credentials or queue contents. Worker readiness is optional in
API-only mode. Stop only launcher-owned Java processes, preserving all queues,
Mongo data, and Redis keys:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ops/stop-perf-local.ps1
```

Queue recovery is non-destructive: stop API admission, inspect the RabbitMQ
perf queues read-only, and—only after Judge0 sandbox approval—start the worker
and allow it to drain until `messages_ready` and `messages_unacknowledged`
return to the pre-test baseline. Never purge queues, delete messages, reset
Mongo, flush Redis, or use `down -v` as recovery. Without verified Judge0, the
remaining queued jobs are an explicit blocker rather than something this
launcher will process or discard.

After API-only validation and the queue baseline are confirmed, the guarded
50-VU admission command is:

```powershell
if (-not $env:PERF_PASSWORD) { throw 'Set PERF_PASSWORD in the local shell without echoing it' }
k6 run -e BASE_URL=http://127.0.0.1:8082 -e VUS=50 -e DURATION=30s perf/k6/submission-pipeline.js
```

The run is acceptable only when HTTP 202 admissions, admitted jobs/sec,
status counts, HTTP p95, enqueue p95, and admission success rate meet the
documented thresholds; authentication, transport, and unexpected-response
counts are zero; and the perf queue drains back to its recorded baseline.

## k6

Install [k6](https://k6.io) locally (free/open-source).

```bash
k6 run -e BASE_URL=http://127.0.0.1:8080 -e VUS=20 -e DURATION=30s perf/k6/smoke.js
```

Ramp gradually: 20 → 50 → 100 → 200 VUs. Abort on threshold breach.

**Interpretation:** k6 virtual users measure API/queue admission under synthetic load. They are **not** proof that N concurrent Judge0 executions are supported. Separate mock-Judge0 enqueue tests from real Judge0 latency tests.

## Synthetic users

```bash
node scripts/perf/seed-institutional.mjs          # ~20k users on 27027 / coderzclub_perf
node scripts/perf/seed-institutional.mjs --cleanup
```

Idempotent by `perf_seed_meta.version`. Never targets developer `27017` or Atlas unless `PERF_ALLOW_NON_ISOLATED=1`.

Realistic workload:

```bash
k6 run -e BASE_URL=http://127.0.0.1:8082 -e VUS=20 -e DURATION=30s perf/k6/institutional.js
k6 run -e BASE_URL=http://127.0.0.1:8082 -e VUS=20 -e DURATION=20s perf/k6/submission-pipeline.js
```

`submission-pipeline.js` is a local admission test, not a production or Judge0
capacity test. It refuses non-loopback API targets, requires `PERF_PASSWORD` (no
default is embedded), and submits at most one non-retried job per VU. HTTP 202
alone is admitted; 409, 429, unexpected responses, authentication failures, and
transport errors are reported separately. The k6 `setup()` return value contains
only `{ apiReady: true }`, so credentials/tokens are not placed in exported
`setup_data` or logs.

## Read-only preflight before submission traffic

Do not infer dependency isolation from `http://127.0.0.1:8082`. Before running
the test, inspect the API and worker environment/configuration and confirm all
of these facts without changing state:

```powershell
Get-ChildItem Env:MONGODB_URI,Env:SPRING_DATA_REDIS_HOST,Env:SPRING_DATA_REDIS_PORT,Env:SPRING_REDIS_HOST,Env:SPRING_REDIS_PORT,Env:RABBITMQ_HOST,Env:RABBITMQ_PORT,Env:RABBITMQ_VHOST,Env:JUDGE0_API_URL -ErrorAction SilentlyContinue
Get-Content backend/src/main/resources/application.properties | Select-String 'mongodb|redis|rabbitmq|judge0'
docker compose -p coderzclub-perf -f docker-compose.perf.yml ps
docker port coderzclub-perf-mongo coderzclub-perf-redis coderzclub-perf-rabbit
```

The API/worker must use the disposable perf Mongo at `127.0.0.1:27027` with a
non-production database (for example `coderzclub_perf`), Redis at
`127.0.0.1:6389` on a dedicated database, RabbitMQ at `127.0.0.1:5682` with a
dedicated vhost/queue, and a local Judge0 endpoint such as `127.0.0.1:2358`.
Verify the actual resolved values in the process/container environment; do not
print secrets or full credential-bearing URIs. If any target is unknown,
production-attached, shared with developer services, or the worker is absent,
do not run the test. Do not purge queues or reset data.

The bounded 50-VU command (do not execute from this review) is:

```powershell
if (-not $env:PERF_PASSWORD) { throw 'Set PERF_PASSWORD in the local shell without echoing it' }
k6 run -e BASE_URL=http://127.0.0.1:8082 -e VUS=50 -e DURATION=30s perf/k6/submission-pipeline.js
```

Pass only if the run stays on the loopback target, `status_202` is the only
admission count, admission success rate is at least 70%, HTTP and enqueue p95
are below 5 seconds, and authentication/transport/unexpected counts are zero.
Treat any 409/429 as controlled rejection and investigate queue depth; do not
call the run safe if the queue is not demonstrably draining afterward. A repeat
run can still create new attempts; the backend's first-solve reward guard is
covered by `LocalSubmissionFlowIT`, but the script cannot prove that an
external worker or dependency is isolated. Queue backlog and dependency
isolation therefore remain operator-verified blockers.

Measured laptop results: `docs/production-readiness/CAPACITY_REPORT.md`.

## Disposable stack teardown (not recovery)

This is not part of the API/worker shutdown or queue-recovery procedure. Use
only after all perf work is complete, the queue is drained, and disposable
infrastructure teardown has been explicitly approved. It removes the perf
volumes; never use it to handle a backlog or an unhealthy worker.

```bash
docker compose -p coderzclub-perf -f docker-compose.perf.yml down -v
```
