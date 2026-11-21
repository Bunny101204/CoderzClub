#!/usr/bin/env bash
set -euo pipefail
secret() { gcloud secrets versions access latest --project=coderzclub-load-test --secret="$1"; }
user=$(secret coderzclub-staging-rabbitmq-username)
pass=$(secret coderzclub-staging-rabbitmq-password)
python3 - "$user" "$pass" <<'PY'
import json, sys, urllib.request
u, p = sys.argv[1:]
req = urllib.request.Request("http://127.0.0.1:15672/api/queues/%2Fcoderzclub-staging", headers={"Authorization": "Basic " + __import__("base64").b64encode((u+":"+p).encode()).decode()})
with urllib.request.urlopen(req, timeout=10) as r:
    rows=json.load(r)
for row in rows:
    if row["name"].startswith("coderzclub.staging"):
        print(json.dumps({k: row.get(k) for k in ("name","messages","messages_ready","messages_unacknowledged","consumers")}))
PY
