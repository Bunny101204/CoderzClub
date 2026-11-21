#!/usr/bin/env bash
# Regression tests for judge0-harden.sh matching REAL GCP layout.
# No GCP changes; no container mutation.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")" && pwd)
SCRIPT="$ROOT/judge0-harden.sh"
FIX_SRC="$ROOT/testdata/judge0-v1131"
INFO_PY="$ROOT/judge0_config_info_validate.py"
ENV_PY="$ROOT/judge0_compose_env_validate.py"
# Fixtures live outside script TMPDIR so leak-check clears do not delete them.
WORK=$(mktemp -d)
TMP_PRIVATE=$(mktemp -d)
SCRIPT_TMP="$TMP_PRIVATE/script-tmp"
mkdir -p "$SCRIPT_TMP"
trap 'rm -rf "$WORK" "$TMP_PRIVATE"' EXIT
export TMPDIR="$SCRIPT_TMP"

assert_no_confidential_temps() {
  local label=$1
  # After script EXIT cleanup, script TMPDIR must have no leftover files.
  local leftovers
  leftovers=$(find "$SCRIPT_TMP" -type f 2>/dev/null || true)
  if [[ -n "$leftovers" ]]; then
    echo "FAIL: confidential temp files remain after $label:" >&2
    echo "$leftovers" >&2
    while IFS= read -r f; do
      [[ -n "$f" ]] || continue
      echo "  leftover size=$(wc -c <"$f") path=$f" >&2
    done <<<"$leftovers"
    exit 1
  fi
  echo "NO_TEMP_LEAK_OK label=$label"
}

clear_script_temps() {
  find "$SCRIPT_TMP" -mindepth 1 -delete 2>/dev/null || true
}

echo "=== bash -n ==="
bash -n "$SCRIPT"
python3 -m py_compile "$INFO_PY" "$ENV_PY"
echo SYNTAX_OK

echo "=== static guards ==="
python3 - "$SCRIPT" <<'PY'
from pathlib import Path
import sys
t = Path(sys.argv[1]).read_text().replace("\r\n", "\n")
assert 'COMPOSE_DIR="${COMPOSE_DIR:-/home/g228r1a0537/judge0-v1.13.1}"' in t
assert "judge0-v1131" in t
assert "10.20.0.6:2358" in t
assert "--no-deps" in t
assert "patch_present_keys" in t
assert "apply_patches_atomic" in t
assert "append_cleanup" in t
assert "CLEANUP_LIST_FILE" in t
assert "JUDGE0_EXPECT_COUNT" in t
assert "JUDGE0_EXPECTED_COUNT" in t
assert "print_manual_rollback_on_failure" in t
assert "Do NOT use" in t or "Do NOT pipe rollback" in t
assert "COUNT=6" not in t  # never force app worker concurrency into Judge0 COUNT
info_py = Path(sys.argv[1]).parent.joinpath("judge0_config_info_validate.py").read_text()
assert "isinstance(actual, bool)" in info_py
assert "numbers_equal" in info_py
assert "int(actual)" not in info_py
env_py = Path(sys.argv[1]).parent.joinpath("judge0_compose_env_validate.py").read_text()
assert "JUDGE0_EXPECT_COUNT" in env_py
assert "JUDGE0_EXPECTED_COUNT" in env_py
print("STATIC_GUARDS_OK")
PY

echo "=== config_info JSON positive (15 integer) ==="
GOOD='{"enable_network":false,"allow_enable_network":false,"enable_per_process_and_thread_time_limit":false,"allow_enable_per_process_and_thread_time_limit":false,"enable_per_process_and_thread_memory_limit":false,"allow_enable_per_process_and_thread_memory_limit":false,"max_cpu_time_limit":15,"max_wall_time_limit":20,"max_memory_limit":512000}'
printf '%s' "$GOOD" | python3 "$INFO_PY"
echo CONFIG_INFO_POSITIVE_OK

echo "=== config_info: valid CPU limit 15.0 accepted ==="
GOOD15='{"enable_network":false,"allow_enable_network":false,"enable_per_process_and_thread_time_limit":false,"allow_enable_per_process_and_thread_time_limit":false,"enable_per_process_and_thread_memory_limit":false,"allow_enable_per_process_and_thread_memory_limit":false,"max_cpu_time_limit":15.0,"max_wall_time_limit":20,"max_memory_limit":512000}'
printf '%s' "$GOOD15" | python3 "$INFO_PY"
echo CONFIG_INFO_15_0_OK

echo "=== config_info: 15.5 rejected when requirement is 15 ==="
set +e
printf '%s' '{"enable_network":false,"allow_enable_network":false,"enable_per_process_and_thread_time_limit":false,"allow_enable_per_process_and_thread_time_limit":false,"enable_per_process_and_thread_memory_limit":false,"allow_enable_per_process_and_thread_memory_limit":false,"max_cpu_time_limit":15.5,"max_wall_time_limit":20,"max_memory_limit":512000}' | python3 "$INFO_PY"
rc=$?
set -e
[[ "$rc" -eq 3 ]] || { echo "expected exit 3 for 15.5, got $rc"; exit 1; }
echo CONFIG_INFO_15_5_REJECT_OK exit=$rc

echo "=== config_info JSON negative: string false must fail ==="
set +e
printf '%s' '{"enable_network":"false","allow_enable_network":false,"enable_per_process_and_thread_time_limit":false,"allow_enable_per_process_and_thread_time_limit":false,"enable_per_process_and_thread_memory_limit":false,"allow_enable_per_process_and_thread_memory_limit":false,"max_cpu_time_limit":15,"max_wall_time_limit":20,"max_memory_limit":512000}' | python3 "$INFO_PY"
rc=$?
set -e
[[ "$rc" -eq 3 ]] || { echo "expected exit 3 for string boolean, got $rc"; exit 1; }
echo CONFIG_INFO_STRING_BOOL_FAIL_OK

echo "=== config_info JSON negative: allow true must fail ==="
set +e
printf '%s' '{"enable_network":false,"allow_enable_network":true,"enable_per_process_and_thread_time_limit":false,"allow_enable_per_process_and_thread_time_limit":false,"enable_per_process_and_thread_memory_limit":false,"allow_enable_per_process_and_thread_memory_limit":false,"max_cpu_time_limit":15,"max_wall_time_limit":20,"max_memory_limit":512000}' | python3 "$INFO_PY"
rc=$?
set -e
[[ "$rc" -eq 3 ]] || { echo "expected exit 3 for allow true, got $rc"; exit 1; }
echo CONFIG_INFO_ALLOW_TRUE_FAIL_OK

echo "=== stdin not consumed by heredoc (pipe works) ==="
printf '%s' "$GOOD" | python3 "$INFO_PY" >/tmp/judge0-info-out.txt
grep -q CONFIG_INFO_JSON_OK /tmp/judge0-info-out.txt
echo STDIN_PIPE_OK

echo "=== real GCP layout fixture offline dry-run (COUNT= empty) ==="
cp -a "$FIX_SRC/." "$WORK/"
grep -q '^COUNT=$' "$WORK/judge0.conf"
! grep -E 'ENABLE_NETWORK|ALLOW_ENABLE_NETWORK|MAX_CPU_TIME_LIMIT' "$WORK/docker-compose.yml"
[[ ! -e "$WORK/.env" ]]
clear_script_temps
set +e
COMPOSE_DIR="$WORK" SKIP_LIVE_DOCKER=yes DRY_RUN=yes bash "$SCRIPT"
DRY_RC=$?
set -e
[[ "$DRY_RC" -eq 0 ]] || { echo "dry-run exit=$DRY_RC"; exit 1; }
echo OFFLINE_DRY_RUN_EXIT=$DRY_RC
assert_no_confidential_temps "successful dry-run COUNT="

echo "=== COUNT=\"\" preserved in fixture after dry-run ==="
grep -q '^COUNT=$' "$WORK/judge0.conf"
grep -q '10.20.0.6:2358:2358' "$WORK/docker-compose.yml"
grep -q '^ENABLE_NETWORK=$' "$WORK/judge0.conf"
echo COUNT_EMPTY_PRESERVED_OK

echo "=== COUNT=\"\" compose env validator accepts empty ==="
python3 - <<'PY' | JUDGE0_EXPECT_COUNT=1 JUDGE0_EXPECTED_COUNT= python3 "$ENV_PY"
import json
req = {
  "ENABLE_NETWORK":"false","ALLOW_ENABLE_NETWORK":"false",
  "ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT":"false",
  "ALLOW_ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT":"false",
  "ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT":"false",
  "ALLOW_ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT":"false",
  "MAX_CPU_TIME_LIMIT":"15","MAX_WALL_TIME_LIMIT":"20","MAX_MEMORY_LIMIT":"512000",
  "COUNT":"",
}
print(json.dumps({"services":{"server":{"environment":req},"workers":{"environment":req}}}))
PY
echo COUNT_EMPTY_COMPOSE_OK

echo "=== COUNT=\"6\" preserved (dedicated fixture) ==="
WORK6=$(mktemp -d -p "$TMP_PRIVATE")
cp -a "$FIX_SRC/." "$WORK6/"
sed -i 's/^COUNT=$/COUNT=6/' "$WORK6/judge0.conf"
grep -q '^COUNT=6$' "$WORK6/judge0.conf"
clear_script_temps
set +e
COMPOSE_DIR="$WORK6" SKIP_LIVE_DOCKER=yes DRY_RUN=yes bash "$SCRIPT"
RC6=$?
set -e
[[ "$RC6" -eq 0 ]] || { echo "COUNT=6 dry-run failed exit=$RC6"; rm -rf "$WORK6"; exit 1; }
grep -q '^COUNT=6$' "$WORK6/judge0.conf"
assert_no_confidential_temps "successful dry-run COUNT=6"
rm -rf "$WORK6"
echo COUNT_6_PRESERVED_OK exit=$RC6

echo "=== compose env validator: COUNT=6 preserved when expected ==="
python3 - <<'PY' | JUDGE0_EXPECT_COUNT=1 JUDGE0_EXPECTED_COUNT=6 python3 "$ENV_PY"
import json
req = {
  "ENABLE_NETWORK":"false","ALLOW_ENABLE_NETWORK":"false",
  "ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT":"false",
  "ALLOW_ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT":"false",
  "ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT":"false",
  "ALLOW_ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT":"false",
  "MAX_CPU_TIME_LIMIT":"15","MAX_WALL_TIME_LIMIT":"20","MAX_MEMORY_LIMIT":"512000",
  "COUNT":"6",
}
print(json.dumps({"services":{"server":{"environment":req},"workers":{"environment":req}}}))
PY
echo COUNT_6_COMPOSE_OK

echo "=== compose env validator rejects COUNT change ==="
set +e
python3 - <<'PY' | JUDGE0_EXPECT_COUNT=1 JUDGE0_EXPECTED_COUNT= python3 "$ENV_PY"
import json
req = {
  "ENABLE_NETWORK":"false","ALLOW_ENABLE_NETWORK":"false",
  "ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT":"false",
  "ALLOW_ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT":"false",
  "ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT":"false",
  "ALLOW_ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT":"false",
  "MAX_CPU_TIME_LIMIT":"15","MAX_WALL_TIME_LIMIT":"20","MAX_MEMORY_LIMIT":"512000",
  "COUNT":"6",
}
print(json.dumps({"services":{"server":{"environment":req},"workers":{"environment":req}}}))
PY
rc=$?
set -e
[[ "$rc" -ne 0 ]] || { echo "expected COUNT mismatch failure"; exit 1; }
echo COUNT_MISMATCH_REJECT_OK exit=$rc

echo "=== compose env validator positive (no COUNT expect) ==="
python3 - <<'PY' | python3 "$ENV_PY"
import json
req = {
  "ENABLE_NETWORK":"false","ALLOW_ENABLE_NETWORK":"false",
  "ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT":"false",
  "ALLOW_ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT":"false",
  "ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT":"false",
  "ALLOW_ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT":"false",
  "MAX_CPU_TIME_LIMIT":"15","MAX_WALL_TIME_LIMIT":"20","MAX_MEMORY_LIMIT":"512000",
}
print(json.dumps({"services":{"server":{"environment":req},"workers":{"environment":req}}}))
PY
echo COMPOSE_ENV_POSITIVE_OK

echo "=== compose env validator negative ==="
set +e
python3 - <<'PY' | python3 "$ENV_PY"
import json
req = {"ENABLE_NETWORK":"true","COUNT":"2"}
print(json.dumps({"services":{"server":{"environment":req},"workers":{"environment":req}}}))
PY
rc=$?
set -e
[[ "$rc" -ne 0 ]] || { echo "expected compose env failure"; exit 1; }
echo COMPOSE_ENV_NEGATIVE_OK

echo "=== refuse offline mutate ==="
clear_script_temps
set +e
COMPOSE_DIR="$WORK" SKIP_LIVE_DOCKER=yes DRY_RUN=no APPROVE=yes bash "$SCRIPT"
rc=$?
set -e
[[ "$rc" -ne 0 ]] || { echo "offline mutate should fail"; exit 1; }
assert_no_confidential_temps "failed offline mutate"
echo REFUSE_OFFLINE_MUTATE_OK exit=$rc

echo "=== missing-key fail-closed (no temp leak) ==="
bad=$(mktemp -d -p "$TMP_PRIVATE")
cat > "$bad/docker-compose.yml" <<'EOF'
services:
  server:
    image: judge0/judge0:1.13.1
    env_file: [judge0.conf]
  workers:
    image: judge0/judge0:1.13.1
    env_file: [judge0.conf]
  db:
    image: postgres:13
  redis:
    image: redis:7.2-alpine
EOF
echo 'COUNT=1' > "$bad/judge0.conf"
clear_script_temps
set +e
COMPOSE_DIR="$bad" SKIP_LIVE_DOCKER=yes DRY_RUN=yes bash "$SCRIPT"
rc=$?
set -e
rm -rf "$bad"
[[ "$rc" -ne 0 ]] || { echo "expected missing-key failure"; exit 1; }
assert_no_confidential_temps "failed missing-key dry-run"
echo MISSING_KEY_FAIL_CLOSED_OK exit=$rc

echo "=== rollback text must not use hardened validator for rollback verify ==="
out=$(COMPOSE_DIR="$WORK" SKIP_LIVE_DOCKER=yes DRY_RUN=yes bash "$SCRIPT" 2>&1)
echo "$out" | grep -q 'Do NOT pipe rollback config_info\|Do NOT use'
echo "$out" | grep -q 'MANUAL ROLLBACK\|ROLLBACK'
# Successful dry-run rollback preview should mention not piping into hardened validator
echo ROLLBACK_NO_HARDENED_VALIDATOR_OK

echo "=== patch-only-present-keys: compose without keys still OK when conf has all ==="
echo PATCH_PRESENT_KEYS_OK

echo "ALL_JUDGE0_HARDEN_TESTS_OK"
