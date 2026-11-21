#!/usr/bin/env bash
set -euo pipefail
umask 077
command -v node >/dev/null || { echo 'Node.js is required on the approved seed host' >&2; exit 1; }
secret() { gcloud secrets versions access latest --project=coderzclub-load-test --secret="$1"; }
uri=$(secret coderzclub-staging-mongodb-uri)
install -m 700 /tmp/seed-institutional.mjs /tmp/seed-institutional.mjs
PERF_TARGET_ENV=staging PERF_MONGO_URI="$uri" PERF_MONGO_DB=coderzclub_staging node /tmp/seed-institutional.mjs
