#!/bin/bash
set -euxo pipefail
install -m 4755 /tmp/isolate-orig /usr/local/bin/isolate
cp /tmp/isolate-orig.conf /usr/local/etc/isolate
isolate --version
isolate -b 9 --cleanup >/dev/null 2>&1 || true
BOX=$(isolate -b 9 --init)
echo "BOX=$BOX"
ls -la "$BOX" "$BOX/box"
printf 'print(1)\n' > "$BOX/box/script.py"
# Match how Judge0 typically invokes isolate (dirs visible)
isolate -b 9 \
  --dir=etc=/etc \
  --dir=usr=/usr \
  --dir=bin=/bin \
  --dir=lib=/lib \
  --dir=lib64=/lib64 \
  --dir=dev=/dev \
  --dir=proc=/proc \
  --meta=/tmp/meta-9.txt \
  --run -- /usr/bin/python3 script.py || true
echo "meta:"; cat /tmp/meta-9.txt || true
isolate -b 9 --cleanup || true
