# Production deployment preflight (read-only)

**Do not deploy, mutate production config, apply indexes, or upgrade cloud services from this document.**  
Secrets must never be printed. Values below are **names only**.

Updated: 2026-11-08

## Topology (from repo docs — re-verify live before any swap)

| Component | Expected location | Notes |
|---|---|---|
| API | GCP `coderzclub-api-1` (`10.20.0.4`) :8080 / mgmt :8081 | Profile `api` |
| Worker | GCP `coderzclub-worker-1` (`10.20.0.7`) :8082 / mgmt :8083 | Profile `worker` |
| Frontend | Cloudflare (`coderzclub.site`) | API `https://api.coderzclub.site` |
| MongoDB | Atlas DB `coderzclub` | Never use for local tests |
| Redis / RabbitMQ / Judge0 | Private VPC peers | Not public |

See also: `DEPLOYMENT_CHECKLIST.md`, `EMAIL_OPERATIONS.md`, `CAPACITY_REPORT.md`.

## 1. Required environment variables (names)

### API (`SPRING_PROFILES_ACTIVE=api`)

- `MONGODB_URI`, `JWT_SECRET`
- `SPRING_REDIS_HOST` (+ port; preferred by `spring.redis.host`).
  `SPRING_DATA_REDIS_HOST` remains accepted as a compatibility fallback for the
  existing production Compose configuration.
- `RABBITMQ_HOST`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`
- `JUDGE0_API_URL` (+ `JUDGE0_AUTH_MODE` / key as applicable)
- `APP_FRONTEND_URL`, `APP_BACKEND_URL`, `APP_CORS_ALLOWED_ORIGINS`
- Mail (recommended on API): `SPRING_MAIL_ENABLED`, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_SMTP_AUTH`, `SPRING_MAIL_SMTP_STARTTLS`, `APP_EMAIL_FROM`
- `MANAGEMENT_HEALTH_MAIL_ENABLED=false` unless SMTP probe should affect readiness
- Keep `APP_CUSTOM_DOMAINS_PUBLIC_RESOLUTION_ENABLED=false` until DNS/TLS verified

### Worker (`SPRING_PROFILES_ACTIVE=worker`)

- Same Mongo / Redis / Rabbit / Judge0 as API
- `WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY` (default 2)
- SMTP **not** required; prefer mail health disabled

### Frontend / edge

- API base URL pointing at production API
- Cloudflare deployment identity + rollback target recorded by owner

## 2. Database indexes vs expected

Expected unique indexes on `users`: **`username`**, **`email`** (Spring `@Indexed(unique=true)` names).

Local evidence:

| Database | Unique username/email | Collisions (case-insensitive) |
|---|---|---|
| `coderzclub_perf` (27027) | Present | None (20,000 users) |
| `coderzclub_institution_e2e` (27017) | Present after seed | None |
| Production Atlas | **Not inspected** (no prod writes/reads beyond public API) | Unknown |

Read-only scanner (local/staging only):

```bash
node scripts/ops/account-index-preflight.mjs
# MONGODB_URI=... MONGODB_DB=... node scripts/ops/account-index-preflight.mjs
```

**Do not run index creation against Atlas without owner approval.** Legacy collision groups must be resolved before unique-index create.

## 3. Legacy account normalization risks

- New writes: email lowercased + trimmed; username trimmed (case preserved).
- Login/register/password-reset lookups: **exact indexed match first**, rare case-insensitive fallback for legacy mixed-case rows (`UserService`).
- Risk if production contains case-insensitive duplicate username/email pairs: unique index create fails; registration may collide unpredictably.
- Profile update path does **not** change username/email (bio/location/website only) — reduces accidental unique-key churn.

## 4. SMTP configuration readiness

- Local demo: Mailpit (`127.0.0.1:1025` / UI `:8025`) — verified path for verification + reset emails.
- Production: owner must validate real SMTP keys in secret manager and a safe mailbox send after cutover (`EMAIL_OPERATIONS.md`).
- Worker should not depend on SMTP for submission processing.

## 5. Database backup prerequisites

Before any production index/migration/deploy:

1. Confirm recent successful Atlas snapshot / PITR window.
2. Record restore procedure owner + last restore drill date (if any).
3. Prefer staging restore drill of the migration plan before prod.

## 6. Worker concurrency and queues

- Default per-JVM Judge0 caps: worker `WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY=2`, API `JUDGE0_API_MAX_CONCURRENCY=2` (`SUBMISSION_EXECUTION_SCALING.md`).
- Queue names must match across API+worker (`coderzclub.submissions`, contest-run queue). Deploy **worker before** API when changing consumers.
- Rabbit depth + QUEUE_AGE / 429 signals are the release watch items.

## 7. Judge0 capacity constraints

- HTTP VU success ≠ concurrent Judge0 executions.
- Cluster Judge0 throughput ≈ `worker_replicas × WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY` (plus API run path separately).
- Local demo Judge0: `docker-compose.judge0-local.yml` on `127.0.0.1:2358` only.

## 8. Health / readiness endpoints

| Check | Endpoint |
|---|---|
| Public smoke | `GET /api/test`, `GET /api/problems/test` |
| API readiness | `GET http://<api-mgmt>:8081/actuator/health/readiness` → `UP` |
| Worker readiness | `GET http://<worker-mgmt>:8083/actuator/health/readiness` → `UP` + consumer started logs |
| Public site user count (non-secret) | `GET https://api.coderzclub.site/api/users/leaderboard` → `total` (observed **1015** on 2026-11-08) |

## 9. Deployment and rollback ordering

1. Verify image digests + free `coderzclub-api-before-<releaseId>` name.
2. Confirm indexes/migrations approved (or none).
3. Start/verify **worker** image first.
4. Swap **API**; park previous under `coderzclub-api-before-<releaseId>`.
5. Smoke login, problems, one submission job, institution dashboard.
6. Frontend Cloudflare rollback via wrangler (owner-operated) if needed.

Rollback: stop only the new live container when digests match plan; restore parked container. Never delete historical `coderzclub-api-before-*`.

## Owner-approved actions still required for first institution onboarding

1. Staging SMTP validation + production SMTP enablement decision.
2. Production index preflight on Atlas (read-only) then approved index apply if missing.
3. Production deploy of institution/tenancy-capable API+worker images.
4. DNS/TLS/Cloudflare decisions for any custom domain (remain **off** until verified).
5. Backup/restore confirmation window.
6. Explicit approval for any Atlas change, secret update, or public DNS change.
