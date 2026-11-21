#!/usr/bin/env bash
# Park/swap coderzclub-api to coderzclub-backend:6cf84e7 on coderzclub-api-1.
# Default: DRY_RUN=yes (no mutation). Live swap requires:
#   DRY_RUN=no CONFIRM_DEPLOY=api-6cf84e7 EXPECTED_CURRENT_IMAGE_ID=sha256:...
# Authorized maintenance from an intentionally stopped container also requires:
#   ALLOW_STOPPED_SOURCE=yes
# Readiness probes use private management IP 10.20.0.4:8081 (app 10.20.0.4:8080).
set -euo pipefail
umask 077
: "${EXPECTED_CURRENT_IMAGE_ID:?set live image digest from inventory (e.g. sha256:7f8ae0e1... full)}"
export ROLE=api
export RELEASE_ID=6cf84e7
export EXPECTED_HOSTNAME="${EXPECTED_HOSTNAME:-coderzclub-api-1}"
export EXPECTED_NEW_IMAGE_ID="${EXPECTED_NEW_IMAGE_ID:-sha256:b30086c75baa8cc68f14d5c183bf09d521ed0eb2363aedfed5ad2f5f29c6107f}"
export NEW_IMAGE="${NEW_IMAGE:-coderzclub-backend:6cf84e7}"
export DRY_RUN="${DRY_RUN:-yes}"
export ALLOW_STOPPED_SOURCE="${ALLOW_STOPPED_SOURCE:-no}"
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
test "$(hostname)" = "$EXPECTED_HOSTNAME"
test "$(docker image inspect --format '{{.Id}}' "$NEW_IMAGE")" = "$EXPECTED_NEW_IMAGE_ID"
python3 "$SCRIPT_DIR/docker_swap.py"
