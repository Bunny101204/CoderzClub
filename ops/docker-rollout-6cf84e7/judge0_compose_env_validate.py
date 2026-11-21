#!/usr/bin/env python3
"""Validate resolved Compose JSON env for server/workers hardening keys.

Reads `docker compose config --format json` from stdin.
Does not print secret values.

COUNT handling:
  If JUDGE0_EXPECT_COUNT=1, require COUNT on server/workers to equal
  JUDGE0_EXPECTED_COUNT exactly (empty string is valid and must be preserved).
  Never require a nonempty COUNT and never force COUNT=6.
"""
from __future__ import annotations

import json
import os
import sys

REQUIRED = {
    "ENABLE_NETWORK": "false",
    "ALLOW_ENABLE_NETWORK": "false",
    "ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT": "false",
    "ALLOW_ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT": "false",
    "ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT": "false",
    "ALLOW_ENABLE_PER_PROCESS_AND_THREAD_MEMORY_LIMIT": "false",
    "MAX_CPU_TIME_LIMIT": "15",
    "MAX_WALL_TIME_LIMIT": "20",
    "MAX_MEMORY_LIMIT": "512000",
}
SERVICES = ("server", "workers")


def env_map(service_obj):
    env = service_obj.get("environment") or {}
    if isinstance(env, list):
        out = {}
        for item in env:
            if isinstance(item, str) and "=" in item:
                k, v = item.split("=", 1)
                out[k] = v
            elif isinstance(item, dict):
                out.update({str(k): "" if v is None else str(v) for k, v in item.items()})
        return out
    if isinstance(env, dict):
        return {str(k): "" if v is None else str(v) for k, v in env.items()}
    return {}


def main() -> int:
    try:
        data = json.load(sys.stdin)
    except Exception as exc:
        print(f"FAIL: compose config is not JSON: {exc}", file=sys.stderr)
        return 2
    services = data.get("services") or {}
    errors = []
    expect_count = os.environ.get("JUDGE0_EXPECT_COUNT") == "1"
    expected_count = os.environ.get("JUDGE0_EXPECTED_COUNT", "")
    for name in SERVICES:
        svc = services.get(name)
        if not isinstance(svc, dict):
            errors.append(f"missing service {name}")
            continue
        env = env_map(svc)
        for key, want in REQUIRED.items():
            got = env.get(key)
            if got is None:
                errors.append(f"{name} missing {key}")
            elif str(got) != want:
                errors.append(f"{name} {key}={got!r} want {want!r}")
        if expect_count:
            if "COUNT" not in env:
                errors.append(f"{name} missing COUNT (expected original {expected_count!r})")
            elif str(env["COUNT"]) != expected_count:
                errors.append(
                    f"{name} COUNT={env['COUNT']!r} want original {expected_count!r}"
                )
    if errors:
        print("FAIL: resolved compose env: " + "; ".join(errors), file=sys.stderr)
        return 2
    print("RESOLVED_COMPOSE_ENV_OK services=server,workers")
    return 0


if __name__ == "__main__":
    sys.exit(main())
