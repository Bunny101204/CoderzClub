# Staging pilot operations runbook

This is a controlled, owner-approved staging procedure. It is not a
production deployment procedure. Do not start a VM, connect by SSH, or run
these commands until the owner has approved the exact staging project,
resource identities, secrets, and private firewall paths.

## Preconditions

- `coderzclub-judge0-1` is confirmed dedicated to staging, or a separate
  staging Judge0 host is selected. A production-attached host is not reused
  merely because it is stopped.
- API/worker can reach private `10.20.0.6:2358` only if firewall and source
  identity evidence proves staging isolation. Otherwise use a dedicated
  staging Judge0 address.
- Mongo uses a staging account and database `coderzclub_staging`.
- Redis and RabbitMQ use staging instances/vhosts/credentials and unique
  persistent volumes.
- Staging JWT, broker, Redis, Mongo, SMTP, and Judge0 secrets come from the
  approved secret store and never appear in Git or command output.
- Read-only Mongo index preflight completed against the staging database.
- Judge0 functional checks pass; direct active-cgroup evidence is recorded
  before execution capacity is claimed.

## Controlled startup

1. Validate image digests and the staging environment.
2. Start staging data services and verify health without deleting volumes.
3. Start the worker and verify management readiness plus both Rabbit consumers.
4. Start the API and verify readiness plus aggregate dependency health.
5. Run the synthetic-institution smoke flow below.

Validation-only command shape:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ops/start-staging.ps1
```

Only after explicit owner approval on the selected staging host:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ops/start-staging.ps1 -Apply
```

The script must receive `.env.staging`, retain project `coderzclub-staging`,
and reject production endpoints or placeholder secrets.

## Smoke test

- `/api/test` and `/api/problems/test` return HTTP 200.
- API readiness and aggregate health are UP.
- Worker readiness is UP with submission and contest-run consumers active.
- Synthetic user login succeeds and staging CORS is enforced.
- Organization → batch → contest → submission returns `ACCEPTED`.
- Report/CSV export succeeds and cross-organization access is denied.
- Password reset reaches the approved staging mailbox or Mailpit.
- TLE, MLE, process-limit, and network-denial cases match Judge0 evidence.
- Queue depth returns to baseline with no unexpected DLQ growth.

An HTTP smoke pass is not a Judge0 concurrency result.

## Rollback

1. Stop accepting new staging submissions.
2. Preserve Mongo, Redis, RabbitMQ volumes, and queued messages.
3. Stop only the new staging API and worker containers.
4. Restore the previous approved staging image/configuration.
5. Start worker, verify consumers, then start API and verify health.
6. Re-run smoke and record queue drain/retry behavior.

Never run production swap helpers, `down -v`, database drops, queue purges,
or local Judge0 no-cgroup scripts during rollback.
