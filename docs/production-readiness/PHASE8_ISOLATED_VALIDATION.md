# Phase 8 — Isolated non-production validation (2026-11-18)

**Scope:** Local `cz-pb-staging` only (`127.0.0.1`). No production systems touched.  
**Private artifacts:** quantitative raw JSON under gitignored `.local/problem-bank/reports/phase8/`.  
**This document contains no credentials, private IPs, or hidden test data.**

---

## Exact GO / NO-GO

| Gate | Verdict |
|---|---|
| Secure Judge0 without local no-cgroup workaround | **NO-GO** |
| E2E judging throughput + p95/p99 (100–200 students) on secure Judge0 | **NO-GO** (measured only on insecure local isolate) |
| Queue backlog / recovery / rate-limit on isolated staging | **PASS** (local staging) |
| MongoDB backup + restore rehearsal (isolated DB) | **PASS** (local staging) |
| Dry-run + rollback verification for 187 problems / 17 bundles | **PASS** (local staging; production import still blocked) |
| Production deploy / production import | **NO-GO** (owner approval required) |

**Final decision: NO-GO for production cutover and production problem import.**

---

## 1) Secure Judge0 (blocker)

| Probe | Result |
|---|---|
| Host Docker cgroup | **v2** (`cgroup2fs`) |
| Stock `judge0/judge0:1.13.1` `isolate --cg -b 0 --init` | **FAIL** — `Failed to create control group /sys/fs/cgroup/memory/box-0/` |
| Running pb-staging Judge0 worker | Uses **LOCAL DEMO** empty `@cgroups` patch (insecure; staging-only) |
| Approved durable path | Linux host/VM with **cgroup v1** + stock isolate `--cg`, or approved isolate-v2 build (`JUDGE0_SANDBOX.md`) |

Secure Judge0 execution was **not** achieved on this workstation without the staging-only workaround.

---

## 2) E2E judging throughput / latency (100–200 simulated students)

Harness: private `verdict-load.mjs` against loopback API; Python **71**; Array Sum; worker global concurrency **2**.

| VUs | Terminal | ACCEPTED | Enqueue p95 | Enqueue p99 | Verdict p50 | Verdict p95 | Verdict p99 | Wall |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | 100 | **100** | 1.05s | 1.20s | 51.6s | **82.0s** | **85.3s** | 87.3s |
| 200 | 200 | **200** | 1.30s | 1.45s | 92.7s | **155.6s** | **160.9s** | 164.3s |

**Interpretation**

- Admission + full terminal judging held for 100 and 200 concurrent students on this laptop stack.
- Latency scales with queueing behind ~2 Judge0 worker slots — **not** 100–200 concurrent sandboxes.
- Because Judge0 is still on the insecure no-cgroup patch, these numbers are **capacity evidence only**, not a production security pass.

---

## 3) Queue backlog, recovery, rate-limit

| Check | Result |
|---|---|
| Staging submission queue consumers | **2** on `cz.pb.staging.submissions` |
| DLQ / retry depth after runs | **0** |
| Backlog under burst | Observed ready/unacked messages while workers drained (e.g. ready=1, unacked=2) then returned to **0** |
| Worker container restart | API remained **200**; catalog still **187** problems / **17** bundles |
| Per-user burst (50 rapid jobs) | **27×202** + **23×429** — cooldown/rate-limit engaged |
| Unsupported language (Java 62) | **HTTP 400** (gate intact) |

---

## 4) MongoDB backup / restore rehearsal

Isolated mongod only (`coderzclub_pb_staging`):

| Step | Result |
|---|---|
| `mongodump` | Success (incl. 187 problems, 17 `problem_bundles`, jobs/submissions) |
| `mongorestore` into scratch DB `coderzclub_pb_staging_restore_drill` | **45027** documents restored, **0** failed |
| Live DB unchanged after restore drill | problems **187** unchanged |
| Scratch DB dropped after drill | Yes |

This is **not** an Atlas PITR drill. Cloud backup ownership remains an owner gate.

---

## 5) Import dry-run + rollback verification

| Check | Result |
|---|---|
| Dry-run phases 1–5 | **187** problems, **17** bundles; **0** verify failures |
| Production apply without `--allow-production` | **Refused** |
| Staging phase-1 re-apply | **34** `skipped-unchanged`, **3** `bundle-exists` (idempotent) |
| Rollback plan | Private plan lists compensating delete IDs from manifests; **no deletes executed** |
| Live staging catalog after exercises | **187** problems / **17** bundles |

---

## Remaining blockers (ordered)

1. **Secure Judge0 host** (cgroup v1 or approved isolate-v2) with `--cg` proven + memory/CPU enforcement cases.  
2. Re-run 100/200 VU terminal-verdict latency on that secure Judge0.  
3. Owner-approved staging (non-laptop) smoke if cloud staging differs from `cz-pb-staging`.  
4. Atlas snapshot/PITR confirmation + owner restore drill.  
5. Explicit written approval for production image cutover, then separately for 187-problem import.

---

## Related

- `FINAL_READINESS_REPORT.md`, `DEPLOYMENT_CHECKLIST.md`, `JUDGE0_SANDBOX.md`
- Private evidence: `.local/problem-bank/reports/phase8/`
