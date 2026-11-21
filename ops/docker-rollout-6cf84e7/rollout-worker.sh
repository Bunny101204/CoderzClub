#!/usr/bin/env bash
# Park/swap coderzclub-worker to coderzclub-backend:6cf84e7 on coderzclub-worker-1.
# Preserves WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY=6 via env file copy from live inspect.
# Default: DRY_RUN=yes. Live swap requires:
#   DRY_RUN=no CONFIRM_DEPLOY=worker-6cf84e7 EXPECTED_CURRENT_IMAGE_ID=sha256:...
# Authorized maintenance from an intentionally stopped container also requires:
#   ALLOW_STOPPED_SOURCE=yes
# Readiness probes use private management IP 10.20.0.7:8083.
set -euo pipefail
umask 077
: "${EXPECTED_CURRENT_IMAGE_ID:?set live image digest from inventory (e.g. sha256:7f8ae0e1... full)}"
export ROLE=worker
export RELEASE_ID=6cf84e7
export EXPECTED_HOSTNAME="${EXPECTED_HOSTNAME:-coderzclub-worker-1}"
export EXPECTED_NEW_IMAGE_ID="${EXPECTED_NEW_IMAGE_ID:-sha256:b30086c75baa8cc68f14d5c183bf09d521ed0eb2363aedfed5ad2f5f29c6107f}"
export NEW_IMAGE="${NEW_IMAGE:-coderzclub-backend:6cf84e7}"
export DRY_RUN="${DRY_RUN:-yes}"
export ALLOW_STOPPED_SOURCE="${ALLOW_STOPPED_SOURCE:-no}"
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
test "$(hostname)" = "$EXPECTED_HOSTNAME"
test "$(docker image inspect --format '{{.Id}}' "$NEW_IMAGE")" = "$EXPECTED_NEW_IMAGE_ID"
# Non-secret concurrency check before any mutation path:
conc=$(docker inspect coderzclub-worker --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY=//p' | head -n1)
test "$conc" = "6"
python3 "$SCRIPT_DIR/docker_swap.py"
