#!/bin/bash
set -euxo pipefail
export PATH="/usr/local/bin:$PATH"
isolate --version
isolate -b 12 --cleanup >/dev/null 2>&1 || true
BOX=$(isolate -b 12 --init)
echo "BOX=$BOX"
ls -la "$BOX" "$BOX/box"
if touch "$BOX/box/script.py"; then echo touch_ok; else echo touch_fail; fi
id
sudo -n true && echo sudo_ok || echo sudo_fail
sudo chown -R "$(whoami):" "$BOX/box"
printf 'print(1)\n' > "$BOX/box/script.py"
isolate -b 12 --dir=etc=/etc --dir=usr=/usr --dir=bin=/bin --dir=lib=/lib --dir=lib64=/lib64 --dir=dev=/dev --dir=proc=/proc --processes=20 --meta=/tmp/meta-12.txt --run -- /usr/bin/python3 script.py
cat /tmp/meta-12.txt
isolate -b 12 --cleanup
