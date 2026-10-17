#!/usr/bin/env python3
"""Read-only inventory of one CoderzClub VM.

Prints container identity, image IDs, private port bindings, and a hash of the
environment. Secret values are not printed. Set EXPECTED_HOSTNAME to make a
hostname mismatch a hard failure.
"""

import hashlib
import ipaddress
import json
import os
import socket
import subprocess
import sys

SAFE_ENV = {
    "APP_CORS_ALLOWED_ORIGINS",
    "APP_FRONTEND_URL",
    "SPRING_PROFILES_ACTIVE",
    "SERVER_PORT",
    "MANAGEMENT_SERVER_PORT",
}
REQUIRED_API_CORS = "https://coderzclub.site,https://coderzclub.coderzclub1.workers.dev"
REQUIRED_FRONTEND = "https://coderzclub.site"
RELEASE_IMAGE = "coderzclub-backend:batch-mgmt-20261017"
RELEASE_IMAGE_ID = "sha256:5fdb39d431e09d729a6b8216b67368d9e727163d84d40eec056d854aa553f6e0"


def run(args):
    completed = subprocess.run(args, check=False, text=True, capture_output=True)
    if completed.returncode != 0:
        raise SystemExit(completed.stderr.strip() or f"command failed: {' '.join(args)}")
    return completed.stdout


def private_binding(host_ip):
    if not host_ip or host_ip in {"0.0.0.0", "::", "::0"}:
        return False
    try:
        address = ipaddress.ip_address(host_ip)
    except ValueError:
        return False
    return address.is_private


def image_id(reference):
    completed = subprocess.run(
        ["docker", "image", "inspect", "--format", "{{.Id}}", reference],
        check=False, text=True, capture_output=True,
    )
    if completed.returncode != 0:
        return None
    return completed.stdout.strip()


def env_map(env_list):
    values = {}
    for item in env_list or []:
        if "=" not in item:
            continue
        key, value = item.split("=", 1)
        values[key] = value
    return values


def container_record(name):
    raw = run(["docker", "inspect", name])
    data = json.loads(raw)[0]
    env = data.get("Config", {}).get("Env") or []
    ports = data.get("HostConfig", {}).get("PortBindings") or {}
    safe = {key: env_map(env).get(key) for key in sorted(SAFE_ENV)}
    bindings = []
    public_bindings = []
    for container_port, items in sorted(ports.items()):
        for item in items or []:
            host_ip = item.get("HostIp") or ""
            binding = {
                "containerPort": container_port,
                "hostIp": host_ip,
                "hostPort": item.get("HostPort") or "",
            }
            bindings.append(binding)
            if not private_binding(host_ip):
                public_bindings.append(binding)
    digest = hashlib.sha256(("\n".join(sorted(env))).encode("utf-8")).hexdigest()
    return {
        "name": data.get("Name", "").lstrip("/"),
        "id": data.get("Id"),
        "image": data.get("Config", {}).get("Image"),
        "imageId": data.get("Image"),
        "status": data.get("State", {}).get("Status"),
        "restart": (data.get("HostConfig", {}).get("RestartPolicy") or {}).get("Name"),
        "network": data.get("HostConfig", {}).get("NetworkMode"),
        "binds": data.get("HostConfig", {}).get("Binds") or [],
        "privileged": bool(data.get("HostConfig", {}).get("Privileged")),
        "user": data.get("Config", {}).get("User") or "",
        "entrypoint": data.get("Config", {}).get("Entrypoint") or [],
        "cmd": data.get("Config", {}).get("Cmd") or [],
        "envKeys": sorted(env_map(env)),
        "envSha256": digest,
        "safeEnv": safe,
        "bindings": bindings,
        "publicBindings": public_bindings,
    }


def running_summary():
    rows = []
    for line in run(["docker", "ps", "--no-trunc", "--format", "{{json .}}"]).splitlines():
        if not line.strip():
            continue
        item = json.loads(line)
        rows.append({
            "id": item.get("ID"),
            "names": item.get("Names"),
            "image": item.get("Image"),
            "status": item.get("Status"),
            "ports": item.get("Ports"),
        })
    return rows


def inventory(container_name):
    hostname = socket.gethostname()
    expected = os.environ.get("EXPECTED_HOSTNAME", "")
    report = {
        "hostname": hostname,
        "expectedHostname": expected,
        "hostnameMatch": (not expected) or hostname == expected,
        "releaseImage": RELEASE_IMAGE,
        "releaseImagePresent": image_id(RELEASE_IMAGE),
        "expectedReleaseImageId": RELEASE_IMAGE_ID,
        "container": container_record(container_name) if container_name else None,
        "running": running_summary(),
    }
    return report


def validate_report(report, role):
    errors = []
    if not report["hostnameMatch"]:
        errors.append(f"hostname is {report['hostname']}, expected {report['expectedHostname']}")
    container = report.get("container")
    if container is None:
        errors.append("container name is required")
        return errors
    if container["privileged"]:
        errors.append("container is privileged")
    if container["publicBindings"]:
        errors.append("a published port is not bound to a private address")
    if container["restart"] != "unless-stopped":
        errors.append(f"restart policy is {container['restart']}")
    profile = (container.get("safeEnv") or {}).get("SPRING_PROFILES_ACTIVE")
    if profile != role:
        errors.append(f"SPRING_PROFILES_ACTIVE is {profile}")
    if role == "api":
        safe = container.get("safeEnv") or {}
        if safe.get("APP_CORS_ALLOWED_ORIGINS") != REQUIRED_API_CORS:
            errors.append("APP_CORS_ALLOWED_ORIGINS does not match the approved pair of origins")
        if safe.get("APP_FRONTEND_URL") != REQUIRED_FRONTEND:
            errors.append("APP_FRONTEND_URL is not https://coderzclub.site")
    present = report.get("releaseImagePresent")
    if present and present != RELEASE_IMAGE_ID:
        errors.append("local release tag points at a different image id")
    return errors


def main():
    container = os.environ.get("CONTAINER_NAME", "")
    role = os.environ.get("RELEASE_ROLE", "")
    if role not in {"api", "worker"}:
        print("Set RELEASE_ROLE=api or RELEASE_ROLE=worker.", file=sys.stderr)
        return 2
    if not container:
        print("Set CONTAINER_NAME to the live container, after docker ps.", file=sys.stderr)
        return 2
    report = inventory(container)
    errors = validate_report(report, role)
    report["errors"] = errors
    text = json.dumps(report, indent=2, sort_keys=True)
    out = os.environ.get("INSPECT_OUT")
    if out:
        os.umask(0o077)
        with open(out, "w", encoding="utf-8") as handle:
            handle.write(text)
            handle.write("\n")
        os.chmod(out, 0o600)
    print(text)
    return 0 if not errors else 3


if __name__ == "__main__":
    sys.exit(main())
