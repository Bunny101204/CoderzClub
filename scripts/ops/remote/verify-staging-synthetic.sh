#!/usr/bin/env bash
set -euo pipefail
secret() { gcloud secrets versions access latest --project=coderzclub-load-test --secret="$1"; }
uri=$(secret coderzclub-staging-mongodb-uri)
mongosh --quiet "$uri"/coderzclub_staging --eval 'const m=db.perf_seed_meta.findOne({_id:"meta"}); if(!m || m.usersTotal !== 20000) quit(2); printjson({seedVersion:m.version,usersTotal:m.usersTotal,db:db.getName()})'
