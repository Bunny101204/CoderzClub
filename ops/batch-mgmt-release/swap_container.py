#!/usr/bin/env python3
"""Replace one API or worker container with the verified release image.

Dry-run reads the live container and prints the plan. It does not rename,
stop, or create anything. A real swap requires CONFIRM_DEPLOY and a fresh
inspect that records runtime security settings. The previous container is
renamed and kept. Historical backup containers are never removed.
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
import swap_safety as safety


def fail(message, code=3):
    print(message, file=sys.stderr)
    raise SystemExit(code)


def docker(*args, check=True):
    completed = subprocess.run(["docker", *args], check=False, text=True, capture_output=True)
    if check and completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip() or f"docker {' '.join(args)} failed"
        redacted = detail.replace("\n", " ")
        for marker in ("mongodb", "password", "secret", "jwt", "bearer"):
            if marker in redacted.lower():
                redacted = "docker command failed; secret-bearing output was redacted"
                break
        fail(redacted)
    return completed


def same(left, right, label, errors):
    if left != right:
        errors.append(label)


def binding_for(bindings, port):
    matches = [item for item in bindings if item["containerPort"] == f"{port}/tcp"]
    if len(matches) != 1:
        return None
    return matches[0]


def http_text(url):
    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            return response.status, response.read(65536).decode("utf-8", "replace")
    except Exception:
        return None, ""


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
    command, errors = safety.create_command(record, host.RELEASE_IMAGE)
    if errors:
        fail("Stopping before any container change:\n- " + "\n- ".join(errors))
    insert_at = command.index(host.RELEASE_IMAGE)
    command[insert_at:insert_at] = ["--env-file", env_file]
    completed = subprocess.run(command, check=False, text=True, capture_output=True)
    if completed.returncode != 0:
        fail(completed.stderr.strip() or "docker create failed")


def container_running(name):
    completed = docker("inspect", "--format", "{{.State.Running}}", name, check=False)
    return completed.returncode == 0 and completed.stdout.strip() == "true"


def restore(original, parked, created, parked_id):
    print("Restoring the previous container.")
    if created and safety.removal_allowed(original, None, parked_id, original):
        docker("stop", "--time", str(safety.DOCKER_STOP_SECONDS), original, check=False)
        docker("rm", original, check=False)
    if not container_running(parked):
        docker("start", parked, check=False)
    docker("rename", parked, original, check=False)
    print(f"rollback-recovered={'yes' if container_running(original) else 'no'}")


def wait_ready(role, app_binding, management_binding, log_text, queues):
    deadline = time.time() + 120
    ready_url = (
        f"http://{management_binding['hostIp']}:{management_binding['hostPort']}"
        "/actuator/health/readiness"
    )
    app_url = None
    if role == "api":
        app_url = f"http://{app_binding['hostIp']}:{app_binding['hostPort']}/api/problems/test"
    while time.time() < deadline:
        status, body = http_text(ready_url)
        ready = status == 200 and safety.ready_body(body)
        if role == "api":
            app_status, _ = http_text(app_url)
            ready = ready and app_status == 200
        if role == "worker" and ready:
            gaps = safety.consumer_gaps(log_text(), queues[0], queues[1])
            ready = not gaps
        if ready:
            return True
        time.sleep(3)
    return False


def emit_dry_run(role, container, errors):
    ports = ",".join(f"{host_ip}:{host_port}" for _, host_ip, host_port in safety.service_ports(role))
    print(f"dry-run role={role} container={container} ports={ports}")
    if role == "worker":
        print("consumers=submission,contest-run")
        print("handoff=stop-old-before-start")
        print(f"stop-seconds={safety.DOCKER_STOP_SECONDS}")
    print("mutation=none")
    if errors:
        print("errors=" + "; ".join(errors))
        return 3
    return 0


def main():
    mode = safety.mode_error(os.environ)
    if mode and os.environ.get("DRY_RUN") == "yes" and os.environ.get("CONFIRM_DEPLOY"):
        fail(mode, 2)
    if mode and os.environ.get("DRY_RUN") != "yes":
        fail(mode, 2)
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
        ("read only", "readonlyRootfs"),
        ("capabilities", "capDrop"),
        ("security options", "securityOpt"),
    ):
        same(current.get(key), previous.get(key), f"{label} changed since inspect", errors)
    if live.get("releaseImagePresent") != host.RELEASE_IMAGE_ID:
        errors.append("verified release image is not loaded at the expected id")
    safe = current.get("safeEnv") or {}
    ports = safety.service_ports(role)
    app_binding = binding_for(current["bindings"], ports[0][2])
    management_binding = binding_for(current["bindings"], ports[1][2])
    if app_binding is None or management_binding is None:
        errors.append("required private port binding is missing")
    submission, contest, queue_errors = safety.queue_names(safe)
    if role == "worker":
        errors.extend(queue_errors)
    _, create_errors = safety.create_command(current, host.RELEASE_IMAGE)
    errors.extend(create_errors)
    if os.environ.get("DRY_RUN") == "yes":
        return emit_dry_run(role, container, errors)
    if errors:
        fail("Stopping before any container change:\n- " + "\n- ".join(errors))

    env_file = write_env_file(container)
    created = False
    renamed = False
    restored = False
    parked_id = current.get("id")
    try:
        docker("rename", container, parked)
        renamed = True
        docker("stop", "--time", str(safety.DOCKER_STOP_SECONDS), parked)
        if safety.handoff_blocked(container_running(parked)):
            restore(container, parked, False, parked_id)
            restored = True
            fail("Previous container is still running. New container was not started.")
        create_container(current, env_file)
        created = True
        if safety.handoff_blocked(container_running(parked)):
            restore(container, parked, created, parked_id)
            restored = True
            fail("Previous container started again before handoff. Restoring it.")
        docker("start", container)

        def log_text():
            completed = docker("logs", container, check=False)
            return completed.stdout + completed.stderr

        if not wait_ready(role, app_binding, management_binding, log_text, (submission, contest)):
            restore(container, parked, created, parked_id)
            restored = True
            fail("New container did not become ready. Previous container restore was requested.")
    except SystemExit:
        if renamed and not created and not restored:
            if not container_running(parked):
                docker("start", parked, check=False)
            docker("rename", parked, container, check=False)
            print(f"rollback-recovered={'yes' if container_running(container) else 'no'}")
        raise
    finally:
        if os.path.exists(env_file):
            os.remove(env_file)
    print(f"Started {container} from {host.RELEASE_IMAGE}. Kept {parked} for rollback.")
    if role == "worker":
        print("consumer-evidence=started")
    return 0


if __name__ == "__main__":
    sys.exit(main())
