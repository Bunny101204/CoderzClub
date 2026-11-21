# Staging implementation plan (Stage 1 — inspect & plan only)

**Status:** Plan only. No cloud provision, deploy, commit, or production mutation authorized by this document.  
**Date:** 2026-11-08  
**Inputs:** `PROGRESS.md`, `PRODUCTION_PREFLIGHT.md`, `JUDGE0_SANDBOX.md`, `CAPACITY_REPORT.md`, `DEMO_LAUNCH.md`, compose/k8s/ops inspection.

---

## 1. Current architecture (as evidenced in-repo + local Docker)

```
[Browser / Cloudflare frontend] → https://api.coderzclub.site
        │
        ▼
[GCP VM coderzclub-api-1 :8080/8081]  SPRING_PROFILES_ACTIVE=api
        │  Mongo / Redis / Rabbit / Judge0 (private VPC)
        ▼
[GCP VM coderzclub-worker-1 :8082/8083] SPRING_PROFILES_ACTIVE=worker
        │
        ▼
[Judge0 (private)] ← isolate --cg required for secure sandbox
[MongoDB Atlas DB coderzclub]
[Redis + RabbitMQ private peers]
```

**Application roles**

| Role | Profile | Ports | Notes |
|---|---|---|---|
| API | `api` | 8080 + mgmt 8081 | Login, institution APIs, enqueue jobs, optional run path |
| Worker | `worker` | 8082 + mgmt 8083 | Consumes `coderzclub.submissions` (+ contest-run); Judge0 execute |
| Frontend | Cloudflare | public | CORS via `APP_CORS_ALLOWED_ORIGINS` |

**Local stacks (dev/demo — not staging)**

| Stack | Purpose |
|---|---|
| `coderzclub-test-*` :27017/6379/5672 | Institution e2e/demo DB (~10 users) |
| `coderzclub-perf-*` :27027/6389/5682 | Isolated 20k perf DB |
| `coderzclub-judge0` :2358 | Local Judge0 (cgroup v2 host; insecure patch must not leave laptop) |
| Mailpit :1025/:8025 | Local email |

**k8s manifests** (`k8s/coderzclub.yaml`): aspirational API×3 + worker×2, HPA/KEDA, secrets placeholders — **no Judge0 Deployment in-repo**. Production today is documented as **Docker on GCP VMs**, not an applied GKE cluster (re-verify before assuming k8s is live).

---

## 2. Available infrastructure vs required for staging

### Already available (repo / local)

- Backend jar/image build path (`backend/Dockerfile`, local tags such as `coderzclub-backend:api-*`)
- Compose templates: `docker-compose.production.yml` (api/worker/redis/rabbit + env **names**), local mailpit/perf/judge0-local
- Gated release helpers: `ops/release/*`, `ops/api-aa1ea86-release/*`, `ops/batch-mgmt-release/*` (redaction, dry-run patterns)
- Read-only preflight: `scripts/ops/account-index-preflight.mjs`
- Institution seed + rehearsal (local synthetic)
- Docs: deploy checklist, email ops, Judge0 sandbox policy, capacity evidence

### Exists in production notes (not modified here)

- GCP API/worker VMs (`10.20.0.4` / `10.20.0.7` — re-verify)
- Atlas `coderzclub`, Cloudflare frontend, private Redis/Rabbit/Judge0 peers

### Missing for an **isolated staging** environment

| Component | Gap |
|---|---|
| Staging GCP project or clearly named staging VMs | Not defined in-repo as a separate inventory |
| Staging Mongo (Atlas project/DB or dedicated RS) | Must **not** be production `coderzclub` |
| Staging Redis + Rabbit | Separate instances/vhosts from prod |
| Staging Judge0 with **secure** isolate | Cannot be Windows Docker Desktop + `--cg` disable |
| Staging secrets / SMTP | Owner-held; not in git |
| Staging frontend origin + CORS | e.g. staging hostname |
| Staging DNS / TLS | Optional for pilot; can use IP + hosts file short-term |
| Applied k8s staging cluster | Manifests exist; live cluster not evidenced |

---

## 3. Secure Judge0 isolation — can existing config achieve it?

| Environment | Secure isolate (`--cg` / equivalent) |
|---|---|
| Windows Docker Desktop (cgroup **v2**) + stock `judge0/judge0:1.13.1` | **No** — isolate 1.8 expects cgroup v1 |
| Local `--cg` disable patch | **Insecure — forbidden for staging/prod** |
| `docker-compose.production.yml` | Does **not** include Judge0 service; assumes external URL |
| k8s manifests | No Judge0 workload; only concurrency env + alerts |

**Conclusion:** Secure Judge0 for staging requires a **different Linux host/runtime**, not the laptop workaround:

1. **Recommended (smallest practical):** dedicated Linux VM (GCP) with **cgroup v1** (`systemd.unified_cgroup_hierarchy=0` or distro-supported legacy hierarchy), Docker Engine, stock Judge0 privileged compose, private VPC only.  
2. **Alternative (when vetted):** cgroup v2 host + approved isolate-v2 / Judge0 build that **keeps** resource isolation (not flag stripping).  
3. **Not allowed:** promoting patched local containers or images with empty `@cgroups`.

---

## 4. Deployment script risks (read-only review)

| Asset | Findings |
|---|---|
| `ops/batch-mgmt-release/*` | Hardcoded **production** bind IPs (`10.20.0.4` / `10.20.0.7`); container swap helpers — **do not run against prod without approval**; dry-run first |
| `ops/api-aa1ea86-release/api_release.py` | Same prod IP assumptions; has redaction helpers — still prod-oriented |
| `ops/release/README.md` | Explicitly gated; warns Judge0 not verified on audit host |
| `backend/mongodb-indexes.js` | **Creates and drops** indexes — data-definition risk; never run blindly on Atlas |
| `scripts/institution-e2e/seed.mjs` | `dropDatabase()` on **e2e DB only** — safe if URI forced to local e2e; dangerous if mis-pointed |
| `scripts/ops/patch-judge0-no-cgroup.sh` | Opt-in insecure; blocked without env flag — **exclude from staging runbooks** |
| `docker-compose.production.yml` | Requires env substitution; no hardcoded cloud passwords; binds `8080:8080` (tighten for staging) |
| k8s Secret | Placeholder `replace-me` only — good |

**Hardcoded credentials in git:** none found for live Atlas/SMTP (placeholders / `${VAR:?}`). Fixture env is gitignored.

---

## 5. Recommended smallest staging architecture

**Goal:** one institution pilot path, isolated from production data plane.

```
                    [Staging browser / Vite or CF staging]
                                    │
                    [Staging API VM] ── Mongo staging DB (Atlas staging or RS)
                         │          ── Redis staging
                    [Staging Worker]── Rabbit staging (own vhost)
                         │
                    [Staging Judge0 VM — Linux, cgroup v1, private IP]
                         │
                    [Mailpit or staging SMTP]
```

**Separation rules**

- Distinct Mongo database (and preferably Atlas project) — never `coderzclub` prod.
- Distinct Redis DB index and Rabbit **vhost** (or separate brokers).
- Distinct JWT secret; `APP_REQUIRE_SECURE_SECRETS=true` on staging API.
- Judge0 reachable only from API/worker private network (no public 2358).
- Frontend CORS limited to staging origin.
- Image tags labeled `staging-*`; never swap prod containers with staging scripts that hardcode prod IPs without a staging profile.

**Minimum VM estimates (pilot smoke — not 500–1000 VU)**

| VM | vCPU | RAM | Disk | Notes |
|---|---|---|---|---|
| API + worker (can share one VM initially) | 2–4 | 8 GB | 40 GB | Two JVMs; raise if co-located with brokers |
| Judge0 (dedicated) | 2–4 | 8 GB | 40 GB | Privileged Docker; cgroup v1; compilers heavy |
| Optional: Redis+Rabbit co-located with API | +1–2 vCPU / +2–4 GB | | | Or use small managed/redis container |
| Mongo | Atlas M10-class **estimate** or small RS | | | Confirm with metrics; free/shared noisy |

Single combined “all-in-one staging” VM (API+worker+redis+rabbit+judge0) is possible for cost control but **Judge0 privileged + API on same host** increases blast radius — prefer **Judge0 on its own VM** even for minimum staging.

---

## 6. Free local vs paid cloud

| Work | Free / local | Needs paid cloud |
|---|---|---|
| Compose staging **templates** + env name checklists | Yes | |
| Account index preflight on local e2e/perf | Yes | |
| Institution workflow rehearsal (demo DB) | Yes | |
| Unit/integration tests, k6 HTTP against local perf | Yes (laptop limits) | |
| Secure Judge0 Accepted + TLE/MLE proof | **No** on current Windows cgroup v2 | Linux VM |
| Staging SMTP provider validation | Partial (Mailpit local) | Real SMTP |
| Isolated staging Mongo/Redis/Rabbit durable | Limited (local Docker) | Atlas / GCP VMs |
| 500–1000 concurrent contest claims | No | Staging-sized load + Judge0 |
| Cloudflare staging hostname | | Owner CF / DNS |

---

## 7. Step-by-step implementation (after Stage 1 approval)

### Phase A — Local docs/templates only (no cloud)

1. Add `docker-compose.staging.yml` (or staging overlay) with **explicit project name**, loopback or staging-only binds, **no** prod IPs, **no** Judge0 `--cg` patch.  
2. Staging env example file (**names only**, no secrets): DB name `coderzclub_staging`, separate Redis DB, Rabbit vhost.  
3. Staging runbook: health checks, smoke list, rollback (stop new containers / restore previous tag).  
4. Explicit “forbidden” list: patch-judge0-no-cgroup, mongodb-indexes.js against Atlas, batch-mgmt swap against prod IPs.

### Phase B — Owner provisions (paid)

1. Create staging GCP VM(s) + firewall (private Judge0).  
2. Configure Linux Judge0 host for **cgroup v1** (or approved v2+isolate-v2).  
3. Provision staging Mongo + Redis + Rabbit.  
4. Load secrets into staging secret store (not git).

### Phase C — Deploy to staging (owner-approved)

1. Deploy **worker** then **API** images (`staging` tags).  
2. Point `JUDGE0_API_URL` at private staging Judge0.  
3. Run index preflight read-only; apply indexes only if approved.  
4. Smoke: login → org batch → contest → **Accepted** (secure Judge0) → CSV → tenant isolation → Mailpit/SMTP.  
5. Optional: modest k6 against staging API (HTTP) separate from Judge0 burst test.

### Verification checklist

- [ ] `docker info` on Judge0 host → Cgroup Version **1** (or documented isolate-v2 path)  
- [ ] Sample job uses isolate **with** cgroup limits (log/command evidence)  
- [ ] Intentional TLE/MLE cases behave correctly  
- [ ] Staging Mongo ≠ production URI/DB name  
- [ ] Readiness UP on API 8081 / worker 8083  
- [ ] No insecure patch scripts in the executed runbook  

### Rollback

1. Stop new staging API/worker containers (or revert tag).  
2. Restore previous staging image tag; do not touch production `coderzclub-api-before-*`.  
3. Leave staging Mongo intact unless explicitly wiping a disposable staging DB.  
4. Judge0: revert compose to last known-good image digest on staging VM only.

---

## Exact next **local** implementation step (Stage 2 start — not executed in Stage 1)

**Create a staging-oriented compose overlay + env-name checklist + runbook section that:**

1. Uses project name `coderzclub-staging` and database name `coderzclub_staging`.  
2. Documents required Linux Judge0 host prerequisites (cgroup v1 verify commands).  
3. Lists forbidden scripts (`patch-judge0-no-cgroup.sh`, prod IP swap helpers).  
4. Contains smoke/rollback commands with **placeholder hosts** (no prod IP defaults).

Do **not** provision VMs or apply cloud changes until the owner approves Stage 2.

## Stage 2 local implementation status

The local-only portion of Stage 2 is now implemented:

- `docker-compose.staging.yml` uses project `coderzclub-staging`, database
  `coderzclub_staging`, a protected Redis instance, a separate RabbitMQ vhost,
  loopback API binds, fail-closed required variables, and an external private
  Judge0 URL. It contains no Judge0 service and no insecure cgroup workaround.
- `.env.staging.example` and `ops/staging/judge0.env.example` contain
  placeholders only.
- `scripts/ops/start-staging.ps1` validates selectors and rejects production or
  insecure references before Compose validation. It starts nothing unless
  `-Apply` is explicitly supplied.
- `scripts/ops/staging-smoke.ps1` and `scripts/ops/rollback-staging.ps1` accept
  only `.env.staging`; rollback preserves volumes and requires confirmation.
- `ops/staging/JUDGE0_SANDBOX_CHECKLIST.md` documents cgroup, CPU, memory,
  execution-time, process, network, and `--cg` enforcement evidence.
- `scripts/ops/test-staging-config.mjs` statically checks for production
  references, credentials, destructive commands, and insecure Judge0 settings.

The Redis mismatch was corrected in the application binding: `SPRING_REDIS_HOST`
is preferred and the existing `SPRING_DATA_REDIS_HOST` is accepted as a
fallback. Production Compose was not changed.

## Next step before any staging VM

Run the static/configuration validation locally and review the generated
staging environment template. Only after that review should the owner provide
staging secrets through the approved secret store and approve provisioning of a
dedicated Linux Judge0 VM plus isolated staging data services. No VM startup,
SSH, cloud provisioning, or deployment is part of this local step.

## Stage 3 Judge0 evidence status

Owner-provided inspection of stopped `coderzclub-judge0-1` recorded Ubuntu
22.04, kernel `6.8.0-1070-gcp`, Judge0 `1.13.1`, isolate `1.8.1`, hybrid
cgroups with v1 memory/CPU/pids controllers, and private binding
`10.20.0.6:2358`. Python Accepted, CPU time-limit, 64 MiB memory termination,
process restriction, and `enable_network=false` private API denial were
functionally observed.

This supports compatibility planning only. Direct active isolate cgroup
membership, cgroup event/cleanup evidence, image provenance, firewall state,
and production/staging network separation remain open. The VM is terminated;
no live capacity or reachability claim is made.

The controlled operations procedure is documented in
`STAGING_OPERATIONS_RUNBOOK.md`. HTTP users, queue throughput, and concurrent
Judge0 execution are separate capacity gates.
