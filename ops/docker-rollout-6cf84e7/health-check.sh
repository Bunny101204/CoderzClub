#!/usr/bin/env bash
# Post-swap / rollback health checks for api or worker role on the VM.
# Uses private-IP management bindings (not loopback).
set -euo pipefail
ROLE="${1:?usage: health-check.sh api|worker}"
case "$ROLE" in
  api)
    curl --fail --silent --show-error --retry 40 --retry-connrefused --retry-delay 3 --max-time 10 \
      http://10.20.0.4:8081/actuator/health/readiness
    echo
    curl --fail --silent --show-error --max-time 10 http://10.20.0.4:8081/actuator/health
    echo
    curl --fail --silent --show-error --max-time 10 http://10.20.0.4:8080/api/problems/test
    echo
    img=$(docker inspect coderzclub-api --format '{{.Image}}')
    echo "LIVE_IMAGE=$img"
    ;;
  worker)
    curl --fail --silent --show-error --retry 40 --retry-connrefused --retry-delay 3 --max-time 10 \
      http://10.20.0.7:8083/actuator/health/readiness
    echo
    curl --fail --silent --show-error --max-time 10 http://10.20.0.7:8083/actuator/health
    echo
    conc=$(docker inspect coderzclub-worker --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY=//p' | head -n1)
    test "$conc" = "6"
    img=$(docker inspect coderzclub-worker --format '{{.Image}}')
    echo "LIVE_IMAGE=$img concurrency=$conc"
    ;;
  *)
    echo "ROLE must be api or worker" >&2
    exit 2
    ;;
esac
echo "HEALTH_OK role=$ROLE"
