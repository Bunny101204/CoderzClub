# Deployment Checklist

**No production changes without explicit owner approval.**  
Updated: 2026-11-18 — post PR #2 / PR #4 merge on `main` (`a6bade3`).

This checklist is read-only guidance. Checking a box does **not** authorize deploy or problem import.  
It contains **no credentials, private IP addresses, or internal hostnames**.

---

## Current topology (re-verify live before any swap)

| Role | Expected location | Notes |
|---|---|---|
| API | Private cloud VM (owner inventory) | App + management ports private only |
| Worker | Private cloud VM (owner inventory) | App + management ports private only |
| RabbitMQ / Redis / Judge0 | Private VPC peers | Do not expose publicly |
| Frontend | Cloudflare Worker / public site | API on the production API hostname |
| MongoDB | Managed Atlas database `coderzclub` | Never use for local tests |

---

## A. Pre-approval gates (code / CI)

- [x] `main` contains PR #2 (curriculum) + PR #4 (release hardening) — merge commit `a6bade3`
- [x] GitHub CI on `a6bade3`: backend-tests, frontend-build, docker-build, manifest-validation, index-migration-validation, Workers Builds — **success**
- [x] Student languages gated to Python **71** + C++ **54** (backend catalog + frontend templates)
- [x] Unverified languages (Java/JS/Go/…) remain rejected (do not weaken tests)
- [x] Problem-bank seeder refuses production API hosts without `--allow-production`
- [x] Private bank (oracles, hidden cases, credentials) stays under `.local/problem-bank/` (gitignored)
- [ ] Owner explicitly approves **backend/worker image cutover**
- [ ] Owner explicitly approves **187-problem production import** (separate from image cutover)

## B. Production env hygiene (do not copy staging)

Confirm production does **not** inherit `cz-pb-staging` values:

- [ ] Queue names are production `coderzclub.submissions*` / contest-run names (not `cz.pb.staging.*`)
- [ ] Submission limits are product defaults (or an owner-approved raise) — **not** staging inflated daily/per-problem/cooldown values
- [ ] `SUBMISSION_LIMIT_REDIS_FAIL_OPEN=false`
- [ ] `APP_REQUIRE_SECURE_SECRETS=true` and JWT passes `ProductionSecretValidator` (≥64 bytes, non-placeholder)
- [ ] `JUDGE0_API_URL` points at VPC Judge0 only; sandboxes keep network disabled
- [ ] `WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY` set intentionally (default 2) — document replica math
- [ ] `APP_CUSTOM_DOMAINS_PUBLIC_RESOLUTION_ENABLED=false` until DNS/TLS verified
- [ ] CORS / frontend URLs are exact production origins (no local ports)

## C. Database backup / indexes

- [ ] Latest Atlas snapshot **successful**; PITR window recorded
- [ ] Restore owner + last restore-drill date recorded (or drill scheduled)
- [ ] Read-only account index preflight reviewed if applying unique indexes (`scripts/ops/account-index-preflight.mjs`)
- [ ] **Do not** run `backend/mongodb-indexes.js` blindly (drop/TTL risk)
- [ ] No Mongo migration required for this cutover, or migration is separately approved

## D. Image cutover order (only after §A owner approval)

1. [ ] Record live image digests (API + worker) as rollback targets
2. [ ] Confirm parked previous-container name for this release ID is free
3. [ ] Deploy **worker** image first (consumers before publishers spike)
4. [ ] Deploy **API** image; park previous under the release rollback name
5. [ ] Public health probe OK
6. [ ] Private readiness UP on management ports
7. [ ] Smoke: login, problems list, **one Python submission job → terminal verdict**, Java submit → **400**
8. [ ] Watch 30–60 min: Rabbit depth, DLQ, Judge0 errors, 429/QUEUE_AGE, JVM heap/GC
9. [ ] Frontend Cloudflare deploy only if this release requires it; record wrangler rollback target

Helpers (gated; not auto-authorization):

- Parameterized API swap scripts under `ops/` (release-specific; owner-run)
- Submission reliability runbook: `ops/release/README.md`
- Do not reuse historical October batch-mgmt tags for new releases

## E. Problem-bank import (only after separate owner approval)

- [ ] Production backup confirmed the same day
- [ ] Dry-run against production API succeeds with zero unexpected diffs
- [ ] Import in batches of 10–20 with resume/idempotency
- [ ] After each batch: verify API catalog counts + one Python ACCEPTED on a sample problem
- [ ] Keep hidden tests / reference solutions out of git and out of logs
- [ ] Machine-readable success/skip/fail report retained privately
- [ ] Stop on first systemic Judge0/API failure; do not force remaining batches

Seeder reminder:

```text
# Dry-run only until owner approval + backup
node scripts/problem-bank/seed.mjs --api-base <production-api-base>
# Apply requires BOTH --apply AND --allow-production after backup verification
```

## F. Rollback

### API / worker

- [ ] Stop only the new live container when digest matches the failed release
- [ ] Rename/start the parked previous container for that release ID
- [ ] Never delete historical parked rollback containers
- [ ] Re-check health + one submission smoke after rollback

### Frontend

- [ ] Cloudflare wrangler rollback to recorded prior deployment (owner-operated)

### Data

- [ ] Prefer forward fix for content mistakes; Atlas PITR / snapshot restore only with owner approval
- [ ] Problem-bank: use seeder skip/idempotency + documented compensating deletes — never raw prod Mongo edits by default

## G. Staging rehearsal (recommended before prod)

Order on **staging** only:

1. Indexes verified on staging DB
2. Redis + Rabbit healthy; queue names match API/worker
3. Worker then API
4. Smoke: login, catalog, Python ACCEPTED, Java 400, institution dashboard if in scope
5. Optional: re-run bounded k6 against staging URL with non-prod credentials

Local pb-staging (loopback API) already validated Phase 6–7; it is **not** a substitute for cloud staging cutover.

---

## Required production env keys (names only — never commit values)

### API (`SPRING_PROFILES_ACTIVE=api`)

- `MONGODB_URI`, `JWT_SECRET`
- `SPRING_REDIS_HOST` / port (or `SPRING_DATA_REDIS_*` compatibility)
- `RABBITMQ_HOST`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`
- `JUDGE0_API_URL` (+ auth mode/key as applicable)
- `APP_FRONTEND_URL`, `APP_BACKEND_URL`, `APP_CORS_ALLOWED_ORIGINS`
- Mail (recommended): `SPRING_MAIL_*`, `APP_EMAIL_FROM`
- `MANAGEMENT_HEALTH_MAIL_ENABLED=false` unless SMTP should gate readiness
- `APP_REQUIRE_SECURE_SECRETS=true`

### Worker (`SPRING_PROFILES_ACTIVE=worker`)

- Same Mongo/Redis/Rabbit/Judge0 as API
- `WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY`
- SMTP not required; prefer mail health disabled

---

## Owner approval required before

- Production secret updates
- Production SMTP enablement
- Cloudflare / public DNS / TLS changes
- Enabling public custom-domain resolution
- Production index scripts / migrations
- Production API/worker image cutover
- Production import of the 187 DSA problems
- Raising Judge0 sandbox limits or enabling unverified languages
