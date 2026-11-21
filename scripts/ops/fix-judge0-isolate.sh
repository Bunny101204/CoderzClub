#!/bin/bash
set -euo pipefail
mkdir -p /run/isolate/locks /var/local/lib/isolate /tmp/isolate-build

if [ ! -x /tmp/isolate/isolate ]; then
  echo "isolate binary missing at /tmp/isolate/isolate" >&2
  exit 1
fi

install -m 4755 /tmp/isolate/isolate /usr/local/bin/isolate

if [ -f /sys/fs/cgroup/cgroup.controllers ]; then
  echo "controllers=$(cat /sys/fs/cgroup/cgroup.controllers)"
  for c in memory cpu pids cpuset io; do
    echo "+${c}" > /sys/fs/cgroup/cgroup.subtree_control 2>/dev/null || true
  done
  mkdir -p /sys/fs/cgroup/isolate.scope
  echo "subtree=$(cat /sys/fs/cgroup/cgroup.subtree_control 2>/dev/null || true)"
fi

cat > /usr/local/etc/isolate <<'EOF'
box_root = /var/local/lib/isolate
lock_root = /run/isolate/locks
cg_root = /sys/fs/cgroup/isolate.scope
first_uid = 60000
first_gid = 60000
num_boxes = 1000
EOF

isolate --version
isolate --cg -b 5 --cleanup >/dev/null 2>&1 || true
isolate --cg -b 5 --init
echo "init_ok"
isolate --cg -b 5 --processes --share-net -- /bin/echo hello-from-isolate
echo "run_ok"
isolate --cg -b 5 --cleanup
echo "cleanup_ok"
