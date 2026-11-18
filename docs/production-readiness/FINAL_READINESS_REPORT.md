# Final readiness report — 2026-11-18

**Scope:** Post-merge audit after PR #2 (DSA curriculum) and PR #4 (release hardening) landed on `main`.  
**Repo HEAD:** `a6bade3258a936ec3bcb14f0643719a9b16a6ba1` (fast-forward synced locally).  
**Hard constraints honored:** no production problem import; no production backend/frontend deploy; private bank under `.local/problem-bank/` uncommitted; secrets not printed.

This document contains **no credentials, private IP addresses, or internal hostnames**.

---

## GO / NO-GO

| Decision | Verdict | Why |
|---|---|---|
| Keep `main` as release candidate source | **GO** | PR #2 + #4 merged; GitHub `ci` + Workers Builds **success** on `a6bade3` |
| Deploy backend/worker images to production | **NO-GO** | Owner approval required; staging→prod cutover not authorized |
| Import 187 DSA problems into production | **NO-GO** | Seeder refuses production hosts without `--allow-production`; backup + approval required |
| Enable Java / Node / Go / other catalog languages | **NO-GO** | Only Python **71** and C++ **54** are live-verified; others stay disabled |
| Claim 200 concurrent Judge0 sandboxes | **NO-GO** | Phase 7 proved HTTP enqueue admission only; worker concurrency remains capped |
| Institution / practice demo on isolated local staging | **CONDITIONAL GO** | Language gates, hidden-test redaction, recovery, and load gates passed on `cz-pb-staging` |

---

## Repository sync (this session)

| Check | Result |
|---|---|
| Local branch | `main` @ `a6bade3` = `origin/main` |
| Method | `git fetch` + `git merge --ff-only origin/main` (no force, no stash drop) |
| Tracked dirty tree | Clean before/after sync |
| Private `.local/problem-bank/` | Present; gitignored; not staged |
| Untracked ops/perf trees | Preserved through checkout |

---

## Main CI verification (`a6bade3`)

Workflow: https://github.com/Bunny101204/CoderzClub/actions/runs/44187568713 — **success**

| Check | Conclusion |
|---|---|
| backend-tests | success |
| frontend-build | success |
| docker-build | success |
| manifest-validation | success |
| index-migration-validation | success |
| Workers Builds: coderzclub | success |

Prior merge commit for curriculum (`649a536`) also had **ci success**.

---

## Audit findings

### 1) Staging-only security settings (must not copy to production)

| Setting / practice | Isolated local staging (`cz-pb-staging`) | Production expectation |
|---|---|---|
| Bind addresses | Loopback-only published ports | Private VPC; no public Judge0 |
| `SUBMISSION_LIMIT_DAILY` / per-problem | Inflated for validation | Keep product defaults unless owner raises |
| `SUBMISSION_LIMIT_COOLDOWN_MS` | Lowered for local throughput | Product default unless staging-proven |
| `SUBMISSION_LIMIT_REDIS_FAIL_OPEN` | `false` | Keep **`false`** (fail closed) |
| Queue names | Staging-prefixed queues | Production queue names |
| JWT / Redis / Rabbit passwords | Compose placeholders for local only | Secret Manager; `APP_REQUIRE_SECURE_SECRETS=true` |
| Judge0 `privileged` + cgroup workaround | Allowed on laptop isolate only | Production Judge0 must use owned hardening; **do not** lift sandbox limits to “fix” Java/Node |
| Network from sandboxes | `ENABLE_NETWORK=false` | Keep disabled |

**Pass criterion for prod cutover:** production Compose/env must not inherit pb-staging limit inflation or staging queue names.

### 2) Production Judge0 isolation

| Control | Status |
|---|---|
| pb-staging Judge0 URL is compose-internal only | Verified in staging compose |
| Load/import scripts refuse production API hosts by default | `scripts/problem-bank/lib/gates.mjs` |
| k6 pb-staging guard targets loopback staging API only | Phase 7 docs + local guard script |
| Worker global Judge0 concurrency default | `WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY=2` |
| API Judge0 concurrency default | `JUDGE0_API_MAX_CONCURRENCY=2` (see scaling docs) |
| Production Judge0 probe for Java/Node | **Not done** — languages remain disabled |

**Risk if ignored:** pointing production API at laptop/staging Judge0, or copying privileged cgroup workarounds into prod without review.

### 3) Frontend / backend language compatibility

| Layer | Enabled IDs | Notes |
|---|---|---|
| `LanguageCapabilityCatalog` | 71, 54 | Others `enabled=false`; Go/Java/etc. rejected at validation |
| `LanguageTemplates.RELEASE_ENABLED_LANGUAGE_IDS` / `SUPPORTED_LANGUAGES` | 71, 54 | Editor dropdown aligned |
| `editorLanguagePreference` | Falls back to Python | Unsupported stored IDs remapped |
| CI / Playwright | Updated for Python default + C++ switch | Merged in PR #4 |

**Pass:** FE and BE gating match. **Do not** re-enable Java (62) / JS (63) until a production-class Judge0 hello-world + problem probe returns ACCEPTED.

### 4) Queue throughput

| Evidence | Result |
|---|---|
| Phase 7 k6 100 VU / 90s | checks 13875/13875; `http_req_failed` 0%; enqueue_fail 0%; p95 HTTP **1.45s** |
| Phase 7 k6 200 VU / 90s | checks 14120/14120; fail rates 0%; p95 HTTP **3.3s** |
| Interpretation | Admission/enqueue path holds under load; **not** equal to N concurrent isolates |
| Retry / outbox | Defaults: retry delay 30s (max 900s), outbox poll 1s / repair 30s (`application.properties`) |
| Institutional 300 VU (earlier laptop) | HTTP mix OK after login-index fix — still ≠ Judge0 concurrency |

**Production watch items:** Rabbit depth, DLQ growth, QUEUE_AGE / 429 rate, worker restart count.

### 5) End-to-end submission latency

| Path | Evidence | Claim level |
|---|---|---|
| Enqueue → HTTP 202 under load | Phase 7 p95 1.45s–3.3s (includes browse/login mix) | Staging HTTP latency only |
| Full Judge0 verdict latency (ACCEPTED) | Phase 6: 187/187 references ACCEPTED on staging Judge0 | Staging isolate; not cloud production path |
| Production API → production Judge0 E2E p95 | **Unmeasured** | Required before latency SLO |

### 6) Recovery behavior

| Check | Result |
|---|---|
| Worker container restart on `cz-pb-staging` | Phase 7 **PASS**; catalog still 17 bundles |
| Submission outbox / retry plumbing | Present in config + prior reliability release |
| Playwright draft recovery / submit failure UX | `e2e/submission-recovery.spec.js` (Python-aligned after PR #4) |
| Redis rate-limit fail-open | Default **false** — unavailable Redis → deny submit (safe) |

### 7) Database backup / rollback procedures

| Procedure | Documented | Executed against production? |
|---|---|---|
| Managed Mongo snapshot / PITR confirm before migrate/import | `PRODUCTION_PREFLIGHT.md` §5 | **No** (correct) |
| API container park/rename rollback | `DEPLOYMENT_CHECKLIST.md` | Owner-operated only |
| Staging rollback scripts | Local ops scripts (not production) | Local/staging only |
| Problem-bank import rollback plan | Curriculum requires backup + explicit `--allow-production` | **Blocked** until approval |
| Blind `backend/mongodb-indexes.js` | **Forbidden** (drop/TTL risk) | Do not run |

**Owner actions still required before any prod write:** verify latest managed snapshot success, record restore owner + last drill date, prefer staging restore rehearsal.

---

## Content / privacy posture

- Hidden tests must never appear on student problem detail (`BundleAccessService` excludes `hiddenTestCases`).
- Verdict sanitization strips hidden inputs/expected outputs from student-facing results.
- Public repo contains curriculum + seeding **tooling** only; oracles, manifests, credentials, and Phase 6/7 raw reports stay under `.local/problem-bank/`.
- Character-frequency ordering contract lives in tests only (no public reusable oracle module).

---

## Remaining owner-gated blockers (ordered)

1. Confirm production secrets (`JWT_SECRET` ≥ 64 bytes, no placeholders) via `APP_REQUIRE_SECURE_SECRETS=true`.
2. Confirm production Judge0 is VPC-isolated; run hello-world probes for any language before enablement.
3. Staging deploy smoke (API + worker + one Python ACCEPTED + one rejected Java) on non-prod data.
4. Managed Mongo backup/PITR verification + optional restore drill.
5. Explicit written approval for: production image cutover; then separately for 187-problem import batches.
6. Post-cutover watch: Rabbit depth, Judge0 errors, 429/QUEUE_AGE, SMTP, edge rollback target.

---

## Related docs

- `DEPLOYMENT_CHECKLIST.md` — executable cutover gates (updated this date)
- `PRODUCTION_PREFLIGHT.md` — env names / health / rollback order
- `docs/problem-bank-phase6-5-release-hardening.md` — language gating evidence
- `docs/problem-bank-phase7-staging-load.md` — load / recovery evidence
- `SECURITY_AUDIT.md`, `CAPACITY_REPORT.md` — earlier baselines (still valid caveats)
