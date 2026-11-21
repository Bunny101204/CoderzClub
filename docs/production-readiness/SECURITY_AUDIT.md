# Security Audit (Baseline)

Date: 2026-11-08. Code review only; no penetration test against production.

## Critical / High

| Finding | Risk | Status |
|---|---|---|
| No multi-tenant isolation | Institutions cannot be safely hosted together | Phase A largely closed for problems/batches/contests/submissions/reports/bundles/workers with negative tests; continue reviewing new endpoints |
| Client-forged submission verdicts | Points/solved/leaderboard inflation via `POST /api/submissions` | **Closed** — legacy endpoint stores `UNVERIFIED` only; rewards require job/worker pathway |
| Org-private history after membership removal | Former members reading private code/problems | **Closed (conservative)** — deny org-private history/jobs; public own history retained; see `SUBMISSION_HISTORY_POLICY.md` |
| Historical org-private global points | Past awards may already inflate `totalPoints` | **Mitigated** — dry-run default; optional idempotent write-back (`enabled` + `dry-run=false` + `apply=true`) with audit collection; pending retries clamp to 0; **never apply in production without explicit approval** |
| Special IT fixtures | Privilege / RS tests previously unverifiable locally | Isolated Compose project on `27018`/`27019`; does not touch developer `27017` |
| Platform ADMIN is global | Org admins would inherit platform power if reused | Mitigated — org roles are separate; APIs reject `ADMIN` escalation; UI does not grant platform Admin |
| Password-reset link uses backend API URL | Phishing/UX confusion; may not land on intended frontend | **Closed** — frontend `/auth?resetPasswordToken=` via `VerificationLinks` |
| SMTP failures / misconfig | Account verification blocked; AuthFailedException historically on worker | **Mitigated locally** — Mailpit path; worker mail forced off; `MANAGEMENT_HEALTH_MAIL_ENABLED=false`; production validation in `EMAIL_OPERATIONS.md` |
| Some public GETs on problems/bundles | Intended for practice catalog; ensure CONTEST_ONLY / private content never leaks | Monitor — ContestAccessService must remain authoritative |
| SSE submission events permitAll with ticket | Ticket design must remain unguessable and short-lived | Monitor |

## Medium

| Finding | Risk | Status |
|---|---|---|
| Role stored as free-form string | Case variants (`ADMIN`/`admin`) handled ad hoc | Improve with enum/normalization |
| CSV import | Formula injection / oversized uploads | Partially mitigated by size limits; add CSV safety review |
| CORS / origins | Configured via `APP_CORS_ALLOWED_ORIGINS` | Local Playwright needs `:4179` origins; production pair must remain exact and exclude local ports |
| Custom Host / X-Forwarded-Host spoofing | Tenant confusion / cache bleed | **Hardened** — resolve only ACTIVE + `public-resolution-enabled` (default false); `TrustedRequestHostResolver` ignores `X-Forwarded-Host` unless trusted peer allowlist; see `CUSTOM_DOMAINS.md` |
| Branding asset XSS | SVG/script injection via logo/favicon URL | HTTPS-only; `.svg` / `javascript:` / `data:` rejected server+client |
| Actuator prometheus permitAll | Metrics exposure on management port | Bind management to private IP only (current GCP pattern) |
| Student personal data in reports | Emails may appear in admin batch reports | Minimize; document retention |

## Age / privacy (human review required)

- Platform may serve minors via schools. Legal consent, parental policies, and retention need institutional review.
- Existing Privacy/Terms pages are present; verify they match actual data practices before institutional launch.

## Institution isolation (local verification 2026-11-08)

Two synthetic organizations exercised in Playwright + direct API negatives:

- Org B cannot read/modify Org A batches/contests even with spoofed `X-Organization-Id`.
- Non-enrolled / cross-org students cannot open batch-restricted contests.
- Instructor coordinator grants are batch-scoped; org B membership APIs remain forbidden.
- Backend remains the authoritative security boundary; UI mirrors permissions only.

## Hard rules for upcoming work

- Every org-scoped API checks membership + role server-side.
- Negative tests for IDOR and privilege escalation before marking tenant features complete.
- Never log SMTP passwords, JWT secrets, Mongo URIs, or bearer tokens.
- Do not weaken security to green tests.
- Do not run org-private points apply against production without written approval.
