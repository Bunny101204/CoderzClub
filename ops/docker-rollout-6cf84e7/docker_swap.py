#!/usr/bin/env python3
"""Inventory-preserving Docker park/swap for coderzclub API or worker (6cf84e7).

Default mode is dry-run. Mutation requires CONFIRM_DEPLOY=<role>-6cf84e7 on the
matching host. Does not print secret env values. Importing this module does
not mutate Docker or GCP.
"""

from __future__ import annotations

import hashlib
import ipaddress
import json
import os
import re
import socket
import stat
import subprocess
import sys
import time
import urllib.error
import urllib.request

RELEASE_ID = "6cf84e7"
DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
RELEASE_ID_RE = re.compile(r"^[0-9a-f]{7,40}$")
IMAGE_ENTRYPOINT = ["java", "-jar", "/app/app.jar"]
DEFAULT_SHM = 64 * 1024 * 1024
STOP_FALLBACK = 30
DEFAULT_NEW_IMAGE_ID = (
    "sha256:b30086c75baa8cc68f14d5c183bf09d521ed0eb2363aedfed5ad2f5f29c6107f"
)
IMAGE_TAG = f"coderzclub-backend:{RELEASE_ID}"

ROLES = {
    "api": {
        "liveName": "coderzclub-api",
        "hostname": "coderzclub-api-1",
        "parkedPrefix": "coderzclub-api-before-",
        "confirm": f"api-{RELEASE_ID}",
        "profile": "api",
        "bindings": (
            ("8080/tcp", "10.20.0.4", "8080"),
            ("8081/tcp", "10.20.0.4", "8081"),
        ),
        "readyUrl": "http://127.0.0.1:8081/actuator/health/readiness",
        "healthUrl": "http://127.0.0.1:8081/actuator/health",
        "appUrl": "http://127.0.0.1:8080/api/problems/test",
        "stopSeconds": 30,
        "requireConcurrency": None,
    },
    "worker": {
        "liveName": "coderzclub-worker",
        "hostname": "coderzclub-worker-1",
        "parkedPrefix": "coderzclub-worker-before-",
        "confirm": f"worker-{RELEASE_ID}",
        "profile": "worker",
        "bindings": (
            ("8082/tcp", "10.20.0.7", "8082"),
            ("8083/tcp", "10.20.0.7", "8083"),
        ),
        "readyUrl": "http://127.0.0.1:8083/actuator/health/readiness",
        "healthUrl": "http://127.0.0.1:8083/actuator/health",
        "appUrl": None,
        "stopSeconds": 150,
        "requireConcurrency": "6",
    },
}

SAFE_VALUE_KEYS = (
    "APP_CORS_ALLOWED_ORIGINS",
    "APP_FRONTEND_URL",
    "SPRING_PROFILES_ACTIVE",
    "SERVER_PORT",
    "MANAGEMENT_SERVER_PORT",
    "SUBMISSION_QUEUE_NAME",
    "CONTEST_RUN_QUEUE_NAME",
    "WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY",
    "SUBMISSION_QUEUE_CONCURRENCY",
    "JUDGE0_API_MAX_CONCURRENCY",
    "JUDGE0_TIMEOUT_SECONDS",
    "WORKER_LEASE_DURATION_SECONDS",
    "JUDGE0_MAX_CPU_TIME_LIMIT_SECONDS",
    "JUDGE0_WALL_TIME_LIMIT_SECONDS",
    "SUBMISSION_MAX_EXECUTION_TIME_SECONDS",
)


def role_config(role: str) -> dict:
    if role not in ROLES:
        raise ValueError("ROLE must be api or worker")
    return ROLES[role]


def private_address(host_ip: str) -> bool:
    if not host_ip or host_ip in {"0.0.0.0", "::", "::0"}:
        return False
    try:
        return ipaddress.ip_address(host_ip).is_private
    except ValueError:
        return False


def _env_parts(env_list):
    keys = []
    safe = {}
    for item in env_list or []:
        if "=" not in item:
            continue
        key, value = item.split("=", 1)
        keys.append(key)
        if key in SAFE_VALUE_KEYS:
            safe[key] = value
    return sorted(keys), safe


def _duration(nanoseconds):
    if not nanoseconds:
        return None
    if nanoseconds % 1_000_000_000 == 0:
        return f"{nanoseconds // 1_000_000_000}s"
    if nanoseconds % 1_000_000 == 0:
        return f"{nanoseconds // 1_000_000}ms"
    return f"{nanoseconds}ns"


def _health(config):
    if not config:
        return None
    test = config.get("Test") or []
    if not test or test == ["NONE"]:
        return {"disabled": True}
    return {
        "disabled": False,
        "test": test,
        "interval": _duration(config.get("Interval") or 0),
        "timeout": _duration(config.get("Timeout") or 0),
        "retries": config.get("Retries") or 0,
        "startPeriod": _duration(config.get("StartPeriod") or 0),
    }


def unsupported_settings(host):
    host = host or {}
    found = []
    if host.get("Privileged"):
        found.append("privileged")
    if host.get("PublishAllPorts"):
        found.append("publishAllPorts")
    if host.get("AutoRemove"):
        found.append("autoRemove")
    if host.get("VolumesFrom"):
        found.append("volumesFrom")
    if host.get("Links"):
        found.append("links")
    return found


def safe_record(data):
    data = data or {}
    config = dict(data.get("Config") or {})
    env_list = list(config.pop("Env", []) or [])
    digest = hashlib.sha256("\n".join(sorted(env_list)).encode("utf-8")).hexdigest()
    keys, safe = _env_parts(env_list)
    del env_list
    host = data.get("HostConfig") or {}
    bindings = []
    for container_port, items in sorted((host.get("PortBindings") or {}).items()):
        for item in items or []:
            bindings.append({
                "containerPort": container_port,
                "hostIp": item.get("HostIp") or "",
                "hostPort": item.get("HostPort") or "",
            })
    mounts = []
    for mount in data.get("Mounts") or []:
        mounts.append({
            "type": mount.get("Type") or "",
            "source": mount.get("Source") or mount.get("Name") or "",
            "destination": mount.get("Destination") or "",
            "rw": bool(mount.get("RW", True)),
            "propagation": mount.get("Propagation") or "",
        })
    log = host.get("LogConfig") or {}
    restart = host.get("RestartPolicy") or {}
    state = data.get("State") or {}
    container_id = data.get("Id") or ""
    hostname = config.get("Hostname") or ""
    custom_hostname = hostname
    if container_id and hostname and container_id.startswith(hostname):
        custom_hostname = ""
    return {
        "name": (data.get("Name") or "").lstrip("/"),
        "id": container_id,
        "image": config.get("Image") or "",
        "imageId": data.get("Image") or "",
        "status": state.get("Status") or "",
        "running": bool(state.get("Running")),
        "restart": restart.get("Name") or "",
        "network": host.get("NetworkMode") or "",
        "user": config.get("User") or "",
        "entrypoint": config.get("Entrypoint") or [],
        "cmd": config.get("Cmd") or [],
        "workdir": config.get("WorkingDir") or "",
        "hostname": custom_hostname,
        "stopSignal": config.get("StopSignal") or "",
        "envKeys": keys,
        "envSha256": digest,
        "safeEnv": safe,
        "bindings": bindings,
        "mounts": mounts,
        "logDriver": (log.get("Type") or ""),
        "logOptions": dict(log.get("Config") or {}),
        "memory": host.get("Memory") or 0,
        "memoryReservation": host.get("MemoryReservation") or 0,
        "memorySwap": host.get("MemorySwap") or 0,
        "nanoCpus": host.get("NanoCpus") or 0,
        "cpuShares": host.get("CpuShares") or 0,
        "pidsLimit": host.get("PidsLimit"),
        "ulimits": [
            {
                "name": limit.get("Name") or "",
                "soft": limit.get("Soft"),
                "hard": limit.get("Hard"),
            }
            for limit in (host.get("Ulimits") or [])
        ],
        "readonlyRootfs": bool(host.get("ReadonlyRootfs")),
        "capAdd": list(host.get("CapAdd") or []),
        "capDrop": list(host.get("CapDrop") or []),
        "securityOpt": list(host.get("SecurityOpt") or []),
        "privileged": bool(host.get("Privileged")),
        "init": host.get("Init") is True,
        "shmSize": host.get("ShmSize") or 0,
        "tmpfs": dict(host.get("Tmpfs") or {}),
        "sysctls": dict(host.get("Sysctls") or {}),
        "dns": list(host.get("Dns") or []),
        "extraHosts": list(host.get("ExtraHosts") or []),
        "groupAdd": list(host.get("GroupAdd") or []),
        "devices": [
            {
                "host": device.get("PathOnHost") or "",
                "container": device.get("PathInContainer") or "",
                "permissions": device.get("CgroupPermissions") or "",
            }
            for device in (host.get("Devices") or [])
        ],
        "cgroupns": host.get("CgroupnsMode") or "",
        "healthcheck": _health(config.get("Healthcheck")),
        "stopTimeout": config.get("StopTimeout")
        if config.get("StopTimeout") is not None
        else (
            host.get("StopTimeout")
            if host.get("StopTimeout") is not None
            else STOP_FALLBACK
        ),
        "unsupported": unsupported_settings(host),
    }


def create_argv(record, image, env_file, live_name):
    record = record or {}
    timeout = record.get("stopTimeout")
    if timeout is None:
        timeout = STOP_FALLBACK
    argv = [
        "docker",
        "create",
        f"--name={record.get('name') or live_name}",
        "--restart=unless-stopped",
        f"--network={record.get('network') or 'bridge'}",
        f"--stop-timeout={timeout}",
    ]
    if record.get("user"):
        argv.append(f"--user={record['user']}")
    if record.get("readonlyRootfs"):
        argv.append("--read-only")
    for cap in record.get("capDrop") or []:
        argv.append(f"--cap-drop={cap}")
    for cap in record.get("capAdd") or []:
        argv.append(f"--cap-add={cap}")
    for opt in record.get("securityOpt") or []:
        argv.append(f"--security-opt={opt}")
    for binding in record.get("bindings") or []:
        port = str(binding["containerPort"]).split("/")[0]
        argv.append(f"--publish={binding['hostIp']}:{binding['hostPort']}:{port}")
    for mount in record.get("mounts") or []:
        parts = [f"type={mount.get('type') or 'bind'}", f"dst={mount.get('destination') or ''}"]
        if mount.get("source"):
            parts.append(f"src={mount['source']}")
        if not mount.get("rw", True):
            parts.append("readonly")
        if mount.get("propagation"):
            parts.append(f"bind-propagation={mount['propagation']}")
        argv.append("--mount=" + ",".join(parts))
    if record.get("logDriver"):
        argv.append(f"--log-driver={record['logDriver']}")
    for key, value in sorted((record.get("logOptions") or {}).items()):
        argv.append(f"--log-opt={key}={value}")
    if (record.get("memory") or 0) > 0:
        argv.append(f"--memory={record['memory']}")
    if (record.get("memoryReservation") or 0) > 0:
        argv.append(f"--memory-reservation={record['memoryReservation']}")
    swap = record.get("memorySwap") or 0
    if swap != 0:
        argv.append(f"--memory-swap={swap}")
    if (record.get("nanoCpus") or 0) > 0:
        argv.append(f"--cpus={record['nanoCpus'] / 1_000_000_000:.3f}".rstrip("0").rstrip("."))
    if (record.get("cpuShares") or 0) > 0:
        argv.append(f"--cpu-shares={record['cpuShares']}")
    if record.get("pidsLimit"):
        argv.append(f"--pids-limit={record['pidsLimit']}")
    for limit in record.get("ulimits") or []:
        argv.append(f"--ulimit={limit['name']}={limit['soft']}:{limit['hard']}")
    health = record.get("healthcheck")
    if health and health.get("disabled"):
        argv.append("--no-healthcheck")
    elif health:
        test = health.get("test") or []
        if test and test[0] in {"CMD-SHELL", "CMD"}:
            argv.append("--health-cmd=" + " ".join(test[1:]))
        elif test:
            argv.append("--health-cmd=" + " ".join(test))
        if health.get("interval"):
            argv.append(f"--health-interval={health['interval']}")
        if health.get("timeout"):
            argv.append(f"--health-timeout={health['timeout']}")
        if health.get("retries"):
            argv.append(f"--health-retries={health['retries']}")
        if health.get("startPeriod"):
            argv.append(f"--health-start-period={health['startPeriod']}")
    for destination, options in sorted((record.get("tmpfs") or {}).items()):
        suffix = f":{options}" if options else ""
        argv.append(f"--tmpfs={destination}{suffix}")
    for key, value in sorted((record.get("sysctls") or {}).items()):
        argv.append(f"--sysctl={key}={value}")
    for address in record.get("dns") or []:
        argv.append(f"--dns={address}")
    for host_entry in record.get("extraHosts") or []:
        argv.append(f"--add-host={host_entry}")
    for group in record.get("groupAdd") or []:
        argv.append(f"--group-add={group}")
    for device in record.get("devices") or []:
        argv.append(f"--device={device['host']}:{device['container']}:{device['permissions']}")
    shm = record.get("shmSize") or 0
    if shm and shm != DEFAULT_SHM:
        argv.append(f"--shm-size={shm}")
    if record.get("init"):
        argv.append("--init")
    if record.get("hostname"):
        argv.append(f"--hostname={record['hostname']}")
    if record.get("workdir"):
        argv.append(f"--workdir={record['workdir']}")
    if record.get("cgroupns") == "host":
        argv.append("--cgroupns=host")
    stop_signal = record.get("stopSignal") or ""
    if stop_signal and stop_signal not in {"SIGTERM", "15"}:
        argv.append(f"--stop-signal={stop_signal}")
    entrypoint = record.get("entrypoint") or []
    if entrypoint and entrypoint != IMAGE_ENTRYPOINT:
        argv.append(f"--entrypoint={entrypoint[0]}")
    argv.append(f"--env-file={env_file}")
    argv.append(image)
    if entrypoint and entrypoint != IMAGE_ENTRYPOINT:
        argv.extend(entrypoint[1:])
    argv.extend(record.get("cmd") or [])
    return argv


def preservation_gaps(record, argv):
    text = "\n".join(argv or [])
    gaps = []

    def need(label, present, token):
        if present and token not in text:
            gaps.append(label)

    need("user", record.get("user"), f"--user={record.get('user')}")
    need("read-only", record.get("readonlyRootfs"), "--read-only")
    for cap in record.get("capDrop") or []:
        need(f"cap-drop {cap}", True, f"--cap-drop={cap}")
    for binding in record.get("bindings") or []:
        port = str(binding["containerPort"]).split("/")[0]
        token = f"--publish={binding['hostIp']}:{binding['hostPort']}:{port}"
        need(token, True, token)
    for mount in record.get("mounts") or []:
        need(mount.get("destination") or "mount", True, f"dst={mount.get('destination')}")
    need("network", True, f"--network={record.get('network') or 'bridge'}")
    need("restart", True, "--restart=unless-stopped")
    need("stop timeout", True, f"--stop-timeout={record.get('stopTimeout')}")
    return gaps


def parse_release(environ, role, require_new_digest):
    cfg = role_config(role)
    environ = environ or {}
    errors = []
    release_id = (environ.get("RELEASE_ID") or RELEASE_ID).strip().lower()
    if release_id != RELEASE_ID or not RELEASE_ID_RE.fullmatch(release_id):
        errors.append(f"RELEASE_ID must be {RELEASE_ID}")
    parked = f"{cfg['parkedPrefix']}{RELEASE_ID}"
    image = (environ.get("NEW_IMAGE") or IMAGE_TAG).strip()
    if image != IMAGE_TAG:
        errors.append(f"NEW_IMAGE must be {IMAGE_TAG}")
    current = (environ.get("EXPECTED_CURRENT_IMAGE_ID") or "").strip()
    if not DIGEST_RE.fullmatch(current):
        errors.append("EXPECTED_CURRENT_IMAGE_ID must be a full sha256 digest")
        current = ""
    new_digest = (environ.get("EXPECTED_NEW_IMAGE_ID") or DEFAULT_NEW_IMAGE_ID).strip()
    if new_digest and not DIGEST_RE.fullmatch(new_digest):
        errors.append("EXPECTED_NEW_IMAGE_ID must be a sha256 digest")
        new_digest = ""
    elif require_new_digest and not DIGEST_RE.fullmatch(new_digest):
        errors.append("EXPECTED_NEW_IMAGE_ID must be a sha256 digest")
    if current and new_digest and current == new_digest:
        errors.append("new image digest must differ from the current image digest")
    return {
        "releaseId": RELEASE_ID,
        "role": role,
        "image": image,
        "currentImageId": current,
        "newImageId": new_digest,
        "confirm": cfg["confirm"],
        "parkedName": parked,
        "envFilename": f"cz-{role}-{RELEASE_ID}.env",
        "directoryName": f"cz-{role}-{RELEASE_ID}",
        "errors": errors,
    }


def _binding_present(record, container_port, host_ip, host_port):
    for binding in record.get("bindings") or []:
        if (
            binding.get("containerPort") == container_port
            and binding.get("hostIp") == host_ip
            and str(binding.get("hostPort")) == host_port
        ):
            return True
    return False


def preflight(record, existing_names, loaded_new_image_id, release, role):
    cfg = role_config(role)
    record = record or {}
    release = release or {}
    errors = list(release.get("errors") or [])
    live = cfg["liveName"]
    parked = release.get("parkedName") or ""
    names = set(existing_names or [])
    if live not in names:
        errors.append(f"{live} is not present")
    if parked in names:
        errors.append(f"{parked} already exists")
    if record.get("name") != live:
        errors.append(f"inspect is not the live {role} container")
    expected_current = release.get("currentImageId") or ""
    if not expected_current or record.get("imageId") != expected_current:
        errors.append("live image is not the expected digest; refusing to guess a rollback target")
    if record.get("status") != "running":
        errors.append(f"live {role} container is not running")
    if record.get("restart") != "unless-stopped":
        errors.append("restart policy is not unless-stopped")
    if record.get("network") not in {"bridge", "host"} and not str(record.get("network") or "").startswith("container:"):
        # allow custom bridge network names; only reject empty
        if not record.get("network"):
            errors.append("network mode is empty")
    if record.get("privileged") or "privileged" in (record.get("unsupported") or []):
        errors.append("container is privileged")
    for item in record.get("unsupported") or []:
        if item != "privileged":
            errors.append(f"unsupported runtime setting {item}")
    for binding in record.get("bindings") or []:
        if not private_address(binding.get("hostIp")):
            errors.append("a published port is not bound to a private address")
    for container_port, host_ip, host_port in cfg["bindings"]:
        if not _binding_present(record, container_port, host_ip, host_port):
            errors.append(f"missing {host_ip}:{host_port}")
    safe = record.get("safeEnv") or {}
    if safe.get("SPRING_PROFILES_ACTIVE") != cfg["profile"]:
        errors.append(f"SPRING_PROFILES_ACTIVE is not {cfg['profile']}")
    required_conc = cfg.get("requireConcurrency")
    if required_conc is not None:
        actual = safe.get("WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY")
        if actual != required_conc:
            errors.append(
                f"WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY must remain {required_conc} (saw {actual!r})"
            )
    expected_new = release.get("newImageId") or ""
    if not expected_new or loaded_new_image_id != expected_new:
        errors.append("new image is not loaded at the expected digest")
    return errors


def ready_body(text):
    try:
        parsed = json.loads(text or "")
    except (TypeError, ValueError):
        return False
    return isinstance(parsed, dict) and parsed.get("status") == "UP"


def redact(text, secrets):
    redacted = text or ""
    for secret in secrets or []:
        if secret and len(secret) >= 8 and secret in redacted:
            redacted = redacted.replace(secret, "[redacted]")
    lowered = redacted.lower()
    if any(marker in lowered for marker in ("mongodb://", "mongodb+srv://", "password=", "bearer ")):
        return "command failed; secret-bearing output was redacted"
    return redacted


def write_restricted_env(directory, contents, filename):
    if not filename or "/" in filename or "\\" in filename or filename.startswith("."):
        raise ValueError("environment filename is invalid")
    path = os.path.join(directory, filename)
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    try:
        os.write(fd, contents.encode("utf-8"))
    finally:
        os.close(fd)
    os.chmod(path, 0o600)
    return path


def remove_env(path):
    if not path or not os.path.exists(path):
        return True
    try:
        os.chmod(path, stat.S_IWRITE | stat.S_IREAD)
        os.remove(path)
        return True
    except OSError:
        return False


class SwapState:
    def __init__(self, parked_id, parked_name):
        self.parked_id = parked_id
        self.parked_name = parked_name
        self.renamed = False
        self.created = False


def restore(docker, state, live_name):
    try:
        if state.created and docker.exists(live_name):
            current_id = docker.container_id(live_name)
            if current_id and current_id != state.parked_id:
                docker.stop(live_name)
                docker.remove(live_name)
        if state.renamed and docker.exists(state.parked_name):
            if not docker.running(state.parked_name):
                docker.start(state.parked_name)
            if not docker.exists(live_name):
                docker.rename(state.parked_name, live_name)
        return (
            docker.exists(live_name)
            and docker.running(live_name)
            and docker.container_id(live_name) == state.parked_id
        )
    except Exception:
        return False


def _http_get(url):
    request = urllib.request.Request(url, headers={"Accept": "application/json, text/plain"})
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            return response.status, response.read(65536).decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        return error.code, error.read(65536).decode("utf-8", "replace")
    except Exception:
        return None, ""


class RoleProbe:
    def __init__(self, role):
        self.cfg = role_config(role)

    def healthy(self):
        ready_status, ready_text = _http_get(self.cfg["readyUrl"])
        health_status, health_text = _http_get(self.cfg["healthUrl"])
        if ready_status != 200 or not ready_body(ready_text):
            return False
        if health_status != 200 or not ready_body(health_text):
            return False
        if self.cfg["appUrl"]:
            app_status, app_text = _http_get(self.cfg["appUrl"])
            if app_status != 200 or "Problems endpoint is working" not in (app_text or ""):
                return False
        return True


def _wait_ready(probe, timeout=180):
    deadline = time.monotonic() + timeout
    while True:
        if probe.healthy():
            return True
        if time.monotonic() >= deadline:
            return False
        time.sleep(3)


class DockerCli:
    def __init__(self, live_name, stop_seconds=STOP_FALLBACK):
        self.live_name = live_name
        self.stop_seconds = stop_seconds
        self.removed = []

    def _run(self, args, check=True):
        completed = subprocess.run(["docker", *args], check=False, text=True, capture_output=True)
        if check and completed.returncode != 0:
            detail = redact((completed.stderr or completed.stdout or "").strip(), [])
            raise RuntimeError(detail or "docker command failed")
        return completed

    def exists(self, name):
        return self._run(["container", "inspect", name], check=False).returncode == 0

    def running(self, name):
        completed = self._run(["inspect", "--format", "{{.State.Running}}", name], check=False)
        return completed.returncode == 0 and completed.stdout.strip() == "true"

    def container_id(self, name):
        completed = self._run(["inspect", "--format", "{{.Id}}", name], check=False)
        return completed.stdout.strip() if completed.returncode == 0 else None

    def image_id(self, name):
        completed = self._run(["inspect", "--format", "{{.Image}}", name], check=False)
        return completed.stdout.strip() if completed.returncode == 0 else None

    def rename(self, source, target):
        self._run(["rename", source, target])

    def stop(self, name):
        self._run(["stop", "--time", str(self.stop_seconds), name])

    def start(self, name):
        self._run(["start", name])

    def create(self, argv):
        self._run(argv[1:] if argv and argv[0] == "docker" else argv)

    def remove(self, name):
        if name != self.live_name or "-before-" in name:
            raise RuntimeError("refusing to remove a protected container")
        self.removed.append(name)
        self._run(["rm", name])


def _loaded_image_id(reference):
    completed = subprocess.run(
        ["docker", "image", "inspect", "--format", "{{.Id}}", reference],
        check=False,
        text=True,
        capture_output=True,
    )
    if completed.returncode != 0:
        return None
    return completed.stdout.strip() or None


def _container_names():
    completed = subprocess.run(
        ["docker", "ps", "-a", "--format", "{{.Names}}"],
        check=False,
        text=True,
        capture_output=True,
    )
    if completed.returncode != 0:
        raise RuntimeError("container name listing failed")
    return [line.strip() for line in completed.stdout.splitlines() if line.strip()]


def execute_swap(record, existing_names, docker, probe, env_text, directory, loaded_new_image_id, release, role):
    cfg = role_config(role)
    live = cfg["liveName"]
    errors = preflight(record, existing_names, loaded_new_image_id, release, role)
    env_filename = release.get("envFilename") or ""
    argv = create_argv(record, release.get("newImageId") or "", os.path.join(directory, env_filename), live)
    errors.extend(preservation_gaps(record, argv))
    result = {
        "ok": False,
        "healthy": False,
        "restored": False,
        "restoredHealthy": False,
        "mutated": False,
        "envRemoved": True,
        "errors": errors,
        "argv": argv,
        "envSha256": record.get("envSha256"),
    }
    if errors:
        return result
    state = SwapState(record.get("id"), release.get("parkedName") or "")
    env_path = None
    secrets = [env_text]
    parked_name = release.get("parkedName") or ""
    try:
        env_path = write_restricted_env(directory, env_text, env_filename)
        docker.rename(live, parked_name)
        state.renamed = True
        result["mutated"] = True
        docker.stop(parked_name)
        if docker.running(parked_name):
            raise RuntimeError("previous container is still running")
        docker.create(argv)
        state.created = True
        if docker.image_id(live) != release.get("newImageId"):
            raise RuntimeError("created container image differs from the verified image")
        docker.start(live)
        if not _wait_ready(probe, timeout=180):
            raise RuntimeError("readiness/health timeout")
        result["healthy"] = True
        result["ok"] = True
        return result
    except Exception as exc:
        result["restored"] = restore(docker, state, live)
        result["ok"] = False
        result["errors"] = [redact(str(exc), secrets)]
        if result["restored"]:
            result["restoredHealthy"] = _wait_ready(probe, timeout=180)
            if not result["restoredHealthy"]:
                result["errors"].append("previous container restored but readiness failed")
        return result
    finally:
        if env_path:
            result["envRemoved"] = remove_env(env_path)


def rollback(docker, new_image_id, parked_name, expected_old_image_id, live_name):
    errors = []
    if not parked_name or "-before-" not in parked_name:
        return {"ok": False, "errors": ["parked name is not safe"]}
    if not DIGEST_RE.fullmatch(new_image_id or ""):
        return {"ok": False, "errors": ["new image digest is missing"]}
    if not docker.exists(parked_name):
        return {"ok": False, "errors": [f"{parked_name} is not present"]}
    if not DIGEST_RE.fullmatch(expected_old_image_id or "") or expected_old_image_id == new_image_id:
        return {"ok": False, "errors": ["expected previous image digest is invalid"]}
    if docker.image_id(parked_name) != expected_old_image_id:
        return {"ok": False, "errors": ["parked container is not the expected previous image"]}
    removed_live = False
    if docker.exists(live_name):
        if docker.image_id(live_name) != new_image_id:
            return {"ok": False, "errors": ["live container is not the new image; rollback stopped"]}
        if docker.container_id(live_name) == docker.container_id(parked_name):
            return {"ok": False, "errors": ["refusing to remove the parked container"]}
        docker.stop(live_name)
        docker.remove(live_name)
        removed_live = True
    if not docker.running(parked_name):
        docker.start(parked_name)
    docker.rename(parked_name, live_name)
    return {
        "ok": docker.running(live_name) and docker.image_id(live_name) == expected_old_image_id,
        "removedLive": removed_live,
        "errors": errors,
    }


def dry_run_summary(record, names, release, loaded_image_id, role):
    summary = dict(record)
    # Strip nothing extra; safe_record already redacts secret values.
    summary["containerNames"] = list(names or [])
    summary["parkedNameFree"] = bool(release.get("parkedName")) and release.get("parkedName") not in set(names or [])
    summary["newImageLoaded"] = loaded_image_id
    summary["releaseId"] = release.get("releaseId") or ""
    summary["role"] = role
    summary["image"] = release.get("image") or ""
    summary["expectedCurrentImageId"] = release.get("currentImageId") or ""
    summary["expectedNewImageId"] = release.get("newImageId") or ""
    live = role_config(role)["liveName"]
    summary["preservationGaps"] = preservation_gaps(
        record,
        create_argv(record, release.get("newImageId") or "", "/tmp/" + (release.get("envFilename") or "unset"), live),
    )
    summary["preflightErrors"] = preflight(record, names, loaded_image_id, release, role)
    summary["mutation"] = "none"
    return summary


def deployment_mode(environ, hostname, role):
    cfg = role_config(role)
    environ = environ or {}
    dry = environ.get("DRY_RUN", "yes") == "yes"
    confirm = environ.get("CONFIRM_DEPLOY")
    expected_host = environ.get("EXPECTED_HOSTNAME") or cfg["hostname"]
    if dry and confirm:
        return "refuse", "DRY_RUN and CONFIRM_DEPLOY are mutually exclusive"
    if hostname != cfg["hostname"] or expected_host != cfg["hostname"]:
        return "refuse", f"hostname is not {cfg['hostname']}"
    if environ.get("ROLLBACK") == "yes":
        parsed = parse_release(environ, role, True)
        if parsed["errors"]:
            return "refuse", parsed["errors"][0]
        if confirm != parsed["confirm"]:
            return "refuse", "confirmation token does not match this release"
        return "rollback", ""
    if dry:
        parsed = parse_release(environ, role, False)
        if parsed["errors"]:
            return "refuse", parsed["errors"][0]
        return "dry-run", ""
    parsed = parse_release(environ, role, True)
    if parsed["errors"]:
        return "refuse", parsed["errors"][0]
    if confirm != parsed["confirm"]:
        return "refuse", "confirmation token does not match this release"
    return "swap", ""


def main(environ=None, hostname=None):
    environ = os.environ if environ is None else environ
    hostname = socket.gethostname() if hostname is None else hostname
    role = (environ.get("ROLE") or "").strip().lower()
    if role not in ROLES:
        print("ROLE must be api or worker", file=sys.stderr)
        return 2
    mode, message = deployment_mode(environ, hostname, role)
    if mode == "refuse":
        print(message, file=sys.stderr)
        return 2
    cfg = role_config(role)
    live = cfg["liveName"]
    if mode == "rollback":
        parsed = parse_release(environ, role, True)
        docker = DockerCli(live, stop_seconds=cfg["stopSeconds"])
        outcome = rollback(docker, parsed["newImageId"], parsed["parkedName"], parsed["currentImageId"], live)
        if outcome.get("ok") and not _wait_ready(RoleProbe(role), timeout=180):
            outcome = {"ok": False, "errors": ["previous container restored but readiness failed"]}
        print("rolledBack=" + ("yes" if outcome.get("ok") else "no"))
        if outcome.get("errors"):
            print("errors=" + "; ".join(outcome["errors"]), file=sys.stderr)
        return 0 if outcome.get("ok") else 4
    parsed = parse_release(environ, role, mode != "dry-run")
    if parsed["errors"]:
        print(parsed["errors"][0], file=sys.stderr)
        return 2
    completed = subprocess.run(["docker", "inspect", live], check=False, text=True, capture_output=True)
    if completed.returncode != 0:
        print("docker inspect failed", file=sys.stderr)
        return 3
    data = json.loads(completed.stdout)[0]
    env_text = "\n".join((data.get("Config") or {}).get("Env") or []) + "\n"
    record = safe_record(data)
    del data
    names = _container_names()
    loaded = _loaded_image_id(parsed["image"]) if parsed["image"] else None
    if mode == "dry-run":
        summary = dry_run_summary(record, names, parsed, loaded, role)
        print(json.dumps(summary, indent=2, sort_keys=True))
        print("mutation=none")
        return 2 if summary["preflightErrors"] or summary["preservationGaps"] else 0
    directory = os.path.join("/tmp", parsed["directoryName"])
    os.makedirs(directory, mode=0o700, exist_ok=False)
    os.chmod(directory, 0o700)
    docker = DockerCli(live, stop_seconds=record.get("stopTimeout") or cfg["stopSeconds"])
    try:
        result = execute_swap(
            record,
            names,
            docker,
            RoleProbe(role),
            env_text,
            directory,
            loaded,
            parsed,
            role,
        )
    finally:
        remove_env(os.path.join(directory, parsed["envFilename"]))
        try:
            os.rmdir(directory)
        except OSError:
            pass
        env_text = ""
    print("ok=" + ("yes" if result.get("ok") else "no"))
    print("healthy=" + ("yes" if result.get("healthy") else "no"))
    print("restored=" + ("yes" if result.get("restored") else "no"))
    print("restoredHealthy=" + ("yes" if result.get("restoredHealthy") else "no"))
    print("envRemoved=" + ("yes" if result.get("envRemoved") else "no"))
    print("envSha256=" + str(result.get("envSha256") or ""))
    if result.get("errors"):
        print("errors=" + "; ".join(result["errors"]), file=sys.stderr)
    return 0 if result.get("ok") and result.get("envRemoved") else 4


if __name__ == "__main__":
    sys.exit(main())
