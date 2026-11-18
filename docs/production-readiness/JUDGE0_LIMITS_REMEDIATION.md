# Judge0 / worker limits remediation

**Status: production NO-GO until verification below is complete.**  
No GCP changes, container restarts, or problem imports were performed by this task.

Updated: 2026-11-18 (corrected for Judge0 CE 1.13.1 compile-path reality)

---

## 1) Verified Judge0 CE 1.13.1 behavior

Source: upstream `judge0/judge0:1.13.1` (`isolate_job.rb`, `submission.rb`, `submissions_controller.rb`, `config.rb`).

| Concern | Actual behavior |
|---|---|
| Runtime CPU | Submission field `cpu_time_limit` (≤ `MAX_CPU_TIME_LIMIT`, default max **15**) |
| Runtime wall | Submission field `wall_time_limit` (≤ `MAX_WALL_TIME_LIMIT`, default max **20**) |
| Runtime memory | Submission field `memory_limit` KB (≤ `MAX_MEMORY_LIMIT`, default max **512000**) |
| **Compile CPU/wall** | **Not client-configurable.** Compile isolate always uses `Config::MAX_CPU_TIME_LIMIT` (`-t`) and `Config::MAX_WALL_TIME_LIMIT` (`-w`) |
| `compile_time_limit` API field | **Not permitted** by `submissions#permit` — Rails drops it; sending it does nothing |
| Network / per-process | Submission flags; upstream **`ALLOW_ENABLE_*=true` by default** unless env sets `false` |
| `--cg` | Used when per-process time/memory flags are false |

### Deployed mismatch that motivated this work

| Layer | Value |
|---|---|
| Judge0 max CPU / memory | 15s / 512000 KB; `--cg` retained |
| Old worker defaults | maxCpu **60**, compile prop **30** (ineffective API field), memory **262144** |
| Old payload | Often omitted `wall_time_limit` → Judge0 default wall **10s** could fire before a 15s CPU budget |
| Live worker concurrency | **6** |

---

## 2) Safest compatible policy

Keep Judge0 maxima at **15s CPU / 20s wall / 512000 KB**. Do not raise them to paper over an old worker JAR.

| Setting | Safe value | Notes |
|---|---|---|
| Runtime `cpu_time_limit` | ≤ **15** | STANDARD_PER_CASE uses per-case budget (default 15 × 1) |
| Runtime `wall_time_limit` | **20** (≥ CPU, ≤ server max) | Must be sent explicitly |
| Runtime `memory_limit` | **262144** KB | ≤ server 512000; ≥ Judge0 min 2048 |
| Compile limits | Judge0 `MAX_*` only | Change only via Judge0 env, not worker payload |
| `enable_network` | **false** | Also set Judge0 `ALLOW_ENABLE_NETWORK=false` |
| Per-process flags | **false** | Also set Judge0 `ALLOW_ENABLE_PER_PROCESS_*=false` |
| Envelope | compileWall(**20**) + runWall(**20**) + slack(**15**) = **55** &lt; HTTP **120** &lt; lease **180** | |

DSA bank problems use **STANDARD_PER_CASE** only (no function-harness batch in the 187).

---

## 3) Code / config changes (local; not deployed)

1. Stop sending `compile_time_limit`.
2. Send `cpu_time_limit`, `wall_time_limit`, `memory_limit`, and force `enable_*=false`.
3. Startup validation rejects app CPU/wall/memory above server maxima; envelope uses compile **MAX_WALL** + run wall + slack.
4. Compose templates document Judge0 `ALLOW_*=false`, `MAX_CPU_TIME_LIMIT=15`, `MAX_WALL_TIME_LIMIT=20`.
5. Regression unit tests cover payload shape, clamps, legacy 60s rejection, and envelope math.

---

## 4) Server-side hardening (owner apply later — not done here)

```text
ENABLE_NETWORK=false
ALLOW_ENABLE_NETWORK=false
ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT=false
ALLOW_ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT=false
ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT=false
ALLOW_ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT=false
MAX_CPU_TIME_LIMIT=15
MAX_WALL_TIME_LIMIT=20
MAX_MEMORY_LIMIT=512000
```

Retain isolate `--cg`. Worker cutover env (names only):

```text
JUDGE0_MAX_CPU_TIME_LIMIT_SECONDS=15
JUDGE0_SERVER_MAX_CPU_TIME_LIMIT_SECONDS=15
JUDGE0_WALL_TIME_LIMIT_SECONDS=20
JUDGE0_SERVER_MAX_WALL_TIME_LIMIT_SECONDS=20
JUDGE0_SERVER_MAX_MEMORY_LIMIT_KB=512000
SUBMISSION_MAX_EXECUTION_TIME_SECONDS=15
SUBMISSION_MAX_MEMORY_KB=262144
```

---

## 5) Regression evidence (local cz-pb-staging only)

| Check | Result |
|---|---|
| Backend unit/IT suite | **776** tests, **0** failures (local Mongo/Redis/Rabbit) |
| Envelope on worker boot | compileWall=20, runCpu=15, runWall=20, slack=15 → **55** &lt; HTTP **120** &lt; lease **180** |
| Payload shape | `cpu_time_limit≤15`, `wall_time_limit=20`, `memory_limit=262144`, `enable_*=false`, **no** `compile_time_limit` |
| Multi-case harness-style batch (one sandbox, N cases) under 15s CPU | Direct Judge0 **Accepted** |
| C++ compile+run under MAX_* / 15s runtime | Direct Judge0 **Accepted** |
| 187 Python refs + available C++ samples | **187/187** ACCEPTED; multi-lang **66/66** ACCEPTED (incl. C++ 54); TLE/RE/infra **0** (local report under `.local/`, not committed) |

**Windows Docker Desktop note:** stock isolate `--cg` fails on cgroup v2. The 187 run uses the existing **LOCAL DEMO** no-cgroup patch on laptop Judge0 only. That does **not** satisfy the secure-sandbox GO gate.

---

## 6) Secure sandbox-enforcement test plan

### A. Config coherence
1. Judge0 env matches §4; isolate job has no `LOCAL DEMO` empty `@cgroups`.
2. Worker image includes remediation; CPU≤15, wall≤20.
3. Captured provider payload has no `compile_time_limit`; has `wall_time_limit` and `enable_*=false`.

### B. Limit enforcement
| Case | Expect |
|---|---|
| Busy-loop under 15s CPU | Runtime **TLE** |
| Allocate &gt;262144 KB | **MLE** / equivalent |
| `enable_network=true` with ALLOW false | Rejected / forced off |
| C++ hello | Compiles under Judge0 **MAX_*** compile isolate; runtime OK |
| Compile bomb | Bounded by Judge0 MAX_CPU/MAX_WALL; HTTP timeout not exceeded |

### C. Multi-case
| Mode | Expect |
|---|---|
| STANDARD_PER_CASE × N | N provider calls; each `cpu_time_limit≤15`, `wall_time_limit≤20` |
| FUNCTION_HARNESS_BATCH × N | 1 call; CPU clamped to ≤15 |
| Legacy maxCpu=60 | Worker **refuses startup** |

### D. Production GO gate
All of: remediation image live, ALLOW_* hardened, TLE/MLE/network cases recorded, owner approval. Until then: **NO-GO**.

---

## 7) Local verification notes (cz-pb-staging Judge0 1.13.1)

Captured from `GET /config_info` on the isolated local Judge0 before compose recreate:

| Key | Observed |
|---|---|
| `max_cpu_time_limit` | **15** |
| `max_wall_time_limit` | **20** |
| `wall_time_limit` (default if omitted) | **10** |
| `max_memory_limit` | **512000** |
| `enable_network` / `allow_enable_network` | **false** / **false** |
| `allow_enable_per_process_and_thread_*` | **true** until compose recreate applies `ALLOW_*=false` |

Sending `compile_time_limit: 99` with a C++ hello still returned **Accepted**; the field is not applied. Explicit `cpu_time_limit=15`, `wall_time_limit=20`, `enable_*=false` is Accepted.

Envelope check (defaults): compileWall 20 + runWall 20 + slack 15 = **55** &lt; HTTP **120** &lt; lease **180**.

---

## 8) Open questions (do not guess)

1. Exact live **GCP** Judge0 `MAX_WALL_TIME_LIMIT` / default `WALL_TIME_LIMIT` / `ALLOW_ENABLE_PER_PROCESS_*` (local stock matches 20 / 10 / allow-true until hardened).
2. Whether production currently sets `ALLOW_ENABLE_NETWORK=false` (upstream default is **allow**; local staging sets false).
3. Whether any non-DSA path still uses FUNCTION_HARNESS_BATCH under load with many cases (15s total clamp may be tight).
