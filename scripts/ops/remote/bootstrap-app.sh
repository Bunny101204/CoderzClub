#!/usr/bin/env bash
set -euo pipefail
umask 077
: "${INFRA_IP:?missing INFRA_IP}"
: "${JUDGE0_IP:?missing JUDGE0_IP}"
: "${SERVICE_ROLE:=api}"
case "$SERVICE_ROLE" in api|worker) ;; *) echo 'SERVICE_ROLE must be api or worker' >&2; exit 1 ;; esac
test -s /tmp/coderzclub-backend.jar || { echo 'Reviewed backend jar missing' >&2; exit 1; }
command -v java >/dev/null || { echo 'Java 17 runtime must be preinstalled on the app VM' >&2; exit 1; }
install -d -m 750 /opt/coderzclub-staging /etc/coderzclub-staging
id coderzclub-staging >/dev/null 2>&1 || useradd --system --home-dir /opt/coderzclub-staging --shell /usr/sbin/nologin coderzclub-staging
install -o coderzclub-staging -g coderzclub-staging -m 750 /tmp/coderzclub-backend.jar /opt/coderzclub-staging/app.jar
secret() { gcloud secrets versions access latest --project=coderzclub-load-test --secret="$1"; }
mongo_uri=$(secret coderzclub-staging-mongodb-uri)
jwt=$(secret coderzclub-staging-jwt-secret)
redis_password=$(secret coderzclub-staging-redis-password)
rabbit_user=$(secret coderzclub-staging-rabbitmq-username)
rabbit_password=$(secret coderzclub-staging-rabbitmq-password)
cat > /etc/coderzclub-staging/api.env <<EOF
SPRING_PROFILES_ACTIVE=api
SERVER_ADDRESS=0.0.0.0
SERVER_PORT=8080
MANAGEMENT_SERVER_ADDRESS=0.0.0.0
MANAGEMENT_SERVER_PORT=8081
MONGODB_URI=$mongo_uri
SPRING_DATA_MONGODB_URI=$mongo_uri
SPRING_DATA_MONGODB_DATABASE=coderzclub_staging
JWT_SECRET=$jwt
SPRING_DATA_REDIS_HOST=$INFRA_IP
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=$redis_password
SPRING_DATA_REDIS_DATABASE=0
SPRING_REDIS_HOST=$INFRA_IP
SPRING_REDIS_PORT=6379
SPRING_REDIS_PASSWORD=$redis_password
SPRING_REDIS_DATABASE=0
RABBITMQ_HOST=$INFRA_IP
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=$rabbit_user
RABBITMQ_PASSWORD=$rabbit_password
RABBITMQ_VHOST=/coderzclub-staging
JUDGE0_API_URL=http://$JUDGE0_IP:2358/submissions
JUDGE0_AUTH_MODE=SELF_HOSTED
JUDGE0_WAIT=true
APP_REQUIRE_SECURE_SECRETS=true
SUBMISSION_LIMIT_REDIS_FAIL_OPEN=false
CONTEST_GLOBAL_ENABLED=false
SUBMISSION_QUEUE_NAME=coderzclub.staging.submissions
SUBMISSION_QUEUE_EXCHANGE=coderzclub.staging.submissions.exchange
SUBMISSION_QUEUE_RETRY_NAME=coderzclub.staging.submissions.retry
SUBMISSION_QUEUE_DLQ_NAME=coderzclub.staging.submissions.dlq
CONTEST_RUN_QUEUE_NAME=coderzclub.staging.contest-runs
CONTEST_RUN_QUEUE_EXCHANGE=coderzclub.staging.contest-runs.exchange
SPRING_MAIL_ENABLED=false
MANAGEMENT_HEALTH_MAIL_ENABLED=false
EOF
cp /etc/coderzclub-staging/api.env /etc/coderzclub-staging/worker.env
sed -i 's/^SPRING_PROFILES_ACTIVE=api$/SPRING_PROFILES_ACTIVE=worker/; s/^SERVER_PORT=8080$/SERVER_PORT=8082/; s/^MANAGEMENT_SERVER_PORT=8081$/MANAGEMENT_SERVER_PORT=8083/' /etc/coderzclub-staging/worker.env
chown root:root /etc/coderzclub-staging/*.env
chmod 600 /etc/coderzclub-staging/*.env
if [ "$SERVICE_ROLE" = api ]; then
cat > /etc/systemd/system/coderzclub-staging-api.service <<'UNIT'
[Unit]
After=network-online.target
[Service]
User=coderzclub-staging
EnvironmentFile=/etc/coderzclub-staging/api.env
ExecStart=/usr/bin/java -jar /opt/coderzclub-staging/app.jar
Restart=on-failure
[Install]
WantedBy=multi-user.target
UNIT
fi
cat > /etc/systemd/system/coderzclub-staging-worker.service <<'UNIT'
[Unit]
After=network-online.target
[Service]
User=coderzclub-staging
EnvironmentFile=/etc/coderzclub-staging/worker.env
ExecStart=/usr/bin/java -jar /opt/coderzclub-staging/app.jar
Restart=on-failure
[Install]
WantedBy=multi-user.target
UNIT
if [ "$SERVICE_ROLE" = api ]; then
systemctl daemon-reload
systemctl enable --now coderzclub-staging-api.service
curl --fail --silent --show-error --max-time 15 http://127.0.0.1:8081/actuator/health/readiness >/dev/null
else
systemctl daemon-reload
systemctl enable --now coderzclub-staging-worker.service
curl --fail --silent --show-error --max-time 15 http://127.0.0.1:8083/actuator/health/readiness >/dev/null
fi
