#!/usr/bin/env bash
set -euo pipefail
umask 077
ROOT=/opt/coderzclub-staging/infra
mkdir -p "$ROOT"
install -m 600 /tmp/docker-compose.staging-infra.yml "$ROOT/docker-compose.yml"
secret() { gcloud secrets versions access latest --project=coderzclub-load-test --secret="$1"; }
mongo_uri=$(secret coderzclub-staging-mongodb-uri)
mongo_password=${mongo_uri#*root:}; mongo_password=${mongo_password%%@*}
redis_password=$(secret coderzclub-staging-redis-password)
rabbit_user=$(secret coderzclub-staging-rabbitmq-username)
rabbit_password=$(secret coderzclub-staging-rabbitmq-password)
replica_host=$(hostname -I | awk '{print $1}')
cat > "$ROOT/.env" <<EOF
MONGO_ROOT_PASSWORD=$mongo_password
MONGO_REPLICA_HOST=$replica_host
REDIS_PASSWORD=$redis_password
RABBITMQ_USERNAME=$rabbit_user
RABBITMQ_PASSWORD=$rabbit_password
RABBITMQ_VHOST=/coderzclub-staging
EOF
chmod 600 "$ROOT/.env"
cd "$ROOT"
docker compose up -d --wait
docker compose ps
