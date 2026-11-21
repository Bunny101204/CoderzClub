#!/usr/bin/env bash
# Rollback worker to parked coderzclub-worker-before-6cf84e7. Requires explicit confirm.
set -euo pipefail
umask 077
: "${EXPECTED_CURRENT_IMAGE_ID:?set previous (parked) image digest}"
export ROLE=worker
export RELEASE_ID=6cf84e7
export EXPECTED_HOSTNAME="${EXPECTED_HOSTNAME:-coderzclub-worker-1}"
export EXPECTED_NEW_IMAGE_ID="${EXPECTED_NEW_IMAGE_ID:-sha256:b30086c75baa8cc68f14d5c183bf09d521ed0eb2363aedfed5ad2f5f29c6107f}"
export ROLLBACK=yes
export DRY_RUN=no
: "${CONFIRM_DEPLOY:?must equal worker-6cf84e7}"
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
test "$(hostname)" = "$EXPECTED_HOSTNAME"
python3 "$SCRIPT_DIR/docker_swap.py"
curl --fail --silent --show-error --retry 40 --retry-connrefused --retry-delay 3 --max-time 10 \
  http://10.20.0.7:8083/actuator/health/readiness
curl --fail --silent --show-error --max-time 10 http://10.20.0.7:8083/actuator/health
conc=$(docker inspect coderzclub-worker --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY=//p' | head -n1)
test "$conc" = "6"
echo "WORKER_ROLLBACK_HEALTH_OK concurrency=$conc"
