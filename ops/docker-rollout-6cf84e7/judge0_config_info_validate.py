#!/usr/bin/env python3
"""Validate Judge0 /config_info JSON from stdin. Exit 0 on success, 3 on failure.

Booleans must be JSON booleans (not strings).
Numbers must equal exactly (15 and 15.0 OK; 15.5 not OK for expected 15).
"""
from __future__ import annotations

import json
import sys


def dig(obj, *keys):
    for key in keys:
        if isinstance(obj, dict) and key in obj:
            return obj[key]
    for nest in ("data", "config", "info"):
        if isinstance(obj, dict) and isinstance(obj.get(nest), dict):
            for key in keys:
                if key in obj[nest]:
                    return obj[nest][key]
    return None


def numbers_equal(actual, expected) -> bool:
    """Exact numeric equality; 15 == 15.0, but 15.5 != 15."""
    if isinstance(actual, bool) or isinstance(expected, bool):
        return False
    if not isinstance(actual, (int, float)) or not isinstance(expected, (int, float)):
        return False
    return float(actual) == float(expected)


def main() -> int:
    raw = sys.stdin.read()
    try:
        data = json.loads(raw)
    except Exception as exc:
        print(f"FAIL: config_info is not JSON: {exc}", file=sys.stderr)
        return 3

    bool_checks = [
        ("enable_network", False),
        ("allow_enable_network", False),
        ("enable_per_process_and_thread_time_limit", False),
        ("allow_enable_per_process_and_thread_time_limit", False),
        ("enable_per_process_and_thread_memory_limit", False),
        ("allow_enable_per_process_and_thread_memory_limit", False),
    ]
    num_checks = [
        ("max_cpu_time_limit", 15),
        ("max_wall_time_limit", 20),
        ("max_memory_limit", 512000),
    ]
    errors = []
    for key, expected in bool_checks:
        actual = dig(data, key, key.upper())
        if actual is None:
            errors.append(f"missing {key}")
        elif not isinstance(actual, bool):
            errors.append(f"{key} must be JSON boolean, got {actual!r}")
        elif actual is not expected:
            errors.append(f"{key}={actual!r} want {expected}")
    for key, expected in num_checks:
        actual = dig(data, key, key.upper())
        if actual is None:
            errors.append(f"missing {key}")
            continue
        if isinstance(actual, bool) or not isinstance(actual, (int, float)):
            errors.append(f"{key} must be JSON number, got {actual!r}")
            continue
        if not numbers_equal(actual, expected):
            errors.append(f"{key}={actual!r} want {expected}")
    if errors:
        print("FAIL: config_info validation: " + "; ".join(errors), file=sys.stderr)
        return 3
    print("CONFIG_INFO_JSON_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
