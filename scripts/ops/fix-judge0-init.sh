#!/bin/bash
# LOCAL DEMO ONLY companion to patch-judge0-no-cgroup.sh.
# Requires: CODERZCLUB_ALLOW_INSECURE_LOCAL_JUDGE0=1
# See docs/production-readiness/JUDGE0_SANDBOX.md
set -euo pipefail

if [ "${CODERZCLUB_ALLOW_INSECURE_LOCAL_JUDGE0:-}" != "1" ]; then
  echo "REFUSED: set CODERZCLUB_ALLOW_INSECURE_LOCAL_JUDGE0=1 for local Judge0 init fix only." >&2
  exit 2
fi

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

if [ -d /var/local/lib/isolate ]; then
  for d in /var/local/lib/isolate/*; do
    [ -d "$d" ] || continue
    id=$(basename "$d")
    isolate -b "$id" --cleanup >/dev/null 2>&1 || rm -rf "$d"
  done
fi

/usr/local/ruby-2.7.0/bin/ruby <<'RUBY'
path = "/api/app/jobs/isolate_job.rb"
text = File.read(path)
if text.include?("Isolate init failed for box")
  puts "init_patch_already"
else
  old = '@workdir = `isolate #{cgroups} -b #{box_id} --init`.chomp'
  new = <<~'LINE'.strip
    `isolate #{cgroups} -b #{box_id} --cleanup`
        @workdir = `isolate #{cgroups} -b #{box_id} --init`.chomp
        raise "Isolate init failed for box #{box_id}" if workdir.to_s.strip.empty?
  LINE
  abort "workdir line missing" unless text.include?(old)
  text = text.sub(old, new)
  File.write(path, text)
  puts "init_patch_ok_local_demo_only"
end
RUBY

grep -n 'workdir\|Isolate init failed\|@cgroups' /api/app/jobs/isolate_job.rb | head -20
isolate --version
echo fix_done_local_demo_only
