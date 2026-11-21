# Secure staging Judge0 checklist

This checklist is for a dedicated Linux staging host. It deliberately does not
include the local `patch-judge0-no-cgroup.sh` or `fix-judge0-init.sh` scripts.

Before connecting the application to Judge0, record evidence for:

## Host evidence commands

Run these on the candidate Linux host before installing or starting Judge0;
they are read-only checks:

```sh
uname -a
cat /etc/os-release
docker version
docker info --format 'Cgroup={{.CgroupVersion}} Kernel={{.KernelVersion}} CPUs={{.NCPU}} Memory={{.MemTotal}}'
stat -fc '%T' /sys/fs/cgroup
mount | grep cgroup
test -d /sys/fs/cgroup/memory && echo 'cgroup-v1 memory hierarchy present'
test -r /sys/fs/cgroup/cgroup.controllers && cat /sys/fs/cgroup/cgroup.controllers
free -h
df -h /var/lib/docker
ss -ltn
```

For the documented stock Judge0 1.13.1/isolate 1.8 path, the approval record
must show cgroup version 1, a mounted memory hierarchy, enough CPU/RAM/disk for
the selected language images, and no public listener for port 2358. A cgroup-v2
host is not acceptable unless the exact isolate-v2/Judge0 build, delegation,
and enforcement behavior has been independently approved and demonstrated.

Also record the candidate VM machine type, vCPU/RAM/disk allocation, kernel
boot parameters, Docker version, Judge0 image digest, and private firewall
source ranges. A stopped production VM cannot be assumed suitable merely
because it is idle; its identity, network attachment, disks, and blast radius
must be reviewed first.

## Observed host evidence: `coderzclub-judge0-1`

These results were provided from a read-only inspection while the VM is now
`TERMINATED`:

| Check | Observed result | Security interpretation |
|---|---|---|
| OS/kernel | Ubuntu 22.04; `6.8.0-1070-gcp` | Linux host path is compatible in principle |
| cgroups | Hybrid hierarchy; v1 memory, CPU, and pids controllers mounted | Required controller families are present; active isolate attachment still needs direct evidence |
| Judge0/isolate | Judge0 `1.13.1`; isolate `1.8.1` | Matches the documented stock Judge0 path |
| Accepted execution | Python submission returned `ACCEPTED` | Functional execution path proven |
| CPU limit | Intentional CPU time-limit case enforced | CPU enforcement functionally proven |
| Memory limit | 64 MiB case terminated | Memory enforcement functionally proven; classify exact verdict and cgroup event in staging |
| Process limit | Process creation restriction enforced | Process containment functionally proven |
| Network | Private Judge0 API connection denied with `enable_network=false` | Network restriction functionally proven |
| Listener | API bound to private `10.20.0.6:2358` | No public binding observed in the supplied evidence |
| VM state | `TERMINATED` | No live capacity or current network reachability claim |

These observations are not a complete security audit. They do not by
themselves prove production/staging network separation, image provenance,
container privilege scope, host firewall state, disk isolation, log integrity,
or that every submission was placed into an isolate cgroup.

- `docker info` and mount evidence document the actual hybrid/cgroup mode;
  for the stock Judge0 1.13.1 path, `/sys/fs/cgroup/memory`, CPU, and pids
  controllers must exist and be writable by the approved container runtime.
  A cgroup-v2-only host requires an approved isolate-v2 build.
- Judge0 server and worker run privileged only on the dedicated private host;
  port 2358 is not publicly reachable and `ENABLE_NETWORK=false` remains set.
- A normal Accepted submission succeeds with isolate resource flags enabled.
- An intentional CPU time-limit case terminates at the configured limit.
- An intentional memory-limit case is classified as MLE or the documented
  equivalent, without host memory exhaustion.
- An intentional process/fork-limit case is contained and does not escape the
  submission sandbox.
- An intentional wall-clock timeout is bounded below the HTTP timeout and
  worker lease; the application defaults are compile + CPU + slack < 120s <
  180s lease.
- Worker logs or inspected command lines prove `--cg` (or the approved
  isolate-v2 equivalent) is active. A passing verdict alone is insufficient.
- During a controlled test, inspect the live isolate process/cgroup membership
  and record the created cgroup path plus CPU/memory/pids limits. This direct
  active-enforcement check is still **OPEN** for `coderzclub-judge0-1`.
- Confirm cgroup OOM/CPU/pids events and cleanup after a job; confirm no stale
  cgroup, process, or writable workspace remains after termination.
- Repeated submissions under a bounded burst show no cross-job filesystem,
  process, network, or resource leakage.

Failure of any item blocks staging execution claims. Do not replace a failed
check by removing `--cg`, changing the image in place, or using the local
Windows workaround.
