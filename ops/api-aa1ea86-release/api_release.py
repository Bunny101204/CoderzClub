"""Safety checks for the aa1ea86 API container swap.

These functions do not talk to Docker, GCP, or the network. A fake Docker
object is enough to exercise failure recovery. Production execution has to
supply CONFIRM_DEPLOY=api-aa1ea866bcd on coderzclub-api-1 and is not invoked
by importing this module.
"""

import hashlib
import ipaddress
import json
import os
import socket
import stat
import subprocess
import sys
import urllib.error
import urllib.request

LIVE_NAME = "coderzclub-api"
PARKED_NAME = "coderzclub-api-before-aa1ea86"
NEW_IMAGE = "coderzclub-backend:api-aa1ea866bcd"
CURRENT_IMAGE_ID = "sha256:5fdb39d431e09d729a6b8216b67368d9e727163d84d40eec056d854aa553f6e0"
CONFIRM = "api-aa1ea866bcd"
EXPECTED_HOSTNAME = "coderzclub-api-1"
IMAGE_ENTRYPOINT = ["java", "-jar", "/app/app.jar"]
REQUIRED_CORS = "https://coderzclub.site,https://coderzclub.coderzclub1.workers.dev"
REQUIRED_FRONTEND = "https://coderzclub.site"
ENV_FILENAME = "cz-api-aa1ea86.env"
DEFAULT_SHM = 64 * 1024 * 1024
STOP_FALLBACK = 30
KEEP_NAMES = {
    "coderzclub-api-before-domain",
    "coderzclub-api-before-cors",
    "coderzclub-api-replacing",
    "coderzclub-api-preflight",
    "coderzclub-api-before-batch-mgmt",
}
SAFE_VALUE_KEYS = (
    "APP_CORS_ALLOWED_ORIGINS",
    "APP_FRONTEND_URL",
    "SPRING_PROFILES_ACTIVE",
    "SERVER_PORT",
    "MANAGEMENT_SERVER_PORT",
    "SUBMISSION_QUEUE_NAME",
    "CONTEST_RUN_QUEUE_NAME",
)
FORBIDDEN_RESPONSE_KEYS = {
    "hiddenTestCases",
    "hidden_test_cases",
    "email",
    "password",
    "solution",
    "testCases",
    "token",
}
PAGE_KEYS = ("currentPage", "totalPages", "totalItems")


def private_address(host_ip):
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
    if host.get("BlkioWeight"):
        found.append("blkioWeight")
    if any(host.get(key) for key in (
        "BlkioWeightDevice", "BlkioDeviceReadBps", "BlkioDeviceWriteBps",
        "BlkioDeviceReadIOps", "BlkioDeviceWriteIOps",
    )):
        found.append("blkioDevice")
    if host.get("DeviceRequests"):
        found.append("deviceRequests")
    if host.get("StorageOpt"):
        found.append("storageOpt")
    if host.get("PidMode"):
        found.append("pidMode")
    if host.get("UsernsMode"):
        found.append("usernsMode")
    if host.get("UTSMode"):
        found.append("utsMode")
    ipc = host.get("IpcMode") or ""
    if ipc and ipc not in {"private", "shareable"}:
        found.append("ipcMode")
    cgroup = host.get("CgroupnsMode") or ""
    if cgroup and cgroup not in {"private", "host"}:
        found.append("cgroupnsMode")
    runtime = host.get("Runtime") or ""
    if runtime and runtime not in {"runc", "io.containerd.runc.v2"}:
        found.append("runtime")
    if "MaskedPaths" in host and host.get("MaskedPaths") == []:
        found.append("maskedPaths")
    return found


def safe_record(data):
    """Container identity and runtime settings, with secret values removed."""
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
    devices = []
    for device in host.get("Devices") or []:
        devices.append({
            "host": device.get("PathOnHost") or "",
            "container": device.get("PathInContainer") or "",
            "permissions": device.get("CgroupPermissions") or "",
        })
    ulimits = []
    for limit in host.get("Ulimits") or []:
        ulimits.append({
            "name": limit.get("Name") or "",
            "soft": limit.get("Soft"),
            "hard": limit.get("Hard"),
        })
    return {
        "name": (data.get("Name") or "").lstrip("/"),
        "id": container_id,
        "image": config.get("Image") or "",
        "imageId": data.get("Image") or "",
        "status": state.get("Status") or "",
        "running": bool(state.get("Running")),
        "restart": restart.get("Name") or "",
        "restartMaximumRetry": restart.get("MaximumRetryCount") or 0,
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
        "ulimits": ulimits,
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
        "devices": devices,
        "cgroupns": host.get("CgroupnsMode") or "",
        "healthcheck": _health(config.get("Healthcheck")),
        "stopTimeout": host.get("StopTimeout") if host.get("StopTimeout") is not None else STOP_FALLBACK,
        "labelKeys": sorted((config.get("Labels") or {})),
        "unsupported": unsupported_settings(host),
    }


def public_summary(record):
    return dict(record)


def name_conflicts(existing_names):
    existing = set(existing_names or [])
    errors = []
    if PARKED_NAME in existing:
        errors.append(f"{PARKED_NAME} already exists")
    if LIVE_NAME not in existing:
        errors.append(f"{LIVE_NAME} is not present")
    return errors


def _binding_present(record, container_port, host_ip, host_port):
    for binding in record.get("bindings") or []:
        if (
            binding.get("containerPort") == container_port
            and binding.get("hostIp") == host_ip
            and str(binding.get("hostPort")) == host_port
        ):
            return True
    return False


def preflight(record, existing_names, loaded_new_image_id, expected_new_image_id):
    record = record or {}
    errors = list(name_conflicts(existing_names))
    if record.get("name") != LIVE_NAME:
        errors.append("inspect is not the live API container")
    if record.get("imageId") != CURRENT_IMAGE_ID:
        errors.append("live image is not the October API digest; refusing to guess a rollback target")
    if record.get("status") != "running":
        errors.append("live API container is not running")
    if record.get("restart") != "unless-stopped":
        errors.append("restart policy is not unless-stopped")
    if record.get("network") != "bridge":
        errors.append("network is not bridge")
    if record.get("privileged") or "privileged" in (record.get("unsupported") or []):
        errors.append("container is privileged")
    for item in record.get("unsupported") or []:
        if item != "privileged":
            errors.append(f"unsupported runtime setting {item}")
    for binding in record.get("bindings") or []:
        if not private_address(binding.get("hostIp")):
            errors.append("a published port is not bound to a private address")
    if not _binding_present(record, "8080/tcp", "10.20.0.4", "8080"):
        errors.append("missing 10.20.0.4:8080")
    if not _binding_present(record, "8081/tcp", "10.20.0.4", "8081"):
        errors.append("missing 10.20.0.4:8081")
    safe = record.get("safeEnv") or {}
    if safe.get("SPRING_PROFILES_ACTIVE") != "api":
        errors.append("SPRING_PROFILES_ACTIVE is not api")
    if safe.get("APP_CORS_ALLOWED_ORIGINS") != REQUIRED_CORS:
        errors.append("CORS origins differ from the approved pair")
    if safe.get("APP_FRONTEND_URL") != REQUIRED_FRONTEND:
        errors.append("frontend URL is not https://coderzclub.site")
    if not expected_new_image_id or loaded_new_image_id != expected_new_image_id:
        errors.append("new image is not loaded at the expected digest")
    if record.get("labelKeys"):
        errors.append("container labels need review before they can be preserved")
    return errors


def create_argv(record, image, env_file):
    record = record or {}
    timeout = record.get("stopTimeout")
    if timeout is None:
        timeout = STOP_FALLBACK
    argv = [
        "docker", "create",
        f"--name={record.get('name') or LIVE_NAME}",
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
    if swap > 0:
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
        if test and test[0] == "CMD-SHELL":
            argv.append("--health-cmd=" + " ".join(test[1:]))
        elif test and test[0] == "CMD":
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
    """Runtime settings that are present on the container and absent from argv."""
    text = "\n".join(argv or [])
    gaps = []

    def need(label, present, token):
        if present and token not in text:
            gaps.append(label)

    need("user", record.get("user"), f"--user={record.get('user')}")
    need("read-only", record.get("readonlyRootfs"), "--read-only")
    for cap in record.get("capDrop") or []:
        need(f"cap-drop {cap}", True, f"--cap-drop={cap}")
    for cap in record.get("capAdd") or []:
        need(f"cap-add {cap}", True, f"--cap-add={cap}")
    for opt in record.get("securityOpt") or []:
        need(f"security-opt {opt}", True, f"--security-opt={opt}")
    for binding in record.get("bindings") or []:
        port = str(binding["containerPort"]).split("/")[0]
        token = f"--publish={binding['hostIp']}:{binding['hostPort']}:{port}"
        need(token, True, token)
    for mount in record.get("mounts") or []:
        need(mount.get("destination") or "mount", True, f"dst={mount.get('destination')}")
    need("log driver", record.get("logDriver"), f"--log-driver={record.get('logDriver')}")
    for key, value in (record.get("logOptions") or {}).items():
        need(f"log option {key}", True, f"--log-opt={key}={value}")
    need("memory", (record.get("memory") or 0) > 0, f"--memory={record.get('memory')}")
    need("pids", record.get("pidsLimit"), f"--pids-limit={record.get('pidsLimit')}")
    for limit in record.get("ulimits") or []:
        need(limit["name"], True, f"--ulimit={limit['name']}={limit['soft']}:{limit['hard']}")
    health = record.get("healthcheck")
    if health and health.get("disabled"):
        need("healthcheck disabled", True, "--no-healthcheck")
    elif health:
        need("health interval", health.get("interval"), f"--health-interval={health.get('interval')}")
    for destination in record.get("tmpfs") or {}:
        need(f"tmpfs {destination}", True, f"--tmpfs={destination}")
    need("network", True, f"--network={record.get('network') or 'bridge'}")
    need("restart", True, "--restart=unless-stopped")
    if record.get("entrypoint") and record.get("entrypoint") != IMAGE_ENTRYPOINT:
        need("entrypoint", True, f"--entrypoint={record['entrypoint'][0]}")
    need("init", record.get("init"), "--init")
    shm = record.get("shmSize") or 0
    need("shm", shm not in {0, DEFAULT_SHM}, f"--shm-size={shm}")
    for key, value in (record.get("sysctls") or {}).items():
        need(key, True, f"--sysctl={key}={value}")
    for address in record.get("dns") or []:
        need(address, True, f"--dns={address}")
    for device in record.get("devices") or []:
        token = f"--device={device['host']}:{device['container']}:{device['permissions']}"
        need(token, True, token)
    need("stop timeout", True, f"--stop-timeout={record.get('stopTimeout')}")
    return gaps


def previous_plan_omissions(record):
    """Settings the October create_command helper does not copy."""
    omitted = []
    if record.get("mounts"):
        omitted.append("mounts")
    if record.get("logDriver") or record.get("logOptions"):
        omitted.append("logging")
    if (record.get("memory") or 0) > 0 or record.get("nanoCpus") or record.get("pidsLimit") or record.get("ulimits"):
        omitted.append("resource limits")
    if record.get("capAdd"):
        omitted.append("added capabilities")
    if record.get("healthcheck"):
        omitted.append("healthcheck")
    if record.get("tmpfs") or record.get("sysctls") or record.get("devices") or record.get("init"):
        omitted.append("tmpfs, sysctls, devices, or init")
    if record.get("dns") or record.get("extraHosts") or record.get("groupAdd"):
        omitted.append("dns, extra hosts, or groups")
    if (record.get("shmSize") or 0) not in {0, DEFAULT_SHM}:
        omitted.append("shm size")
    return omitted


def ready_body(text):
    try:
        parsed = json.loads(text or "")
    except (TypeError, ValueError):
        return False
    return isinstance(parsed, dict) and parsed.get("status") == "UP"


def service_ready(ready_status, ready_text, app_status, app_text):
    return (
        ready_status == 200
        and ready_body(ready_text)
        and app_status == 200
        and "Problems endpoint is working" in (app_text or "")
    )


def _forbidden(value, found):
    if isinstance(value, dict):
        for key, child in value.items():
            if key in FORBIDDEN_RESPONSE_KEYS:
                found.add(key)
            _forbidden(child, found)
    elif isinstance(value, list):
        for child in value:
            _forbidden(child, found)
    return found


def enrollment_errors(status, body):
    if status != 200 or not isinstance(body, dict):
        return ["GET /api/my/batches did not return a JSON object"]
    errors = []
    if not isinstance(body.get("batches"), list):
        errors.append("batches is not a list")
    enrolled = body.get("enrolled")
    if not isinstance(enrolled, dict):
        errors.append("enrolled field is missing")
        return errors
    if not isinstance(enrolled.get("items"), list):
        errors.append("enrolled.items is not a list")
    for key in PAGE_KEYS:
        if key not in enrolled:
            errors.append(f"enrolled.{key} is missing")
    forbidden = _forbidden(body, set())
    if forbidden:
        errors.append("response contains forbidden keys")
    return errors


def practice_errors(status, body, batch_id):
    if status != 200 or not isinstance(body, dict):
        return ["practice endpoint did not return a JSON object"]
    errors = []
    if body.get("batchId") != batch_id:
        errors.append("practice batchId does not match the enrolled batch")
    if not isinstance(body.get("problems"), list):
        errors.append("problems is not a list")
    for key in PAGE_KEYS:
        if key not in body:
            errors.append(f"practice.{key} is missing")
    if _forbidden(body, set()):
        errors.append("practice response contains forbidden keys")
    return errors


def bunny_acceptance(list_status, list_body, practice_status, practice_body, expected_name=""):
    errors = enrollment_errors(list_status, list_body)
    if errors:
        return errors
    items = list_body["enrolled"]["items"]
    if list_body["enrolled"].get("totalItems", 0) < 1 or not items:
        return ["bunny12 enrollment page is empty"]
    first = items[0]
    if not isinstance(first, dict) or not first.get("batchId"):
        return ["enrolled item has no batchId"]
    if expected_name and first.get("name") != expected_name:
        return ["enrolled batch name does not match the expected batch"]
    errors.extend(practice_errors(practice_status, practice_body, first["batchId"]))
    return errors


def redact(text, secrets):
    redacted = text or ""
    for secret in secrets or []:
        if secret and len(secret) >= 8 and secret in redacted:
            redacted = redacted.replace(secret, "[redacted]")
    lowered = redacted.lower()
    if "mongodb://" in lowered or "password=" in lowered or "bearer " in lowered:
        return "command failed; secret-bearing output was redacted"
    return redacted


def write_restricted_env(directory, contents, chmod=os.chmod):
    path = os.path.join(directory, ENV_FILENAME)
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    try:
        os.write(fd, contents.encode("utf-8"))
    finally:
        os.close(fd)
    chmod(path, 0o600)
    return path


def remove_env(path, chmod=os.chmod, remove=os.remove, exists=os.path.exists):
    if not path or not exists(path):
        return True
    try:
        chmod(path, stat.S_IWRITE | stat.S_IREAD)
        remove(path)
        return True
    except OSError:
        return False


class SwapState:
    def __init__(self, parked_id):
        self.parked_id = parked_id
        self.renamed = False
        self.created = False


def restore(docker, state):
    try:
        if state.created and docker.exists(LIVE_NAME):
            current_id = docker.container_id(LIVE_NAME)
            if current_id and current_id != state.parked_id:
                docker.stop(LIVE_NAME)
                docker.remove(LIVE_NAME)
        if state.renamed and docker.exists(PARKED_NAME):
            if not docker.running(PARKED_NAME):
                docker.start(PARKED_NAME)
            if not docker.exists(LIVE_NAME):
                docker.rename(PARKED_NAME, LIVE_NAME)
        return (
            docker.exists(LIVE_NAME)
            and docker.running(LIVE_NAME)
            and docker.container_id(LIVE_NAME) == state.parked_id
        )
    except Exception:
        return False


def _wait_ready(http, clock, sleep, timeout):
    start = clock()
    deadline = start + timeout
    while True:
        ready_status, ready_text, app_status, app_text = http.health()
        if service_ready(ready_status, ready_text, app_status, app_text):
            return True
        now = clock()
        if now >= deadline:
            return False
        sleep(3)
        if clock() <= now:
            return False


def execute_swap(
    record,
    existing_names,
    docker,
    http,
    env_text,
    directory,
    loaded_new_image_id,
    expected_new_image_id,
    require_enrollment=False,
    expected_batch_name="",
    clock=None,
    sleep=None,
    chmod=os.chmod,
    timeout=120,
):
    errors = preflight(record, existing_names, loaded_new_image_id, expected_new_image_id)
    argv = create_argv(record, NEW_IMAGE, os.path.join(directory, ENV_FILENAME))
    errors.extend(preservation_gaps(record, argv))
    result = {
        "ok": False,
        "accepted": False,
        "healthy": False,
        "restored": False,
        "mutated": False,
        "enrollmentChecked": False,
        "envRemoved": True,
        "errors": errors,
        "argv": argv,
    }
    if not require_enrollment or not getattr(http, "enrollment_configured", False):
        errors.append("bunny12 enrollment check is required before the swap")
    if errors:
        return result
    clock = clock or (lambda: 0)
    sleep = sleep or (lambda _seconds: None)
    state = SwapState(record.get("id"))
    env_path = None
    secrets = [env_text]
    try:
        env_path = write_restricted_env(directory, env_text, chmod=chmod)
        docker.rename(LIVE_NAME, PARKED_NAME)
        state.renamed = True
        result["mutated"] = True
        docker.stop(PARKED_NAME)
        if docker.running(PARKED_NAME):
            raise RuntimeError("previous container is still running")
        try:
            docker.create(argv)
        except Exception:
            if docker.exists(LIVE_NAME) and docker.container_id(LIVE_NAME) != state.parked_id:
                state.created = True
            raise
        state.created = True
        if docker.running(PARKED_NAME):
            raise RuntimeError("previous container started again before handoff")
        docker.start(LIVE_NAME)
        if not _wait_ready(http, clock, sleep, timeout):
            raise RuntimeError("readiness timeout")
        result["healthy"] = True
        if require_enrollment:
            list_status, list_text = http.batches()
            list_body = json.loads(list_text or "{}")
            batch_id = ""
            items = ((list_body.get("enrolled") or {}).get("items") or []) if isinstance(list_body, dict) else []
            if items and isinstance(items[0], dict):
                batch_id = items[0].get("batchId") or ""
            practice_status, practice_text = http.practice(batch_id)
            practice_body = json.loads(practice_text or "{}")
            result["enrollmentChecked"] = True
            contract = bunny_acceptance(
                list_status, list_body, practice_status, practice_body, expected_batch_name
            )
            if contract:
                raise RuntimeError("enrollment contract failed")
        result["ok"] = True
        result["accepted"] = result["enrollmentChecked"]
        return result
    except Exception as exc:
        result["restored"] = restore(docker, state)
        result["ok"] = False
        result["accepted"] = False
        result["errors"] = [redact(str(exc), secrets)]
        return result
    finally:
        if env_path:
            result["envRemoved"] = remove_env(env_path)


def deployment_mode(environ, hostname):
    environ = environ or {}
    dry = environ.get("DRY_RUN") == "yes"
    confirm = environ.get("CONFIRM_DEPLOY")
    if dry and confirm:
        return "refuse", "DRY_RUN and CONFIRM_DEPLOY are mutually exclusive"
    if hostname != EXPECTED_HOSTNAME or environ.get("EXPECTED_HOSTNAME") != EXPECTED_HOSTNAME:
        return "refuse", "hostname is not coderzclub-api-1"
    if environ.get("ROLLBACK") == "yes":
        if confirm != CONFIRM:
            return "refuse", "confirmation token does not match this release"
        if not str(environ.get("EXPECTED_NEW_IMAGE_ID") or "").startswith("sha256:"):
            return "refuse", "new image digest is missing"
        return "rollback", ""
    if dry:
        return "dry-run", ""
    if confirm != CONFIRM:
        return "refuse", "confirmation token does not match this release"
    if not environ.get("ENROLLMENT_BEARER"):
        return "refuse", "bunny12 enrollment token is missing"
    if not str(environ.get("EXPECTED_NEW_IMAGE_ID") or "").startswith("sha256:"):
        return "refuse", "new image digest is missing"
    return "swap", ""


def rollback(docker, new_image_id):
    """Put coderzclub-api-before-aa1ea86 back. Never removes historical containers."""
    if not docker.exists(PARKED_NAME):
        return {"ok": False, "errors": [f"{PARKED_NAME} is not present"]}
    removed_live = False
    if docker.exists(LIVE_NAME):
        if docker.image_id(LIVE_NAME) != new_image_id:
            return {"ok": False, "errors": ["live container is not the new image; rollback stopped"]}
        if docker.container_id(LIVE_NAME) == docker.container_id(PARKED_NAME):
            return {"ok": False, "errors": ["refusing to remove the parked container"]}
        docker.stop(LIVE_NAME)
        docker.remove(LIVE_NAME)
        removed_live = True
    for name in KEEP_NAMES:
        if name in getattr(docker, "removed", []):
            return {"ok": False, "errors": ["historical container was removed"]}
    if not docker.running(PARKED_NAME):
        docker.start(PARKED_NAME)
    docker.rename(PARKED_NAME, LIVE_NAME)
    return {
        "ok": docker.running(LIVE_NAME) and docker.image_id(LIVE_NAME) != new_image_id,
        "removedLive": removed_live,
        "errors": [],
    }


def _http_get(url, token=None):
    headers = {"Accept": "application/json, text/plain"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            return response.status, response.read(65536).decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        return error.code, error.read(65536).decode("utf-8", "replace")
    except Exception:
        return None, ""


class ApiProbe:
    def __init__(self, token):
        self.enrollment_configured = bool(token)
        self._token = token

    def health(self):
        ready_status, ready_text = _http_get("http://10.20.0.4:8081/actuator/health/readiness")
        app_status, app_text = _http_get("http://10.20.0.4:8080/api/problems/test")
        return ready_status, ready_text, app_status, app_text

    def batches(self):
        return _http_get("http://10.20.0.4:8080/api/my/batches?page=0&size=20", self._token)

    def practice(self, batch_id):
        allowed = set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-")
        if not batch_id or any(character not in allowed for character in batch_id):
            return 404, "{}"
        return _http_get(
            f"http://10.20.0.4:8080/api/my/batches/{batch_id}/practice?page=0&size=20",
            self._token,
        )


class DockerCli:
    def __init__(self, stop_seconds=STOP_FALLBACK):
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
        if name != LIVE_NAME or "-before-" in name or name in KEEP_NAMES:
            raise RuntimeError("refusing to remove a protected container")
        self.removed.append(name)
        self._run(["rm", name])


def _loaded_image_id(reference):
    completed = subprocess.run(
        ["docker", "image", "inspect", "--format", "{{.Id}}", reference],
        check=False, text=True, capture_output=True,
    )
    if completed.returncode != 0:
        return None
    return completed.stdout.strip() or None


def _container_names():
    completed = subprocess.run(
        ["docker", "ps", "-a", "--format", "{{.Names}}"],
        check=False, text=True, capture_output=True,
    )
    if completed.returncode != 0:
        raise RuntimeError("container name listing failed")
    return [line.strip() for line in completed.stdout.splitlines() if line.strip()]


def main(environ=None, hostname=None):
    environ = os.environ if environ is None else environ
    hostname = socket.gethostname() if hostname is None else hostname
    mode, message = deployment_mode(environ, hostname)
    if mode == "refuse":
        print(message, file=sys.stderr)
        return 2
    if mode == "rollback":
        outcome = rollback(DockerCli(), environ.get("EXPECTED_NEW_IMAGE_ID") or "")
        print("rolledBack=" + ("yes" if outcome.get("ok") else "no"))
        if outcome.get("errors"):
            print("errors=" + "; ".join(outcome["errors"]), file=sys.stderr)
        return 0 if outcome.get("ok") else 4
    completed = subprocess.run(
        ["docker", "inspect", LIVE_NAME], check=False, text=True, capture_output=True,
    )
    if completed.returncode != 0:
        print("docker inspect failed", file=sys.stderr)
        return 3
    data = json.loads(completed.stdout)[0]
    env_text = "\n".join((data.get("Config") or {}).get("Env") or []) + "\n"
    record = safe_record(data)
    del data
    names = _container_names()
    loaded = _loaded_image_id(NEW_IMAGE)
    expected_new = environ.get("EXPECTED_NEW_IMAGE_ID") or ""
    if mode == "dry-run":
        summary = public_summary(record)
        summary["containerNames"] = names
        summary["parkedNameFree"] = PARKED_NAME not in names
        summary["newImageLoaded"] = loaded
        summary["preservationGaps"] = preservation_gaps(
            record, create_argv(record, NEW_IMAGE, "/tmp/" + ENV_FILENAME)
        )
        summary["octoberCommandOmissions"] = previous_plan_omissions(record)
        print(json.dumps(summary, indent=2, sort_keys=True))
        print("mutation=none")
        return 0
    directory = os.path.join("/tmp", "cz-api-aa1ea86")
    os.makedirs(directory, mode=0o700, exist_ok=False)
    os.chmod(directory, 0o700)
    docker = DockerCli(stop_seconds=record.get("stopTimeout") or STOP_FALLBACK)
    try:
        result = execute_swap(
            record,
            names,
            docker,
            ApiProbe(environ.get("ENROLLMENT_BEARER") or ""),
            env_text,
            directory,
            loaded,
            expected_new,
            require_enrollment=True,
            expected_batch_name=environ.get("ENROLLED_BATCH_NAME") or "batch-1",
            clock=__import__("time").time,
            sleep=__import__("time").sleep,
        )
    finally:
        remove_env(os.path.join(directory, ENV_FILENAME))
        try:
            os.rmdir(directory)
        except OSError:
            pass
        env_text = ""
    print("accepted=" + ("yes" if result.get("accepted") else "no"))
    print("healthy=" + ("yes" if result.get("healthy") else "no"))
    print("restored=" + ("yes" if result.get("restored") else "no"))
    print("envRemoved=" + ("yes" if result.get("envRemoved") else "no"))
    if result.get("errors"):
        print("errors=" + "; ".join(result["errors"]), file=sys.stderr)
    return 0 if result.get("accepted") else 4


if __name__ == "__main__":
    sys.exit(main())
