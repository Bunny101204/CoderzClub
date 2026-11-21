# GCP load-test quickstart

This is an execution checklist, not authorization. The workflow uses project
`coderzclub-load-test` and zone `asia-south1-a`, but does not treat the stopped
`coderzclub-api-1`, `coderzclub-worker-1`, `coderzclub-infra-1`, or
`coderzclub-judge0-1` VMs as staging. The existing `coderzclub-loadgen-1` is
usable only if the read-only preflight proves it has the dedicated loadgen role
and service account.

## Smallest safe setup

Use three newly named, dedicated staging VMs with the prefix
`coderzclub-staging-`: one app VM co-locating API+worker, one infra VM, and one
Judge0 VM. The existing loadgen may remain dedicated to k6 after preflight. The infra VM may host only isolated
staging Redis/RabbitMQ and a disposable Mongo deployment, or point at separately
provisioned staging services. It must not reuse production Atlas, Redis, Rabbit,
or credentials. Use separate `coderzclub-staging-app`,
`coderzclub-staging-infra`, and `coderzclub-staging-judge0` service accounts,
plus a separate `coderzclub-loadgen` service account.

The proposed temporary VM plan is:

| Role | Machine | Boot disk | On-demand compute estimate |
|---|---|---:|---:|
| API + worker | `n2-standard-4` | 50 GiB balanced PD | `$0.194236/hour` |
| Infra | `n2-standard-2` | 100 GiB balanced PD | `$0.097118/hour` |
| Judge0 | `n2-standard-4` | 100 GiB balanced PD | `$0.194236/hour` |

Indicative total is `$0.485590/hour` compute plus about `$0.034247/hour`
balanced disk, or approximately `$0.52/hour` / `$379/month` at 730 hours,
before egress, logging/monitoring, Secret Manager, snapshots, discounts, or
the existing loadgen. Prices are planning estimates from the official [GCP
Compute Engine general-purpose pricing](https://cloud.google.com/products/compute/pricing/general-purpose?hl=en)
and [disk pricing](https://cloud.google.com/compute/disks-image-pricing); owner
must confirm the asia-south1 quote before approval.

Co-location makes this a fast isolation pilot, not a production topology:
API and worker contend for CPU, heap, file descriptors, disk, and network;
infra services contend with each other; and Judge0 remains separate. Report
app-VM saturation separately and do not claim independent API/worker capacity.

The staging app must use database `coderzclub_staging` (or another explicitly
approved staging-only database), a staging-only Redis database, Rabbit vhost
`/coderzclub-staging`, staging-specific queue names, a staging JWT, and a
private Judge0 endpoint. The 20,000 users are synthetic only; seed from
`scripts/perf/seed-institutional.mjs` into the staging database after a
read-only target check. Never copy production users.

## Owner commands

All cloud actions below are guarded. `Plan` is local-only. `Preflight` and
`Inspect` are read-only cloud actions. `Start`, `Test`, and `Stop` require
`-Approve` and only target the dedicated prefix plus the separately verified
loadgen VM.

```powershell
# Local plan; no gcloud call.
powershell -ExecutionPolicy Bypass -File scripts/ops/gcp-load-test.ps1 -Action Plan

# First approved cloud action: read-only inventory and label/IAM check.
powershell -ExecutionPolicy Bypass -File scripts/ops/gcp-load-test.ps1 -Action Preflight -ApproveReadOnly

# Dry-run provisioning plan with machine sizes, disks, and cost assumptions.
powershell -ExecutionPolicy Bypass -File scripts/ops/gcp-load-test.ps1 -Action Plan -Topology ThreeVm

# First mutating command; only after owner approval of network, cost, IAM,
# firewall, secrets, and the secure Judge0 plan. Not executed by this task.
powershell -ExecutionPolicy Bypass -File scripts/ops/gcp-load-test.ps1 -Action Provision -Topology ThreeVm -Network <staging-vpc> -Subnetwork <staging-subnet> -ConfigureIam -ConfigureFirewall -Apply -Approve -ConfirmStagingProvision

# Dry-run reviewed local build/artifact transfer plan.
powershell -ExecutionPolicy Bypass -File scripts/ops/prepare-staging-artifact.ps1 -Build

# After provisioning, transfers only the reviewed jar and verifies its hash.
powershell -ExecutionPolicy Bypass -File scripts/ops/prepare-staging-artifact.ps1 -Build -Apply -Approve

# After recording the private IPs from the provisioned app/infra/Judge0 VMs.
powershell -ExecutionPolicy Bypass -File scripts/ops/initialize-staging-secrets.ps1 -InfraIp <infra-private-ip> -Judge0Ip <judge0-private-ip> -Apply -Approve
powershell -ExecutionPolicy Bypass -File scripts/ops/bootstrap-staging-gcp.ps1 -Action Bootstrap -Apply -Approve
powershell -ExecutionPolicy Bypass -File scripts/ops/bootstrap-staging-gcp.ps1 -Action Health -Apply -Approve
powershell -ExecutionPolicy Bypass -File scripts/ops/seed-staging-synthetic.ps1 -Action Seed -Apply -Approve -ConfirmSyntheticOnly
powershell -ExecutionPolicy Bypass -File scripts/ops/seed-staging-synthetic.ps1 -Action Verify -Apply -Approve -ConfirmSyntheticOnly
powershell -ExecutionPolicy Bypass -File scripts/ops/prepare-loadgen-gcp.ps1
powershell -ExecutionPolicy Bypass -File scripts/ops/collect-staging-telemetry.ps1 -ApproveReadOnly

# Start only after the preflight report, staging secrets, firewall, and private
# dependency/Judge0 evidence have been approved.
powershell -ExecutionPolicy Bypass -File scripts/ops/gcp-load-test.ps1 -Action Start -Approve

# Run this on the dedicated loadgen after staging health and queue baselines pass.
powershell -ExecutionPolicy Bypass -File scripts/ops/gcp-load-test.ps1 -Action Test -TestStage admission -Vus 50 -Approve
powershell -ExecutionPolicy Bypass -File scripts/ops/gcp-load-test.ps1 -Action Test -TestStage admission -Vus 100 -Approve
powershell -ExecutionPolicy Bypass -File scripts/ops/gcp-load-test.ps1 -Action Test -TestStage admission -Vus 200 -Approve

# Separate real-verdict stage; start at 2 VUs and increase only after drain gates.
powershell -ExecutionPolicy Bypass -File scripts/ops/gcp-load-test.ps1 -Action Test -TestStage verdict -Vus 2 -Approve

# Read-only VM status during observation.
powershell -ExecutionPolicy Bypass -File scripts/ops/gcp-load-test.ps1 -Action Inspect -ApproveReadOnly

# Stop dedicated staging/loadgen VMs after queues drain; no disk/data deletion.
powershell -ExecutionPolicy Bypass -File scripts/ops/gcp-load-test.ps1 -Action Stop -Approve
```

The wrapper prints the exact k6 command for the loadgen; it does not execute k6.
The k6 scripts are `perf/k6/staging-admission.js` and
`perf/k6/staging-verdict.js`. They require explicit environment approvals,
reject production-like targets, use one job per VU, and treat only HTTP 202 as
admission. The verdict script requires a separate real-verdict approval and
waits for terminal `COMPLETED`/`FAILED` status.

Clean shutdown is `bootstrap-staging-gcp.ps1 -Action Stop -Apply -Approve`
followed by `gcp-load-test.ps1 -Action Stop -Apply -Approve`. This stops
services and VMs but preserves persistent disks and data; disks continue to
incur storage charges after VM shutdown. Deleting the three staging VMs,
disks, firewall rules, service accounts, and Secret Manager versions is a
separate owner-approved teardown and must wait until artifacts, queue evidence,
and results are exported.

## Gates

Before each admission stage, record API/worker readiness, Mongo/Redis/Rabbit
targets, Rabbit `messages_ready` and `messages_unacknowledged`, JVM CPU/heap,
VM CPU/memory, API HTTP p95, enqueue p95, 4xx/5xx/transport errors, and Judge0
health. Require the queue to return to its pre-stage baseline before increasing
VUs. Abort on unexpected growth, retry/DLQ growth, auth failures, transport
errors, or p95 threshold breach.

Admission testing may run with worker consumption paused, but that intentionally
leaves staging jobs queued. Do not purge them. For real verdict testing, require
Judge0 cgroup/isolate evidence from `ops/staging/JUDGE0_SANDBOX_CHECKLIST.md`,
then start the worker and prove queue drain plus accepted verdicts at low
concurrency before increasing load. The stopped existing Judge0 VM is not
approved by its name or state alone.

## Additional infrastructure/blockers

Provisioning now has a guarded three-VM plan and optional VM/IAM/firewall
apply path. It deliberately does not create secret values, seed data, bootstrap
Docker/Judge0, or invent a remote service manager. A reviewed host bootstrap is
still required before artifact transfer becomes a deployment. The fastest safe
path therefore needs three dedicated staging VMs or equivalent isolated
services, a separate staging secret set/service accounts, private firewall
rules, and a verified Judge0 runtime. No production VM can be silently
repurposed.
