#!/usr/bin/env bash
# Secure Python/C++ sandbox smoke + denial checks against local Judge0 on this host.
# Run on coderzclub-judge0-1 (or any host with private :2358). No DB writes.
set -euo pipefail
BASE="${JUDGE0_BASE:-http://127.0.0.1:2358}"
WAIT_URL="$BASE/submissions?base64_encoded=false&wait=true"

post() {
  local body=$1
  curl -sS --fail --max-time 90 "$WAIT_URL" \
    -H 'Content-Type: application/json' \
    -d "$body"
}

echo "=== ABOUT / CONFIG ==="
curl -sS --fail --max-time 10 "$BASE/about"; echo
curl -sS --fail --max-time 10 "$BASE/config_info"; echo

echo "=== PYTHON ACCEPTED ==="
py=$(post '{"source_code":"print(42)","language_id":71,"stdin":"","cpu_time_limit":15,"wall_time_limit":20,"memory_limit":262144,"enable_network":false,"enable_per_process_and_thread_time_limit":false,"enable_per_process_and_thread_memory_limit":false}')
echo "$py" | python3 -c 'import json,sys; d=json.load(sys.stdin); print("status_id",d.get("status",{}).get("id"),"desc",d.get("status",{}).get("description"),"stdout",repr(d.get("stdout"))); assert d.get("status",{}).get("id")==3'

echo "=== C++ ACCEPTED ==="
cpp=$(post '{"source_code":"#include <iostream>\nint main(){std::cout<<7;}\n","language_id":54,"stdin":"","cpu_time_limit":15,"wall_time_limit":20,"memory_limit":262144,"enable_network":false,"enable_per_process_and_thread_time_limit":false,"enable_per_process_and_thread_memory_limit":false}')
echo "$cpp" | python3 -c 'import json,sys; d=json.load(sys.stdin); print("status_id",d.get("status",{}).get("id"),"desc",d.get("status",{}).get("description"),"stdout",repr(d.get("stdout"))); assert d.get("status",{}).get("id")==3'

echo "=== CPU TLE (busy loop) ==="
tle=$(post '{"source_code":"while True: pass","language_id":71,"stdin":"","cpu_time_limit":1,"wall_time_limit":3,"memory_limit":262144,"enable_network":false,"enable_per_process_and_thread_time_limit":false,"enable_per_process_and_thread_memory_limit":false}')
echo "$tle" | python3 -c 'import json,sys; d=json.load(sys.stdin); sid=d.get("status",{}).get("id"); desc=d.get("status",{}).get("description"); print("status_id",sid,"desc",desc); assert sid in (5,6,13) or "Time Limit" in (desc or "")'

echo "=== NETWORK DENY REQUEST (enable_network true should not grant egress when ALLOW false) ==="
net=$(post '{"source_code":"import urllib.request\nurllib.request.urlopen(\"http://1.1.1.1\", timeout=2)\nprint(\"escaped\")\n","language_id":71,"stdin":"","cpu_time_limit":5,"wall_time_limit":8,"memory_limit":262144,"enable_network":true,"enable_per_process_and_thread_time_limit":false,"enable_per_process_and_thread_memory_limit":false}' || true)
echo "$net" | python3 -c 'import json,sys; t=sys.stdin.read();
import re
print(t[:500])
# Accept either hard reject (non-JSON/error) or a failed/non-Accepted status without "escaped"
try:
  d=json.loads(t)
except Exception:
  print("network_case=non_json_or_reject"); sys.exit(0)
stdout=(d.get("stdout") or "")
desc=(d.get("status") or {}).get("description") or ""
sid=(d.get("status") or {}).get("id")
print("status_id",sid,"desc",desc,"stdout",repr(stdout))
assert "escaped" not in stdout
assert sid != 3
print("network_case=contained")'

echo "SMOKE_JUDGE0_OK"
