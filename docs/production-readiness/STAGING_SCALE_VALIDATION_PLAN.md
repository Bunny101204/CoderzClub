# Staging validation plan — 500 / 1,000 / 2,000 concurrent active users

Evidence basis: `CAPACITY_REPORT.md`, `DATABASE_SIZING.md`, local k6 post-login-fix runs.  
**No invented pass/fail results. No invented cloud prices.**

## Critical distinction

| What was measured locally | What it is **not** |
|---|---|
| Up to **300 k6 VUs** institutional HTTP mix on a laptop (login + browse + enqueue attempts), **0%** `http_req_failed` / `login_fail` after index/query fix | 300 students **executing code** on Judge0 at once |
| **20,000** seeded registered users on isolated Mongo `27027` | 20,000 concurrent sessions or executions |

Separate gates:

1. **Browsing/login capacity** (API + Mongo + Redis + auth)
2. **Execution throughput** (workers × Judge0 concurrency + queue depth)

## Measured baseline (do not extrapolate as staging proof)

From `capacity/k6-institutional-300vu-postfix.log` / `.json`:

- Duration: **20s** steady + graceful stop (~**22.5s** wall)
- VUs: **300**
- Iterations: **897**
- HTTP requests: **6623**, failed **0%**
- Checks: **7520/7520** passed; `login_fail` **0%**
- p95 HTTP ~**2.58s**; login p95 ~**3.42s** (demo-heavy latency, not a UX SLO)
- Slow start: first ~6s completed **0** iterations (cold/ramp saturation), then recovered and finished with **0** interrupted iterations
- Container restart / CPU-RAM / Mongo Atlas metrics: **not captured in that log** — treat application HTTP success as necessary but incomplete health evidence; staging must add JVM, host, Mongo, Rabbit, Judge0 metrics

## Stage gates (staging environment only)

### Shared setup

- Dedicated staging Mongo with `username`/`email` unique indexes (run `account-index-preflight.mjs` read-only first).
- ≥2 API replicas behind LB; ≥1 worker initially, scale workers for execution gates.
- Redis + Rabbit sized with alerting on memory, connections, queue depth, consumer lag.
- Judge0 capacity documented as `workers × WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY` (+ API run path separately).
- Seed institutional synthetic users as needed; **never** seed production.

### Gate A — 500 concurrent **active** (browse/login)

**Pass criteria**

- k6 institutional mix @ 500 VU (or closed-model active users) for ≥10 minutes
- `login_fail` < 1%; `http_req_failed` < 1%
- p95 login and authenticated GETs within agreed staging SLO (set before run; do not reuse laptop 300-VU latency as SLO)
- No API/worker crash loops; Mongo connections stable; no sustained pool wait

**Scale knobs if failing:** API replicas, Tomcat threads, Mongo connections, confirm indexes (not “upgrade Atlas” first).

### Gate B — 1,000 concurrent **active** (browse/login)

**Pass criteria**

- Same mix @ 1000 VU ≥10 minutes
- Error budgets as Gate A
- Horizontal API scaling demonstrated (add replica under load; watch session/JWT-stateless behavior)
- Redis CPU/memory headroom; Rabbit not required for pure browse/login but keep healthy

**DB upgrade:** only if metrics show connection limits, cache eviction pressure, or indexed query p95 growth — see `DATABASE_SIZING.md`. Prefer connection pooling + indexes + read isolation before tier changes.

### Gate C — 2,000 concurrent **active** (browse/login)

**Pass criteria**

- 2000 VU sustained with agreed error/latency budgets
- Autoscaling policy validated (API HPA/VM ASG) with cooldown evidence
- Mongo primary CPU, WT cache, and slow-query log reviewed during peak

**Cost:** record actual staging invoice/SKUs after the run; do not estimate here.

### Gate D — Large contest **submission bursts** (execution)

Run **separately** from A–C.

**Pass criteria**

- Burst N enqueue requests (e.g. 200 / 500 / 1000 submits in ≤60s) with known worker/Judge0 caps
- Measure: accept/429/QUEUE_AGE rates, time-to-verdict p50/p95, DLQ count, Judge0 429/5xx
- Prove backlog drains without data loss
- Explicitly report max **in-flight Judge0** executions observed (not VU count)

**Sizing formula (planning, not a claim of capacity):**

`max_submit_provider_activity ≈ worker_replicas × WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY`

Raise workers/Judge0 nodes for burst gates; do not confuse with login VU success.

## Recommended sequencing

1. Gate A (500 browse/login)
2. Gate D small burst (prove execution path on staging)
3. Gate B (1000 browse/login)
4. Gate D medium/large bursts with worker/Judge0 scale-out
5. Gate C (2000) only after A/B stable and cost approved

## Database upgrade recommendation rule

**Do not upgrade cloud Mongo solely because marketing wants 1k–2k concurrent.**  
Upgrade only when staging metrics demonstrate need (pool waits, WT pressure, CPU saturation on indexed hot paths, connection limits). Local 20k registered + 300 VU HTTP success does **not** by itself justify an Atlas tier change.

## Deliverables per gate

- k6 summary JSON + thresholds
- API/worker restart count
- CPU/RAM charts (API, worker, Mongo, Redis, Rabbit, Judge0)
- Queue depth / consumer lag
- Explicit statement: “Judge0 concurrency verified = X in-flight” or “not verified”

## Stage 3 execution-evidence note

The inspected `coderzclub-judge0-1` host has functional evidence for Accepted,
CPU time, memory termination, process restriction, and network denial. This is
not yet a concurrency or complete sandbox-security result: direct active
cgroup membership, limit, event, and cleanup evidence remains required.

Keep these tests separate and report them independently:

1. **HTTP active users:** 500 and 1,000 VUs for at least 10 minutes using the
   institutional mix. Measure login/API errors, p95/p99 latency, API CPU/RAM,
   Mongo pool/cache/slow queries, Redis health, and restarts.
2. **Submission queue throughput:** enqueue 200, 500, and 1,000 known-valid
   submissions in bounded bursts. Measure admission/429 rates, queue depth and
   age, consumer lag, retry/DLQ counts, time-to-verdict, and backlog drain.
   This does not claim simultaneous Judge0 execution.
3. **Concurrent Judge0 executions:** use known worker counts and
   `WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY`; instrument active provider requests
   and Judge0 host processes/cgroups. Report actual maximum in-flight
   executions, verdict latency, CPU/memory/pids enforcement, and host recovery.

Do not combine these workloads or infer execution concurrency from VU count.
The execution test is blocked until direct active cgroup enforcement is
recorded.
