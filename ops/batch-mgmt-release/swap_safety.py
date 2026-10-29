"""Pure checks for a CoderzClub API or worker container swap.

These functions do not talk to Docker or the network. The swap script uses
them so a dry run can fail closed before any container is renamed.
"""

import json
import re

CONFIRM = "batch-mgmt-20261017"
DOCKER_STOP_SECONDS = 30
LISTENER_SHUTDOWN_SECONDS = 5

API_BINDINGS = (
    ("8080/tcp", "10.20.0.4", "8080"),
    ("8081/tcp", "10.20.0.4", "8081"),
)
WORKER_BINDINGS = (
    ("8082/tcp", "10.20.0.7", "8082"),
    ("8083/tcp", "10.20.0.7", "8083"),
)
HISTORICAL_CONTAINERS = {
    "coderzclub-api-before-domain",
    "coderzclub-api-before-cors",
    "coderzclub-api-replacing",
    "coderzclub-api-preflight",
}
QUEUE_NAME = re.compile(r"^[A-Za-z0-9._-]{1,128}$")
RUNTIME_KEYS = ("readonlyRootfs", "capDrop", "securityOpt", "stopTimeout")


def service_ports(role):
    if role == "worker":
        return WORKER_BINDINGS
    if role == "api":
        return API_BINDINGS
    raise ValueError(role)


def binding_errors(role, bindings):
    errors = []
    present = {
        (item.get("containerPort"), item.get("hostIp"), str(item.get("hostPort")))
        for item in bindings or []
    }
    for required in service_ports(role):
        if required not in present:
            errors.append(
                f"missing private binding {required[1]}:{required[2]} -> {required[0]}"
            )
    return errors


def ready_body(text):
    try:
        parsed = json.loads(text)
    except (TypeError, ValueError):
        return False
    return isinstance(parsed, dict) and parsed.get("status") == "UP"


def queue_names(env):
    values = env or {}
    submission = values.get("SUBMISSION_QUEUE_NAME") or "coderzclub.submissions"
    contest = values.get("CONTEST_RUN_QUEUE_NAME") or "coderzclub.contest-runs"
    errors = []
    if not QUEUE_NAME.fullmatch(submission) or not QUEUE_NAME.fullmatch(contest):
        errors.append("queue names must match ^[A-Za-z0-9._-]{1,128}$")
    if submission == contest:
        errors.append("submission and contest queues must be distinct")
    return submission, contest, errors


def consumer_count(log, queue):
    if not queue:
        return 0
    return (log or "").count(f"Started on queue '{queue}'")


def consumer_gaps(log, submission_queue, contest_queue):
    gaps = []
    if consumer_count(log, submission_queue) < 1:
        gaps.append("submission")
    if consumer_count(log, contest_queue) < 1:
        gaps.append("contest-run")
    return gaps


def handoff_blocked(previous_running):
    return bool(previous_running)


def removal_allowed(name, container_id, parked_id, original_name):
    if not name or name != original_name:
        return False
    if container_id and parked_id and container_id == parked_id:
        return False
    if "-before-" in name or name in HISTORICAL_CONTAINERS:
        return False
    return True


def mode_error(environ):
    dry = (environ or {}).get("DRY_RUN") == "yes"
    confirm = (environ or {}).get("CONFIRM_DEPLOY")
    if dry and confirm:
        return "DRY_RUN and CONFIRM_DEPLOY are mutually exclusive"
    if not dry and confirm != CONFIRM:
        return f"Refusing mutation without CONFIRM_DEPLOY={CONFIRM}"
    return None


def create_command(record, image):
    errors = []
    record = record or {}
    for key in RUNTIME_KEYS:
        if key not in record:
            errors.append(f"inspect is missing runtime field {key}; re-run inspect_host.py")
    if errors:
        return [], errors
    command = [
        "docker", "create",
        "--name", record.get("name") or "",
        "--restart", "unless-stopped",
        "--network", record.get("network") or "default",
        "--stop-timeout", str(record.get("stopTimeout") or DOCKER_STOP_SECONDS),
    ]
    if record.get("user"):
        command.extend(["--user", record["user"]])
    if record.get("readonlyRootfs"):
        command.append("--read-only")
    for cap in record.get("capDrop") or []:
        command.extend(["--cap-drop", cap])
    for opt in record.get("securityOpt") or []:
        command.extend(["--security-opt", opt])
    for binding in record.get("bindings") or []:
        port = str(binding["containerPort"]).split("/")[0]
        command.extend(["-p", f"{binding['hostIp']}:{binding['hostPort']}:{port}"])
    for mount in record.get("binds") or []:
        command.extend(["-v", mount])
    entrypoint = record.get("entrypoint") or []
    if entrypoint:
        command.extend(["--entrypoint", entrypoint[0]])
    command.append(image)
    command.extend(entrypoint[1:])
    command.extend(record.get("cmd") or [])
    return command, errors
