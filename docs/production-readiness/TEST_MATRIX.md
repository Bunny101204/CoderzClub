# Test Matrix

## Verified local baseline (2026-11-08 validation pass)

| Suite | Result | Notes |
|---|---|---|
| Backend Maven full | **751 passed**, 0 failed, 0 skipped | Sequential; do not share `target/` |
| Frontend unit (`node --test`) | **208 passed** | |
| Playwright e2e | **26 passed** | Institution suite required reseed of empty `coderzclub_institution_e2e` |
| Mailpit + special ITs | **10 passed** | RS 8 + IndexPrivilege 1 + Mailpit 1 |
| Domain security unit | **16 passed** | Trusted host, ACTIVE-only, allowlist, cross-org deny |
| k6 institutional (post login fix) | **20→300 VU passed**, login_fail **0%** | Was 8.36% fail at 150 before fix; see `CAPACITY_REPORT.md` |
| k6 login-burst 150 | Passed (0.74% status-0 at ramp) | `perf/k6/login-burst.js` |
| k6 submission Mode A | 20 VU admission passed | No worker/Judge0 Mode B this pass |
| Perf seed | **20,000 users** + username/email indexes | `perf-v6-20k` on Mongo `27027` only |
| User lookup normalization | Passed | Exact username; lowercased email |

## Institution two-org Playwright coverage

Seed: `node scripts/institution-e2e/seed.mjs` → DB `coderzclub_institution_e2e`, fixtures under `e2e/.fixtures/`.

| Step | Verified |
|---|---|
| Org A owner login + institution dashboard | Browser |
| Create batch | Browser |
| Enroll student (username) | Browser |
| Grant batch coordinator | Browser |
| Create/publish batch-restricted contest + org CONTEST_ONLY problem | Browser |
| Eligible student accesses contest | Browser + API 200 |
| Non-enrolled org A student / org B student denied | API 403/404 |
| Org B owner cannot read org A batch/contest (incl. spoofed `X-Organization-Id`) | API 403/404 |
| Instructor coordinator can read batch report; cannot list org B members | API |
| Org B UI cannot see org A batches; switcher lacks org A | Browser |
| Multi-org user switches A↔B; active-context API for both | Browser + API |
| Public branding fallback + problems catalog | Browser + API |

## Existing coverage (baseline)

| Area | Backend | Frontend unit | Playwright | Notes |
|---|---|---|---|---|
| Auth login/register/password reset | Partial | Partial | Partial | Password-reset controller tests; email SMTP mocked or absent |
| Problems / catalog cache | Strong | Partial | Partial | Org-admin scoped listing includes CONTEST_ONLY |
| Submissions / jobs / limits | Strong | Strong | Strong | Includes recovery and duplicate-submit e2e |
| Contests boundary/scoring | Strong | Strong | Partial + institution live | |
| Batches / coordinators / enrollment | Strong | Strong | batch-smoke + institution live | |
| Bundles / access | Strong | Strong | Partial | |
| Security filter chains | Strong | — | — | Org contest routes allow USER+ADMIN with in-controller tenant checks |
| Email SMTP real delivery | Missing | — | — | Must use GreenMail/mocks locally |
| Multi-tenant isolation | Strong (`OrganizationTenancySecurityTest`, batch/contest isolation, history, worker context) | Org switcher + institution dashboard | **Live two-org** | Cross-tenant API negatives in Playwright |
| Legacy submission verdicts | `LegacySubmissionVerdictSecurityTest` + job pathway | UI uses submission-jobs | — | Client ACCEPTED/statusId ignored |
| Org-private points reconcile | Dry-run default + optional idempotent apply + audit collection | — | — | Never apply in production without approval |
| White label | Branding PATCH validation | `branding.js` + CSS vars + institution settings | Public fallback e2e | Custom domains documented, not enabled |
| Load / capacity | Missing | — | — | Plan in PERFORMANCE_PLAN.md |

## Environment rules for tests

- Mongo/Redis/Rabbit on loopback only.
- Institution E2E uses `coderzclub_institution_e2e` only — never Atlas production.
- Playwright Vite origin `http://127.0.0.1:4179` must be in `APP_CORS_ALLOWED_ORIGINS` for live API.
- Never real SMTP credentials in CI; use mocks or GreenMail.
- Never print JWT secrets or mail passwords in logs/assertions.
- Special-IT cleanup (when finished): `docker compose -p coderzclub-special-its -f docker-compose.special-its.yml down -v`
