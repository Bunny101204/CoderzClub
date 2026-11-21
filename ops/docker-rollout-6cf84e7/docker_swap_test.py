#!/usr/bin/env python3
"""Local unit tests for docker_swap.py (no Docker/GCP required)."""

import json
import os
import tempfile
import unittest

import docker_swap as swap


CURRENT = "sha256:" + ("a" * 64)
NEW = swap.DEFAULT_NEW_IMAGE_ID


def sample_inspect(role="api", image_id=CURRENT, concurrency="6"):
    cfg = swap.role_config(role)
    env = [
        f"SPRING_PROFILES_ACTIVE={cfg['profile']}",
        "SERVER_PORT=8080" if role == "api" else "SERVER_PORT=8082",
        "MANAGEMENT_SERVER_PORT=8081" if role == "api" else "MANAGEMENT_SERVER_PORT=8083",
        "MONGODB_URI=mongodb://secret",
        "JWT_SECRET=supersecretvalue",
    ]
    if role == "worker":
        env.append(f"WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY={concurrency}")
    bindings = {}
    for container_port, host_ip, host_port in cfg["bindings"]:
        bindings[container_port] = [{"HostIp": host_ip, "HostPort": host_port}]
    return {
        "Id": "abc123def456",
        "Name": "/" + cfg["liveName"],
        "Image": image_id,
        "Config": {
            "Image": "coderzclub-backend:old",
            "User": "10001",
            "Env": env,
            "Entrypoint": ["java", "-jar", "/app/app.jar"],
            "Cmd": [],
            "WorkingDir": "/app",
            "Labels": {},
        },
        "HostConfig": {
            "RestartPolicy": {"Name": "unless-stopped"},
            "NetworkMode": "bridge",
            "PortBindings": bindings,
            "Privileged": False,
            "Memory": 0,
            "ShmSize": swap.DEFAULT_SHM,
            "LogConfig": {"Type": "json-file", "Config": {}},
        },
        "Mounts": [],
        "State": {"Status": "running", "Running": True},
    }


class FakeDocker:
    def __init__(self, containers):
        self.containers = containers
        self.removed = []

    def exists(self, name):
        return name in self.containers

    def running(self, name):
        return bool(self.containers.get(name, {}).get("running"))

    def container_id(self, name):
        return self.containers.get(name, {}).get("id")

    def image_id(self, name):
        return self.containers.get(name, {}).get("image")

    def rename(self, source, target):
        self.containers[target] = self.containers.pop(source)

    def stop(self, name):
        self.containers[name]["running"] = False

    def start(self, name):
        self.containers[name]["running"] = True

    def create(self, argv):
        # argv ends with image id; name from --name=
        name = next(a.split("=", 1)[1] for a in argv if a.startswith("--name="))
        image = argv[-1]
        self.containers[name] = {"id": "newid", "image": image, "running": False}

    def remove(self, name):
        self.removed.append(name)
        del self.containers[name]


class DockerSwapTests(unittest.TestCase):
    def test_create_argv_preserves_private_bindings_and_env_file(self):
        record = swap.safe_record(sample_inspect("worker"))
        argv = swap.create_argv(record, NEW, "/tmp/cz.env", "coderzclub-worker")
        text = "\n".join(argv)
        self.assertIn("--publish=10.20.0.7:8082:8082", text)
        self.assertIn("--publish=10.20.0.7:8083:8083", text)
        self.assertIn("--env-file=/tmp/cz.env", text)
        self.assertIn(NEW, text)
        self.assertEqual(swap.preservation_gaps(record, argv), [])

    def test_worker_preflight_requires_concurrency_6(self):
        record = swap.safe_record(sample_inspect("worker", concurrency="2"))
        release = swap.parse_release(
            {
                "EXPECTED_CURRENT_IMAGE_ID": CURRENT,
                "EXPECTED_NEW_IMAGE_ID": NEW,
            },
            "worker",
            True,
        )
        errors = swap.preflight(record, ["coderzclub-worker"], NEW, release, "worker")
        self.assertTrue(any("WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY" in e for e in errors))

    def test_worker_preflight_ok_with_concurrency_6(self):
        record = swap.safe_record(sample_inspect("worker", concurrency="6"))
        release = swap.parse_release(
            {
                "EXPECTED_CURRENT_IMAGE_ID": CURRENT,
                "EXPECTED_NEW_IMAGE_ID": NEW,
            },
            "worker",
            True,
        )
        errors = swap.preflight(record, ["coderzclub-worker"], NEW, release, "worker")
        self.assertEqual(errors, [])

    def test_api_bindings_required(self):
        record = swap.safe_record(sample_inspect("api"))
        release = swap.parse_release(
            {
                "EXPECTED_CURRENT_IMAGE_ID": CURRENT,
                "EXPECTED_NEW_IMAGE_ID": NEW,
            },
            "api",
            True,
        )
        errors = swap.preflight(record, ["coderzclub-api"], NEW, release, "api")
        self.assertEqual(errors, [])

    def test_dry_run_mode_default_refuses_wrong_host(self):
        mode, msg = swap.deployment_mode({"DRY_RUN": "yes", "ROLE": "api"}, "wrong-host", "api")
        self.assertEqual(mode, "refuse")
        self.assertIn("hostname", msg)

    def test_confirm_token(self):
        mode, _ = swap.deployment_mode(
            {
                "DRY_RUN": "no",
                "CONFIRM_DEPLOY": "worker-6cf84e7",
                "EXPECTED_HOSTNAME": "coderzclub-worker-1",
                "EXPECTED_CURRENT_IMAGE_ID": CURRENT,
                "EXPECTED_NEW_IMAGE_ID": NEW,
            },
            "coderzclub-worker-1",
            "worker",
        )
        self.assertEqual(mode, "swap")

    def test_rollback_restores_parked(self):
        docker = FakeDocker(
            {
                "coderzclub-api": {"id": "live", "image": NEW, "running": True},
                "coderzclub-api-before-6cf84e7": {"id": "old", "image": CURRENT, "running": False},
            }
        )
        docker.live_name = "coderzclub-api"
        outcome = swap.rollback(docker, NEW, "coderzclub-api-before-6cf84e7", CURRENT, "coderzclub-api")
        self.assertTrue(outcome["ok"])
        self.assertIn("coderzclub-api", docker.containers)
        self.assertEqual(docker.image_id("coderzclub-api"), CURRENT)
        self.assertNotIn("coderzclub-api-before-6cf84e7", docker.containers)

    def test_safe_record_redacts_secret_values(self):
        record = swap.safe_record(sample_inspect("api"))
        dumped = json.dumps(record)
        self.assertNotIn("mongodb://secret", dumped)
        self.assertNotIn("supersecretvalue", dumped)
        self.assertIn("MONGODB_URI", record["envKeys"])
        self.assertEqual(record["safeEnv"]["SPRING_PROFILES_ACTIVE"], "api")

    def test_write_and_remove_env(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = swap.write_restricted_env(tmp, "A=1\n", "cz-api-6cf84e7.env")
            self.assertTrue(os.path.exists(path))
            self.assertTrue(swap.remove_env(path))
            self.assertFalse(os.path.exists(path))


if __name__ == "__main__":
    unittest.main()
