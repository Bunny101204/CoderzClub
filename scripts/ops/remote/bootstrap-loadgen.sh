#!/usr/bin/env bash
set -euo pipefail
command -v k6 >/dev/null || { echo 'k6 is not installed; install a reviewed pinned k6 package before testing' >&2; exit 1; }
k6 version
command -v curl >/dev/null
command -v vmstat >/dev/null || true
free -h
nproc
