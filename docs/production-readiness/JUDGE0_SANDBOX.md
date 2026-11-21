# Judge0 sandbox policy (local demo vs production)

## Absolute rule

**Disabling isolate `--cg` (cgroup resource limits) is not an approved production security configuration.**  
It weakens CPU/memory/process accounting that the sandbox relies on. Do not bake that workaround into production images, GCP VMs, Cloudflare, Atlas, or release compose files.

## What happened on the demo laptop

- Host: Docker Desktop / WSL2 with **cgroup v2** (`docker info` → Cgroup Version 2).
- Stock image `judge0/judge0:1.13.1` ships isolate **1.8.x** (cgroup **v1** paths). On cgroup v2, `--cg` init fails → Internal Error / missing `/box/...`.
- For **same-day local demo only**, running containers were patched in-place via:
  - `scripts/ops/patch-judge0-no-cgroup.sh`
  - `scripts/ops/fix-judge0-init.sh`
- Those scripts **mutate the live container filesystem**. They are **not** referenced by `docker-compose.production.yml`, k8s manifests, or GCP release helpers. They require an explicit local opt-in env var (see script headers).

## Containment (cannot accidentally deploy)

| Artifact | Production risk |
|---|---|
| `docker-compose.judge0-local.yml` | Local-only project name `coderzclub-judge0`; loopback bind `127.0.0.1:2358`; not used by production compose |
| `patch-judge0-no-cgroup.sh` / `fix-judge0-init.sh` | Manual `docker exec` only; refuse unless `CODERZCLUB_ALLOW_INSECURE_LOCAL_JUDGE0=1` |
| Backend Java / worker images | **Unchanged** — no `--cg` disable in application code |
| Production Judge0 | Must keep full isolate cgroup limits |

A production deploy that pulls stock or approved Judge0 images **does not** include these patches unless an operator manually re-runs the local scripts against production containers (forbidden).

## Durable approaches that retain safe isolation

Prefer these for staging/production (keep `--cg` / equivalent resource isolation):

1. **Linux VM or bare metal with cgroup v1** (documented Judge0 path)  
   - Kernel: `systemd.unified_cgroup_hierarchy=0` (or distro equivalent).  
   - Verify: `docker info` shows Cgroup Version **1**, and `/sys/fs/cgroup/memory` exists.  
   - Run official `judge0/judge0` with `privileged: true` as upstream documents.  
   - **Keeps** isolate memory/CPU/process cgroup limits.

2. **Official/upstream cgroup v2 + isolate v2 integration (when approved)**  
   - Track Judge0 work such as [judge0/judge0#543](https://github.com/judge0/judge0/issues/543) and isolate-v2 / cgroupv2 PRs (e.g. community PR adapting isolate **v2.6** with cgroup delegation at container start).  
   - Staging must prove: Accepted verdicts **and** cgroup memory/CPU enforcement still active (not “flags stripped”).

3. **Dedicated Judge0 host separate from the API VM**  
   - Private VPC only; no public Judge0; capacity sized via `WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY` × worker replicas (`SUBMISSION_EXECUTION_SCALING.md`).

**Not approved:** shipping the local “empty `@cgroups`” patch, or any image whose runtime simply omits `--cg` to make Windows Docker Desktop green.

## Staging gate before production Judge0 claims

- [ ] Host cgroup mode documented (v1 legacy **or** verified isolate-v2/cgroupv2 build).
- [ ] Sample submissions Accepted under load.
- [ ] Confirm isolate still passes `--cg` (or isolate-v2 equivalent) in worker command lines / logs.
- [ ] Memory/time limits observed (intentional TLE/MLE cases).
- [ ] No local patch scripts in the release runbook.
