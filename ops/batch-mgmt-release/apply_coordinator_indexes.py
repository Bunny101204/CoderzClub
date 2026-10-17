#!/usr/bin/env python3
"""Check coordinator indexes, then apply them only when they are absent.

Requires CONFIRM_INDEXES=batch-mgmt-20261017 and MONGODB_URI in the environment.
The URI is never written to disk by this script. This does not run
backend/mongodb-indexes.js.
"""

import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHECK = ROOT / "backend" / "mongodb-coordinator-indexes-check.js"
APPLY = ROOT / "backend" / "mongodb-coordinator-indexes.js"
CONFIRM = "batch-mgmt-20261017"


def classify(text, returncode):
    if "duplicate coordinator" in text or "conflicting non-unique" in text:
        return 1
    if "coordinator indexes are ready" in text:
        return 0
    if "is absent" in text or "are incomplete" in text:
        return 10
    return returncode


def run(script):
    completed = subprocess.run(
        ["mongosh", os.environ["MONGODB_URI"], "--quiet", str(script)],
        check=False,
        text=True,
        capture_output=True,
    )
    sys.stdout.write(completed.stdout)
    sys.stderr.write(completed.stderr)
    return classify(completed.stdout + completed.stderr, completed.returncode)


def main():
    if os.environ.get("CONFIRM_INDEXES") != CONFIRM:
        print(f"Refusing: set CONFIRM_INDEXES={CONFIRM} after the release is approved.", file=sys.stderr)
        return 2
    if not os.environ.get("MONGODB_URI"):
        print("Refusing: MONGODB_URI must be supplied in the environment.", file=sys.stderr)
        return 2
    for path in (CHECK, APPLY):
        if not path.is_file():
            print(f"Missing {path}", file=sys.stderr)
            return 2
    status = run(CHECK)
    if status == 0:
        print("Coordinator indexes already match the release. No change made.")
        return 0
    if status != 10:
        print(f"Index check failed with status {status}. No index was created.", file=sys.stderr)
        return status
    print("Indexes are absent or incomplete. Applying the coordinator script.")
    applied = run(APPLY)
    if applied != 0:
        print(f"Index apply failed with status {applied}.", file=sys.stderr)
        return applied
    verified = run(CHECK)
    if verified != 0:
        print(f"Indexes were applied but verification returned {verified}.", file=sys.stderr)
        return verified or 1
    print("Coordinator indexes verified after apply.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
