#!/usr/bin/env python3
"""Replace one API or worker container with the verified release image.

The script re-reads the live container and stops if it differs from the inspect
file. The previous container is renamed and kept. A failed health check starts
that container again under its original name.
"""

import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import inspect_host as host

CONFIRM = "batch-mgmt-20261017"


def fail(message, code=3):
    print(message, file=sys.stderr)
    raise SystemExit(code)


def docker(*args, check=True):
    completed = subprocess.run(["docker", *args], check=False, text=True, capture_output=True)
    if check and completed.returncode != 0:
        fail(completed.stderr.strip() or completed.stdout.strip() or f"docker {' '.join(args)} failed")
    return completed


def same(left, right, label, errors):
    if left != right:
        errors.append(label)


def binding_for(bindings, port):
    matches = [item for item in bindings if item["containerPort"] == f"{port}/tcp"]
    if len(matches) != 1:
        return None
    return matches[0]


def http_ok(url, needle=None):
    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            body = response.read(4096).decode("utf-8", "replace")
            if response.status != 200:
                return False
            return needle is None or needle in body
    except Exception:
        return False


def write_env_file(container):
    completed = docker("inspect", "--format", "{{range .Config.Env}}{{println .}}{{end}}", container)
    handle = tempfile.NamedTemporaryFile("w", encoding="utf-8", prefix="coderzclub-env-", delete=False)
    try:
        os.chmod(handle.name, 0o600)
        handle.write(completed.stdout)
        if not completed.stdout.endswith("\n"):
            handle.write("\n")
        handle.close()
    except Exception:
        handle.close()
        os.unlink(handle.name)
        raise
    return handle.name


def create_container(record, env_file):
    command = [
        "docker", "create",
        "--name", record["name"],
        "--restart", "unless-stopped",
        "--network", record["network"] or "default",
        "--env-file", env_file,
    ]
    if record["user"]:
        command.extend(["--user", record["user"]])
    for binding in record["bindings"]:
        port = binding["containerPort"].split("/")[0]
        command.extend(["-p", f"{binding['hostIp']}:{binding['hostPort']}:{port}"])
    for mount in record["binds"]:
        command.extend(["-v", mount])
    entrypoint = record["entrypoint"] or []
    if entrypoint:
        command.extend(["--entrypoint", entrypoint[0]])
    command.append(host.RELEASE_IMAGE)
    command.extend(entrypoint[1:])
    command.extend(record["cmd"] or [])
    completed = subprocess.run(command, check=False, text=True, capture_output=True)
    if completed.returncode != 0:
        fail(completed.stderr.strip() or "docker create failed")


def restore(original, parked, created):
    print("Health check failed. Restoring the previous container.")
    if created:
        docker("stop", original, check=False)
        docker("rm", original, check=False)
    docker("start", parked, check=False)
    docker("rename", parked, original, check=False)


def wait_ready(role, app_binding, management_binding):
    deadline = time.time() + 120
    app_url = None
    if role == "api":
        app_url = f"http://{app_binding['hostIp']}:{app_binding['hostPort']}/api/problems/test"
    ready_url = (
        f"http://{management_binding['hostIp']}:{management_binding['hostPort']}"
        "/actuator/health/readiness"
    )
    while time.time() < deadline:
        app_ok = http_ok(app_url) if app_url else True
        if app_ok and http_ok(ready_url, '"UP"') and http_ok(ready_url, '"status"'):
            return True
        time.sleep(3)
    return False


def main():
    if os.environ.get("CONFIRM_DEPLOY") != CONFIRM:
        fail(f"Refusing: set CONFIRM_DEPLOY={CONFIRM} only after approval.", 2)
    role = os.environ.get("RELEASE_ROLE", "")
    container = os.environ.get("CONTAINER_NAME", "")
    inspect_path = os.environ.get("INSPECT_FILE", "")
    if role not in {"api", "worker"} or not container or not inspect_path:
        fail("Set RELEASE_ROLE, CONTAINER_NAME, EXPECTED_HOSTNAME, and INSPECT_FILE.", 2)
    if not os.environ.get("EXPECTED_HOSTNAME"):
        fail("Set EXPECTED_HOSTNAME from the VM name before swapping.", 2)
    parked = f"{container}-before-batch-mgmt"
    if docker("container", "inspect", parked, check=False).returncode == 0:
        fail(f"{parked} already exists. Leave it for rollback and choose a fresh inspect.", 2)

    recorded = json.loads(Path(inspect_path).read_text(encoding="utf-8"))
    live = host.inventory(container)
    errors = host.validate_report(live, role)
    current = live["container"]
    previous = recorded.get("container") or {}
    for label, key in (
        ("container id", "id"),
        ("image id", "imageId"),
        ("restart policy", "restart"),
        ("network", "network"),
        ("mounts", "binds"),
        ("user", "user"),
        ("entrypoint", "entrypoint"),
        ("command", "cmd"),
        ("environment names", "envKeys"),
        ("environment hash", "envSha256"),
        ("approved settings", "safeEnv"),
        ("port bindings", "bindings"),
    ):
        same(current.get(key), previous.get(key), f"{label} changed since inspect", errors)
    if live.get("releaseImagePresent") != host.RELEASE_IMAGE_ID:
        errors.append("verified release image is not loaded at the expected id")
    safe = current.get("safeEnv") or {}
    app_binding = binding_for(current["bindings"], safe.get("SERVER_PORT") or "")
    management_binding = binding_for(current["bindings"], safe.get("MANAGEMENT_SERVER_PORT") or "")
    if role == "api" and app_binding is None:
        errors.append("SERVER_PORT has no single private binding")
    if management_binding is None:
        errors.append("MANAGEMENT_SERVER_PORT has no single private binding")
    if errors:
        fail("Stopping before any container change:\n- " + "\n- ".join(errors))

    env_file = write_env_file(container)
    created = False
    renamed = False
    try:
        docker("rename", container, parked)
        renamed = True
        docker("stop", parked)
        create_container(current, env_file)
        created = True
        docker("start", container)
        if not wait_ready(role, app_binding, management_binding):
            restore(container, parked, created)
            fail("New container did not become ready. Previous container restore was requested.")
    except SystemExit:
        if renamed and not created:
            docker("start", parked, check=False)
            docker("rename", parked, container, check=False)
        raise
    finally:
        if os.path.exists(env_file):
            os.remove(env_file)
    print(f"Started {container} from {host.RELEASE_IMAGE}. Kept {parked} for rollback.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
