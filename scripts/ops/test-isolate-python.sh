#!/bin/bash
set -euxo pipefail
# Prefer isolate 2.0 binary if present
if [ -x /tmp/isolate/isolate ]; then
  install -m 4755 /tmp/isolate/isolate /usr/local/bin/isolate
  mkdir -p /run/isolate/locks /var/local/lib/isolate /sys/fs/cgroup/isolate.scope || true
  cat > /usr/local/etc/isolate <<'EOF'
box_root = /var/local/lib/isolate
lock_root = /run/isolate/locks
cg_root = /sys/fs/cgroup/isolate.scope
first_uid = 60000
first_gid = 60000
num_boxes = 1000
EOF
fi
isolate --version
isolate -b 11 --cleanup >/dev/null 2>&1 || true
BOX=$(isolate -b 11 --init)
echo "BOX=$BOX"
ls -la "$BOX" "$BOX/box"
printf 'print(1)\n' > "$BOX/box/script.py"
isolate -b 11 \
  --dir=etc=/etc \
  --dir=usr=/usr \
  --dir=bin=/bin \
  --dir=lib=/lib \
  --dir=lib64=/lib64 \
  --dir=dev=/dev \
  --dir=proc=/proc \
  --processes=20 \
  --meta=/tmp/meta-11.txt \
  --stdout=/tmp/out-11.txt \
  --run -- /usr/bin/python3 script.py
echo "stdout:"; cat /tmp/out-11.txt || true
echo "meta:"; cat /tmp/meta-11.txt || true
isolate -b 11 --cleanup || true
