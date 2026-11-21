#!/bin/bash
# LOCAL DEMO ONLY — disables isolate --cg (weakens sandbox resource limits).
# NOT approved for production, staging, or any shared Judge0 host.
#
# Requires: CODERZCLUB_ALLOW_INSECURE_LOCAL_JUDGE0=1
# Must NOT be wired into docker-compose.production.yml, k8s, or GCP releases.
#
# See docs/production-readiness/JUDGE0_SANDBOX.md
set -euo pipefail

if [ "${CODERZCLUB_ALLOW_INSECURE_LOCAL_JUDGE0:-}" != "1" ]; then
  echo "REFUSED: set CODERZCLUB_ALLOW_INSECURE_LOCAL_JUDGE0=1 to patch local Judge0 only." >&2
  echo "Disabling --cg is not an approved production security configuration." >&2
  exit 2
fi

if [ -f /.dockerenv ] || grep -qE '/docker|/lxc' /proc/1/cgroup 2>/dev/null; then
  :
else
  echo "REFUSED: expected to run via docker exec inside a local Judge0 container." >&2
  exit 2
fi

TARGET=/api/app/jobs/isolate_job.rb
cp "$TARGET" "${TARGET}.bak" 2>/dev/null || true
/usr/local/ruby-2.7.0/bin/ruby - <<'RUBY'
path = "/api/app/jobs/isolate_job.rb"
text = File.read(path)
old = '@cgroups = (!submission.enable_per_process_and_thread_time_limit || !submission.enable_per_process_and_thread_memory_limit) ? "--cg" : ""'
new = '@cgroups = "" # LOCAL DEMO ONLY — insecure without cgroups; never production'
if text.include?(old)
  text = text.sub(old, new)
elsif text.include?('@cgroups = "" # LOCAL DEMO ONLY') || text.include?('@cgroups = "" # local demo')
  warn "cgroups_already_patched"
else
  abort "cgroups pattern not found"
end
text = text.gsub(
  '#{submission.enable_per_process_and_thread_time_limit ? (cgroups.present? ? "--no-cg-timing" : "") : "--cg-timing"}',
  '#{""}'
)
text = text.gsub(
  '#{submission.enable_per_process_and_thread_memory_limit ? "-m " : "--cg-mem="}#{Config::MAX_MEMORY_LIMIT}',
  '#{"-m "}#{Config::MAX_MEMORY_LIMIT}'
)
text = text.gsub(
  '#{submission.enable_per_process_and_thread_memory_limit ? "-m " : "--cg-mem="}#{submission.memory_limit}',
  '#{"-m "}#{submission.memory_limit}'
)
text = text.sub(
  'submission.memory = (cgroups.present? ? metadata[:"cg-mem"] : metadata[:"max-rss"])',
  'submission.memory = metadata[:"max-rss"]'
)
File.write(path, text)
puts "patched_ok_local_insecure_demo_only"
RUBY
grep -n '@cgroups\|--cg-timing\|--cg-mem\|max-rss' "$TARGET" | head -20
