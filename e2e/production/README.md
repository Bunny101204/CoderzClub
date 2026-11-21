# Opt-in live production Playwright suite

This suite targets only `https://coderzclub.site` and
`https://api.coderzclub.site`. It is separate from `playwright.config.js`,
has no local `webServer`, and never invokes `scripts/institution-e2e/seed.mjs`.

## Safety model

- The default command performs authenticated read-only prerequisite checks.
- Data creation is skipped unless the wrapper is invoked in write mode and
  both production confirmation variables are present.
- The initial submission burst is hard-capped at five independently
  authenticated students. The credential format accepts 5-10 student
  accounts so later approved stages do not need a secret-file redesign.
- Every organization, membership, batch, coordinator grant, assignment, and
  contest created by a write run is recorded in a local resource ledger.
- There is no automatic cleanup. Any later cleanup must use that run's ledger
  and separate owner approval; it must never infer resources by name alone.
- Passwords and tokens are not printed. Diagnostics redact token-shaped data
  and configured passwords.
- `artifacts/playwright-production/` is ignored because Playwright traces are
  authenticated evidence and can contain browser session material. Never
  publish, attach, or commit that directory without a security review.

## Prerequisites

Obtain owner approval for a production write rehearsal and provision verified,
synthetic-only accounts for:

- one platform `ADMIN` authorized to create the test organization;
- one organization owner and one coordinator;
- 5-10 students (the initial run uses exactly five);
- one student added to the test organization but deliberately not enrolled;
- one College B outsider who belongs to a separate synthetic organization.

All accounts must be active, email-verified, independently authenticated, and
must not be personal or institutional production users. Also obtain an
owner-approved, non-sensitive production test problem that is assignable to a
new organization. Its configured correct and incorrect programs must compile
under the selected Judge0 language and deterministically produce `ACCEPTED`
and `WRONG_ANSWER`.

Before write mode, confirm production API/worker/RabbitMQ/Judge0 health and
capacity for five submissions. This suite verifies observed jobs; it does not
claim Judge0 capacity beyond the five-student run.

## Local secret file

Copy `e2e/production/credentials.example.json` to the ignored path:

```powershell
Copy-Item e2e/production/credentials.example.json e2e/.fixtures/production-live.json
```

Populate it from the approved secret source. Do not paste its contents into
terminal output, tickets, reports, or chat. Alternatively point to an ignored
or external file:

```powershell
$env:PRODUCTION_E2E_CREDENTIALS_FILE="C:\secure\coderzclub-production-live.json"
```

## Execution

Authenticated read-only prerequisite check:

```powershell
npm run test:e2e:production:read
```

Owner-approved write run:

```powershell
$env:PRODUCTION_E2E_ENABLED="true"
$env:PRODUCTION_E2E_CONFIRM="coderzclub.site"
npm run test:e2e:production:write
```

Clear the opt-in values after the run:

```powershell
Remove-Item Env:PRODUCTION_E2E_ENABLED -ErrorAction SilentlyContinue
Remove-Item Env:PRODUCTION_E2E_CONFIRM -ErrorAction SilentlyContinue
```

The wrapper generates a unique run id. Evidence is written under
`artifacts/playwright-production/<run-id>/`, including HTML results, traces,
screenshots, redacted browser/HTTP errors, timing metrics, summary JSON and
Markdown, and `resource-ledger.json`.

## Evidence and optional persona review

Use `summary.md`, screenshots, verdict timings, leaderboard/report evidence,
and the redacted error files for an optional AI-assisted review from the
platform-admin, organization-owner, coordinator, student, unenrolled-student,
and outsider perspectives. Supply only the reviewed/redacted evidence; never
supply traces or the credential file to an external model. Persona discussion
must distinguish observed evidence from recommendations and must not invent
successful steps.
