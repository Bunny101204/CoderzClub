#!/usr/bin/env bash
# Judge0 hardening for verified GCP load-test deployment on coderzclub-judge0-1.
#
# Layout:
#   Compose project : judge0-v1131
#   Directory       : /home/g228r1a0537/judge0-v1.13.1
#   Services        : server, workers, db, redis
#   Policy keys     : typically only in judge0.conf (may be empty); not in docker-compose.yml
#
# Default DRY_RUN=yes. Mutation requires APPROVE=yes.
# Recreates ONLY server + workers (--no-deps). Never touches db/redis volumes.
# Never applies insecure no-cgroup patches.
set -euo pipefail
umask 077

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
EXPECTED_HOSTNAME="${EXPECTED_HOSTNAME:-coderzclub-judge0-1}"
EXPECTED_COMPOSE_PROJECT="${EXPECTED_COMPOSE_PROJECT:-judge0-v1131}"
COMPOSE_DIR="${COMPOSE_DIR:-/home/g228r1a0537/judge0-v1.13.1}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
EXPECTED_SERVER_CONTAINER="${EXPECTED_SERVER_CONTAINER:-judge0-v1131_server_1}"
EXPECTED_WORKERS_CONTAINER="${EXPECTED_WORKERS_CONTAINER:-judge0-v1131_workers_1}"
EXPECTED_DB_CONTAINER="${EXPECTED_DB_CONTAINER:-judge0-v1131_db_1}"
EXPECTED_REDIS_CONTAINER="${EXPECTED_REDIS_CONTAINER:-judge0-v1131_redis_1}"
EXPECTED_SERVER_SERVICE="${EXPECTED_SERVER_SERVICE:-server}"
EXPECTED_WORKERS_SERVICE="${EXPECTED_WORKERS_SERVICE:-workers}"
EXPECTED_DB_SERVICE="${EXPECTED_DB_SERVICE:-db}"
EXPECTED_REDIS_SERVICE="${EXPECTED_REDIS_SERVICE:-redis}"
HEALTH_BASE="${HEALTH_BASE:-http://10.20.0.6:2358}"
BACKUP_SUFFIX="before-6cf84e7-$(date -u +%Y%m%dT%H%M%SZ)"
DRY_RUN="${DRY_RUN:-yes}"
APPROVE="${APPROVE:-no}"
SKIP_LIVE_DOCKER="${SKIP_LIVE_DOCKER:-no}"

CONFIG_INFO_VALIDATOR="${SCRIPT_DIR}/judge0_config_info_validate.py"
COMPOSE_ENV_VALIDATOR="${SCRIPT_DIR}/judge0_compose_env_validate.py"

# File-backed cleanup list so registrations inside command-substitution subshells persist.
CLEANUP_LIST_FILE=$(mktemp)
export CLEANUP_LIST_FILE
PATCHED_LIVE_FILES=()
BACKUPS_MADE=()
INSTALL_IN_PROGRESS=0

secure_rm() {
  local p=$1
  [[ -n "$p" && -e "$p" ]] || return 0
  if [[ -f "$p" ]] && command -v shred >/dev/null 2>&1; then
    shred -u -- "$p" 2>/dev/null || rm -f -- "$p"
  else
    rm -rf -- "$p"
  fi
}

cleanup() {
  local p
  if [[ -n "${CLEANUP_LIST_FILE:-}" && -f "$CLEANUP_LIST_FILE" ]]; then
    while IFS= read -r p || [[ -n "${p:-}" ]]; do
      [[ -n "${p:-}" ]] || continue
      secure_rm "$p"
    done <"$CLEANUP_LIST_FILE"
    rm -f -- "$CLEANUP_LIST_FILE"
  fi
}
trap cleanup EXIT

die() { echo "FAIL: $*" >&2; exit 2; }

# Safe from parent and from $(...) subshells: append path to shared list file.
append_cleanup() {
  local p=$1
  [[ -n "$p" ]] || return 0
  # CLEANUP_LIST_FILE must be exported/inherited; always use absolute path set above.
  printf '%s\n' "$p" >>"$CLEANUP_LIST_FILE"
}

capture_original_count() {
  # Export original COUNT (may be empty) for compose-env validation. Never force a concurrency value.
  if [[ -f judge0.conf ]] && grep -qE '^COUNT=' judge0.conf; then
    JUDGE0_EXPECTED_COUNT=$(grep -E '^COUNT=' judge0.conf | head -n1 | cut -d= -f2-)
    export JUDGE0_EXPECT_COUNT=1
    export JUDGE0_EXPECTED_COUNT
    echo "ORIGINAL_COUNT_CAPTURED=yes (value redacted; empty_ok=$([ -z "$JUDGE0_EXPECTED_COUNT" ] && echo yes || echo no))"
  else
    export JUDGE0_EXPECT_COUNT=0
    unset JUDGE0_EXPECTED_COUNT || true
    echo "ORIGINAL_COUNT_CAPTURED=no"
  fi
}

desired_keys() {
  cat <<'EOF'
ENABLE_NETWORK=false
ALLOW_ENABLE_NETWORK=false
ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT=false
ALLOW_ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT=false
ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT=false
ALLOW_ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT=false
MAX_CPU_TIME_LIMIT=15
MAX_WALL_TIME_LIMIT=20
MAX_MEMORY_LIMIT=512000
EOF
}

desired_key_names() {
  desired_keys | cut -d= -f1
}

compose() {
  docker compose -p "$EXPECTED_COMPOSE_PROJECT" -f "$COMPOSE_FILE" "$@"
}

safe_env_keys_from_container() {
  local container=$1
  docker inspect "$container" --format '{{range .Config.Env}}{{println .}}{{end}}' \
    | grep -E '^(COUNT|ENABLE_NETWORK|ALLOW_ENABLE_NETWORK|ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT|ALLOW_ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT|ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT|ALLOW_ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT|MAX_CPU_TIME_LIMIT|MAX_WALL_TIME_LIMIT|MAX_MEMORY_LIMIT|ENABLE_WAIT_RESULT)=' \
    || true
}

require_host() {
  local host
  host=$(hostname)
  [[ "$host" == "$EXPECTED_HOSTNAME" ]] || die "hostname is '$host', expected '$EXPECTED_HOSTNAME'"
}

check_cgroup() {
  local cg
  cg=$(docker info --format '{{.CgroupVersion}}')
  echo "CgroupVersion=$cg"
  [[ "$cg" == "1" ]] || die "stock Judge0 isolate --cg requires cgroup v1 (saw '$cg')"
  [[ -d /sys/fs/cgroup/memory ]] || die "cgroup v1 memory hierarchy missing"
  echo "cgroup_v1_memory=yes"
}

verify_container_identity() {
  local container=$1 expected_service=$2
  local svc project workdir config_files
  docker inspect "$container" >/dev/null 2>&1 || die "required container missing: $container"
  svc=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.service"}}' "$container")
  project=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$container")
  workdir=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project.working_dir"}}' "$container")
  config_files=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project.config_files"}}' "$container")
  [[ "$svc" == "$expected_service" ]] || die "$container service='$svc' want '$expected_service'"
  [[ "$project" == "$EXPECTED_COMPOSE_PROJECT" ]] || die "$container project='$project' want '$EXPECTED_COMPOSE_PROJECT'"
  # working_dir label may be absolute; require suffix match to COMPOSE_DIR
  [[ "$workdir" == "$COMPOSE_DIR" || "$workdir" == "$COMPOSE_DIR/" ]] \
    || die "$container working_dir='$workdir' want '$COMPOSE_DIR'"
  # config_files may be absolute or relative; require basename docker-compose.yml presence
  echo "$config_files" | grep -q 'docker-compose\.yml' \
    || die "$container config_files='$config_files' missing docker-compose.yml"
  echo "IDENTITY_OK container=$container service=$svc"
}

require_layout() {
  [[ -d "$COMPOSE_DIR" ]] || die "compose directory missing: $COMPOSE_DIR"
  cd "$COMPOSE_DIR"
  [[ -f "$COMPOSE_FILE" ]] || die "compose file missing: $COMPOSE_DIR/$COMPOSE_FILE"
  [[ -L "$COMPOSE_FILE" ]] && die "refusing to mutate through a symlink compose file"
  [[ -f judge0.conf ]] || die "judge0.conf missing (expected policy key source)"
  [[ -e .env ]] && die "unexpected .env present; refusing ambiguous config sources"

  verify_container_identity "$EXPECTED_SERVER_CONTAINER" "$EXPECTED_SERVER_SERVICE"
  verify_container_identity "$EXPECTED_WORKERS_CONTAINER" "$EXPECTED_WORKERS_SERVICE"
  verify_container_identity "$EXPECTED_DB_CONTAINER" "$EXPECTED_DB_SERVICE"
  verify_container_identity "$EXPECTED_REDIS_CONTAINER" "$EXPECTED_REDIS_SERVICE"
  echo "LAYOUT_OK project=$EXPECTED_COMPOSE_PROJECT dir=$COMPOSE_DIR file=$COMPOSE_FILE"
}

inspect_live_safe() {
  echo "=== LIVE CONTAINERS (names/status only) ==="
  docker ps -a --filter "name=${EXPECTED_COMPOSE_PROJECT}_" \
    --format 'name={{.Names}} image={{.Image}} status={{.Status}}'
  echo "=== SAFE ENV KEYS (server) ==="
  safe_env_keys_from_container "$EXPECTED_SERVER_CONTAINER"
  echo "=== SAFE ENV KEYS (workers) ==="
  safe_env_keys_from_container "$EXPECTED_WORKERS_CONTAINER"
  echo "=== HEALTH ABOUT ==="
  curl -sS --fail --max-time 10 "$HEALTH_BASE/about" || true
  echo
  echo "=== CONFIG_INFO ==="
  curl -sS --fail --max-time 10 "$HEALTH_BASE/config_info" || true
  echo
}

key_present_in_file() {
  local file=$1 key=$2
  grep -qE "^[[:space:]]*${key}=" "$file" \
    || grep -qE "^[[:space:]]*${key}:" "$file"
}

# Patch only keys that already exist in file. Writes staged copy path on stdout.
# Side file $2 receives newline-separated keys that were patched.
# Temp path is appended to CLEANUP_LIST_FILE (works inside $(...) subshells).
patch_present_keys() {
  local src=$1
  local keys_out=$2
  local tmp key val
  : >"$keys_out"
  tmp=$(mktemp)
  append_cleanup "$tmp"
  cp -a "$src" "$tmp"
  while IFS= read -r line; do
    key=${line%%=*}
    val=${line#*=}
    if grep -qE "^[[:space:]]*${key}=" "$tmp"; then
      sed -i -E "s|^([[:space:]]*${key}=).*|\1${val}|" "$tmp"
      echo "$key" >>"$keys_out"
    elif grep -qE "^[[:space:]]*${key}:" "$tmp"; then
      sed -i -E "s|^([[:space:]]*${key}:[[:space:]]*).*$|\1\"${val}\"|" "$tmp"
      echo "$key" >>"$keys_out"
    fi
  done < <(desired_keys)
  # Do not echo secrets; only the temp path.
  printf '%s\n' "$tmp"
}

candidate_config_files() {
  local f
  # Prefer conf/env policy sources; include compose only if it actually has keys.
  for f in judge0.conf judge0.env server.env workers.env .env "$COMPOSE_FILE"; do
    [[ -f "$f" ]] || continue
    echo "$f"
  done
}

# Build a temp project dir with staged patches; validate coverage + resolved env.
# Sets globals: STAGE_DIR, STAGE_PATCH_MANIFEST (file listing relative paths patched).
plan_and_validate_patch() {
  local file staged keys_file covered_file key missing=0 path_out
  capture_original_count
  STAGE_DIR=$(mktemp -d)
  append_cleanup "$STAGE_DIR"
  STAGE_PATCH_MANIFEST="$STAGE_DIR/patched_files.txt"
  : >"$STAGE_PATCH_MANIFEST"
  covered_file="$STAGE_DIR/covered_keys.txt"
  : >"$covered_file"

  # Copy full project config surface needed for compose config.
  cp -a "$COMPOSE_FILE" "$STAGE_DIR/"
  for file in judge0.conf judge0.env server.env workers.env .env; do
    [[ -f "$file" ]] || continue
    cp -a "$file" "$STAGE_DIR/"
  done

  while IFS= read -r file; do
    [[ -f "$file" ]] || continue
    # Skip files with none of the nine keys.
    local any=0
    while IFS= read -r key; do
      if key_present_in_file "$file" "$key"; then
        any=1
        break
      fi
    done < <(desired_key_names)
    [[ "$any" -eq 1 ]] || continue

    keys_file="$STAGE_DIR/keys.$(basename "$file").txt"
    # Avoid losing cleanup registration: write temp path via FD, not only stdout capture.
    path_out="$STAGE_DIR/path.$(basename "$file").txt"
    # Run in current shell (not command substitution) so append_cleanup is redundant-safe,
    # and also record path via append_cleanup inside the function for $(...) callers.
    staged=$(patch_present_keys "$file" "$keys_file")
    printf '%s' "$staged" >"$path_out"
    # Replace staged project copy with patched content; then securely remove the temp ASAP.
    install -m 600 "$staged" "$STAGE_DIR/$(basename "$file")"
    secure_rm "$staged"
    echo "$(basename "$file")" >>"$STAGE_PATCH_MANIFEST"
    if [[ -s "$keys_file" ]]; then
      cat "$keys_file" >>"$covered_file"
    fi
  done < <(candidate_config_files)

  # Every desired key must be covered by at least one patched file.
  while IFS= read -r key; do
    if ! grep -qx "$key" "$covered_file"; then
      echo "MISSING_KEY_IN_STACK key=$key" >&2
      missing=1
    fi
  done < <(desired_key_names)
  [[ "$missing" -eq 0 ]] || die "cannot apply hardening: not all nine keys present in config files"

  # Confirm staged judge0.conf (if patched) has target values; COUNT unchanged if present.
  if [[ -f "$STAGE_DIR/judge0.conf" ]]; then
    while IFS= read -r line; do
      key=${line%%=*}
      val=${line#*=}
      grep -qE "^${key}=${val}$" "$STAGE_DIR/judge0.conf" \
        || die "staged judge0.conf missing ${key}=${val}"
    done < <(desired_keys)
    if [[ "${JUDGE0_EXPECT_COUNT:-0}" == "1" ]]; then
      local staged_count
      staged_count=$(grep -E '^COUNT=' "$STAGE_DIR/judge0.conf" | head -n1 | cut -d= -f2- || true)
      [[ "$staged_count" == "${JUDGE0_EXPECTED_COUNT}" ]] || die "COUNT changed during patch staging"
    fi
  fi

  validate_resolved_compose_env "$STAGE_DIR" || die "staged resolved compose env validation failed"
  echo "PLAN_VALIDATE_OK staged=$STAGE_DIR"
}

# Returns 0 on success, non-zero on failure (does not exit). Callers decide restore/die.
validate_resolved_compose_env() {
  local project_dir=$1
  local confjson
  confjson=$(mktemp)
  append_cleanup "$confjson"
  if [[ "$SKIP_LIVE_DOCKER" == "yes" ]]; then
    # Offline: synthesize compose-config-like JSON from env_file merge for server/workers.
    python3 - "$project_dir" "$confjson" <<'PY' || return 2
import json, sys, re
from pathlib import Path
root = Path(sys.argv[1])
out = Path(sys.argv[2])
compose = (root / "docker-compose.yml").read_text()
conf = {}
conf_path = root / "judge0.conf"
if conf_path.exists():
    for line in conf_path.read_text().splitlines():
        line=line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k,v=line.split("=",1)
        conf[k]=v
services = {}
for name in ("server", "workers"):
    if not re.search(rf"^\s*{name}\s*:", compose, re.M):
        print(f"FAIL: compose missing service {name}", file=sys.stderr)
        sys.exit(2)
    if "judge0.conf" not in compose:
        print("FAIL: compose does not reference judge0.conf", file=sys.stderr)
        sys.exit(2)
    services[name] = {"environment": dict(conf)}
json.dump({"services": services}, out.open("w"))
PY
  else
    if ! (
      cd "$project_dir"
      docker compose -p "$EXPECTED_COMPOSE_PROJECT" -f "$COMPOSE_FILE" config --format json
    ) >"$confjson"; then
      echo "FAIL: docker compose config --format json failed" >&2
      return 2
    fi
  fi
  if ! python3 "$COMPOSE_ENV_VALIDATOR" <"$confjson"; then
    echo "FAIL: resolved server/workers env validation failed" >&2
    return 2
  fi
  return 0
}

# Apply staged patches atomically: backup ALL targets first, then install ALL; any failure restores all.
apply_patches_atomic() {
  local rel backup staged
  [[ -n "${STAGE_DIR:-}" && -d "$STAGE_DIR" ]] || die "STAGE_DIR missing; run plan_and_validate_patch first"
  [[ -s "$STAGE_PATCH_MANIFEST" ]] || die "no files staged for patching"

  # Phase 1: backups only (live files unchanged aside from new backup siblings).
  while IFS= read -r rel; do
    [[ -n "$rel" ]] || continue
    [[ -f "$rel" ]] || die "live file missing for backup: $rel"
    backup="${rel}.${BACKUP_SUFFIX}"
    [[ ! -e "$backup" ]] || die "backup already exists: $backup"
    cp -a "$rel" "$backup"
    BACKUPS_MADE+=("$backup")
    echo "BACKUP created=$backup"
  done <"$STAGE_PATCH_MANIFEST"

  INSTALL_IN_PROGRESS=1
  # Phase 2: install staged copies; on ANY failure restore every backed-up file.
  while IFS= read -r rel; do
    [[ -n "$rel" ]] || continue
    staged="$STAGE_DIR/$rel"
    [[ -f "$staged" ]] || {
      echo "FAIL: staged file missing: $staged; restoring backups" >&2
      restore_backups
      INSTALL_IN_PROGRESS=0
      die "atomic apply failed (missing staged file)"
    }
    if ! install -m 600 "$staged" "$rel"; then
      echo "FAIL: install failed for $rel; restoring all backups" >&2
      restore_backups
      INSTALL_IN_PROGRESS=0
      die "atomic apply failed"
    fi
    PATCHED_LIVE_FILES+=("$rel")
    echo "PATCHED file=$rel backup=${rel}.${BACKUP_SUFFIX}"
  done <"$STAGE_PATCH_MANIFEST"
  INSTALL_IN_PROGRESS=0
}

restore_backups() {
  local rel backup
  echo "=== RESTORING BACKUPS (multi-file rollback) ==="
  while IFS= read -r rel; do
    [[ -n "$rel" ]] || continue
    backup="${rel}.${BACKUP_SUFFIX}"
    if [[ -f "$backup" ]]; then
      cp -a "$backup" "$rel" || echo "WARN: restore failed for $rel" >&2
      echo "RESTORED file=$rel from $backup"
    fi
  done <"${STAGE_PATCH_MANIFEST:-/dev/null}"
}

recreate_server_workers_only() {
  compose up -d --no-deps --wait "$EXPECTED_SERVER_SERVICE" "$EXPECTED_WORKERS_SERVICE"
}

post_health() {
  curl --fail -sS --max-time 20 "$HEALTH_BASE/about" >/dev/null
  local info
  info=$(curl --fail -sS --max-time 20 "$HEALTH_BASE/config_info")
  echo "=== POST CONFIG_INFO ==="
  echo "$info"
  printf '%s' "$info" | python3 "$CONFIG_INFO_VALIDATOR"
  docker inspect "$EXPECTED_DB_CONTAINER" >/dev/null
  docker inspect "$EXPECTED_REDIS_CONTAINER" >/dev/null
  echo "DB_REDIS_STILL_PRESENT=yes"
}

# Planned rollback after a successful harden (or dry-run preview). Config_info
# check is OK here because we expect hardened state after a successful apply.
print_rollback() {
  local rel
  echo "=== ROLLBACK (preserves db/redis data; no volume deletes) ==="
  echo "cd $COMPOSE_DIR"
  if [[ ${#PATCHED_LIVE_FILES[@]} -eq 0 && -f "${STAGE_PATCH_MANIFEST:-}" ]]; then
    while IFS= read -r rel; do
      [[ -n "$rel" ]] || continue
      echo "cp -a ${rel}.${BACKUP_SUFFIX} ${rel}"
    done <"$STAGE_PATCH_MANIFEST"
  else
    for rel in "${PATCHED_LIVE_FILES[@]:-}"; do
      echo "cp -a ${rel}.${BACKUP_SUFFIX} ${rel}"
    done
  fi
  echo "docker compose -p ${EXPECTED_COMPOSE_PROJECT} -f ${COMPOSE_FILE} up -d --no-deps --wait ${EXPECTED_SERVER_SERVICE} ${EXPECTED_WORKERS_SERVICE}"
  echo "curl --fail -sS ${HEALTH_BASE}/about"
  echo "curl -sS ${HEALTH_BASE}/config_info"
  echo "# NOTE: after rollback, config_info reflects ORIGINAL (possibly less restrictive) settings."
  echo "# Do NOT pipe rollback config_info into ${CONFIG_INFO_VALIDATOR} (that validator expects hardened values)."
  echo "# Do NOT: docker compose down -v, volume rm, or delete historical containers."
}

# Exact manual commands when recreate or post-health fails. Never uses hardened JSON validator.
print_manual_rollback_on_failure() {
  local rel
  echo "=== MANUAL ROLLBACK REQUIRED (recreate/health failure) ===" >&2
  echo "cd $COMPOSE_DIR" >&2
  if [[ -f "${STAGE_PATCH_MANIFEST:-}" ]]; then
    while IFS= read -r rel; do
      [[ -n "$rel" ]] || continue
      echo "cp -a ${rel}.${BACKUP_SUFFIX} ${rel}" >&2
    done <"$STAGE_PATCH_MANIFEST"
  else
    for rel in "${PATCHED_LIVE_FILES[@]:-}"; do
      echo "cp -a ${rel}.${BACKUP_SUFFIX} ${rel}" >&2
    done
  fi
  echo "docker compose -p ${EXPECTED_COMPOSE_PROJECT} -f ${COMPOSE_FILE} up -d --no-deps --wait ${EXPECTED_SERVER_SERVICE} ${EXPECTED_WORKERS_SERVICE}" >&2
  echo "curl --fail -sS ${HEALTH_BASE}/about" >&2
  echo "curl -sS ${HEALTH_BASE}/config_info" >&2
  echo "# Do NOT use ${CONFIG_INFO_VALIDATOR} against rolled-back (original) config_info." >&2
  echo "# Do NOT: docker compose down -v, volume rm, or delete historical containers." >&2
}

fail_after_apply() {
  local msg=$1
  echo "FAIL: $msg" >&2
  print_manual_rollback_on_failure
  exit 2
}

# --- main ---
[[ -f "$CONFIG_INFO_VALIDATOR" ]] || die "missing $CONFIG_INFO_VALIDATOR"
[[ -f "$COMPOSE_ENV_VALIDATOR" ]] || die "missing $COMPOSE_ENV_VALIDATOR"

if [[ "$SKIP_LIVE_DOCKER" == "yes" ]]; then
  [[ -d "$COMPOSE_DIR" ]] || die "COMPOSE_DIR missing: $COMPOSE_DIR"
  cd "$COMPOSE_DIR"
  [[ -f "$COMPOSE_FILE" ]] || die "compose file missing"
  [[ -f judge0.conf ]] || die "judge0.conf missing"
  echo "OFFLINE_MODE=yes"
  echo "=== DESIRED HARDENING ==="
  desired_keys
  plan_and_validate_patch
  if [[ "$DRY_RUN" == "yes" ]]; then
    echo "DRY_RUN: would backup then atomically patch files listed above, then recreate ONLY server and workers with --no-deps"
    echo "DRY_RUN: would NOT recreate db/redis; would NOT apply no-cgroup patches"
    print_rollback
    exit 0
  fi
  die "offline mode cannot mutate"
fi

require_host
check_cgroup
require_layout
inspect_live_safe
echo "=== DESIRED HARDENING ==="
desired_keys
plan_and_validate_patch

if [[ "$DRY_RUN" == "yes" ]]; then
  echo "DRY_RUN: would backup then atomically patch under $COMPOSE_DIR, then recreate ONLY server and workers"
  echo "DRY_RUN: db/redis left untouched; cgroup-v1/--cg path unchanged"
  print_rollback
  exit 0
fi

[[ "$APPROVE" == "yes" ]] || die "refusing mutate: set DRY_RUN=no APPROVE=yes after review"

DB_ID_BEFORE=$(docker inspect -f '{{.Id}}' "$EXPECTED_DB_CONTAINER")
REDIS_ID_BEFORE=$(docker inspect -f '{{.Id}}' "$EXPECTED_REDIS_CONTAINER")

apply_patches_atomic

# Validate ACTUAL resolved Compose after files are live, BEFORE recreating containers.
if ! validate_resolved_compose_env "$COMPOSE_DIR"; then
  echo "FAIL: post-apply compose validation failed; restoring backups" >&2
  restore_backups
  print_manual_rollback_on_failure
  exit 2
fi

if ! recreate_server_workers_only; then
  echo "FAIL: container recreation failed; restoring config files from backup" >&2
  restore_backups
  fail_after_apply "server/workers recreate failed — configs restored from backup; recreate manually if needed"
fi

DB_ID_AFTER=$(docker inspect -f '{{.Id}}' "$EXPECTED_DB_CONTAINER")
REDIS_ID_AFTER=$(docker inspect -f '{{.Id}}' "$EXPECTED_REDIS_CONTAINER")
if [[ "$DB_ID_BEFORE" != "$DB_ID_AFTER" ]]; then
  fail_after_apply "db container id changed unexpectedly"
fi
if [[ "$REDIS_ID_BEFORE" != "$REDIS_ID_AFTER" ]]; then
  fail_after_apply "redis container id changed unexpectedly"
fi

if ! post_health; then
  fail_after_apply "post-health verification failed — restore configs from backup and recreate server/workers"
fi

print_rollback
echo "JUDGE0_HARDEN_OK backup_suffix=$BACKUP_SUFFIX project=$EXPECTED_COMPOSE_PROJECT"
