#!/usr/bin/env bash
set -euo pipefail
umask 077
test "$(docker info --format '{{.CgroupVersion}}')" = "1" || { echo 'Refusing Judge0: stock isolate requires cgroup v1' >&2; exit 1; }
test -d /sys/fs/cgroup/memory || { echo 'Refusing Judge0: memory cgroup hierarchy missing' >&2; exit 1; }
ROOT=/opt/coderzclub-staging/judge0
mkdir -p "$ROOT"
install -m 600 /tmp/docker-compose.judge0-staging.yml "$ROOT/docker-compose.yml"
secret() { gcloud secrets versions access latest --project=coderzclub-load-test --secret="$1"; }
cat > "$ROOT/.env" <<EOF
JUDGE0_DB_PASSWORD=$(secret coderzclub-staging-judge0-db-password)
JUDGE0_SECRET_KEY_BASE=$(secret coderzclub-staging-judge0-secret-key-base)
EOF
chmod 600 "$ROOT/.env"
cd "$ROOT"
docker compose up -d --wait
curl --fail --silent --show-error --max-time 10 http://127.0.0.1:2358/about >/dev/null
docker compose ps
