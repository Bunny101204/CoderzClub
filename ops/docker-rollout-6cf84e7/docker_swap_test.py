#!/usr/bin/env python3
"""Local unit tests for docker_swap.py (no Docker/GCP required)."""

import json
import os
import tempfile
import unittest
from unittest import mock

import docker_swap as swap


CURRENT = "sha256:" + ("a" * 64)
NEW = swap.DEFAULT_NEW_IMAGE_ID


def sample_inspect(role="api", image_id=CURRENT, concurrency="6", status="running", running=None):
    cfg = swap.role_config(role)
    if running is None:
        running = status == "running"
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
        "State": {"Status": status, "Running": running},
    }


def release_for(role, require_new=True):
    return swap.parse_release(
        {
            "EXPECTED_CURRENT_IMAGE_ID": CURRENT,
            "EXPECTED_NEW_IMAGE_ID": NEW,
        },
        role,
        require_new,
    )


class FakeDocker:
    def __init__(self, containers, fail_start_names=None):
        self.containers = containers
        self.removed = []
        self.fail_start_names = set(fail_start_names or [])
        self.start_calls = []

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
        self.start_calls.append(name)
        if name in self.fail_start_names:
            raise RuntimeError("simulated start failure")
        self.containers[name]["running"] = True

    def create(self, argv):
        name = next(a.split("=", 1)[1] for a in argv if a.startswith("--name="))
        image = argv[-1]
        self.containers[name] = {"id": "newid", "image": image, "running": False}

    def remove(self, name):
        self.removed.append(name)
        del self.containers[name]


def _fast_wait(probe, timeout=180):
    return probe.healthy()


class HealthyProbe:
    def healthy(self):
        return True


class UnhealthyProbe:
    def healthy(self):
        return False


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

    def test_probe_urls_use_private_management_ips(self):
        api = swap.role_config("api")
        worker = swap.role_config("worker")
        self.assertEqual(api["readyUrl"], "http://10.20.0.4:8081/actuator/health/readiness")
        self.assertEqual(api["healthUrl"], "http://10.20.0.4:8081/actuator/health")
        self.assertEqual(api["appUrl"], "http://10.20.0.4:8080/api/problems/test")
        self.assertEqual(worker["readyUrl"], "http://10.20.0.7:8083/actuator/health/readiness")
        self.assertEqual(worker["healthUrl"], "http://10.20.0.7:8083/actuator/health")
        self.assertIsNone(worker["appUrl"])

    def test_worker_preflight_requires_concurrency_6(self):
        record = swap.safe_record(sample_inspect("worker", concurrency="2"))
        errors = swap.preflight(record, ["coderzclub-worker"], NEW, release_for("worker"), "worker")
        self.assertTrue(any("WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY" in e for e in errors))

    def test_worker_preflight_ok_with_concurrency_6(self):
        record = swap.safe_record(sample_inspect("worker", concurrency="6"))
        errors = swap.preflight(record, ["coderzclub-worker"], NEW, release_for("worker"), "worker")
        self.assertEqual(errors, [])

    def test_api_bindings_required(self):
        record = swap.safe_record(sample_inspect("api"))
        errors = swap.preflight(record, ["coderzclub-api"], NEW, release_for("api"), "api")
        self.assertEqual(errors, [])

    def test_incorrect_private_binding_rejected(self):
        data = sample_inspect("api")
        data["HostConfig"]["PortBindings"]["8081/tcp"] = [
            {"HostIp": "127.0.0.1", "HostPort": "8081"}
        ]
        record = swap.safe_record(data)
        errors = swap.preflight(record, ["coderzclub-api"], NEW, release_for("api"), "api")
        self.assertTrue(any("10.20.0.4:8081" in e or "private" in e for e in errors))

    def test_stopped_source_refused_by_default(self):
        record = swap.safe_record(sample_inspect("api", status="exited", running=False))
        errors = swap.preflight(record, ["coderzclub-api"], NEW, release_for("api"), "api")
        self.assertTrue(any("ALLOW_STOPPED_SOURCE=yes" in e for e in errors))

    def test_stopped_source_allowed_with_explicit_flag(self):
        record = swap.safe_record(sample_inspect("api", status="exited", running=False))
        errors = swap.preflight(
            record, ["coderzclub-api"], NEW, release_for("api"), "api", allow_stopped=True
        )
        self.assertEqual(errors, [])
        # Digest / restart / bindings still enforced under maintenance mode.
        self.assertEqual(record["imageId"], CURRENT)
        self.assertEqual(record["restart"], "unless-stopped")

    def test_unexpected_dead_status_refused_even_with_flag(self):
        record = swap.safe_record(sample_inspect("worker", status="dead", running=False))
        errors = swap.preflight(
            record, ["coderzclub-worker"], NEW, release_for("worker"), "worker", allow_stopped=True
        )
        self.assertTrue(any("not running" in e for e in errors))

    def test_dry_run_mode_default_refuses_wrong_host(self):
        mode, msg = swap.deployment_mode({"DRY_RUN": "yes", "ROLE": "api"}, "wrong-host", "api")
        self.assertEqual(mode, "refuse")
        self.assertIn("hostname", msg)

    def test_default_dry_run_yes(self):
        mode, _ = swap.deployment_mode(
            {
                "EXPECTED_CURRENT_IMAGE_ID": CURRENT,
                "EXPECTED_HOSTNAME": "coderzclub-api-1",
            },
            "coderzclub-api-1",
            "api",
        )
        self.assertEqual(mode, "dry-run")

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

    def test_role_probe_healthy_success(self):
        responses = {
            "http://10.20.0.4:8081/actuator/health/readiness": (200, '{"status":"UP"}'),
            "http://10.20.0.4:8081/actuator/health": (200, '{"status":"UP"}'),
            "http://10.20.0.4:8080/api/problems/test": (200, "Problems endpoint is working"),
        }

        def fake_get(url):
            return responses[url]

        with mock.patch.object(swap, "_http_get", side_effect=fake_get):
            self.assertTrue(swap.RoleProbe("api").healthy())

    def test_role_probe_rejects_wrong_binding_path(self):
        # Loopback responses must not satisfy private-IP probe URLs.
        def fake_get(url):
            if "127.0.0.1" in url:
                return 200, '{"status":"UP"}'
            return None, ""

        with mock.patch.object(swap, "_http_get", side_effect=fake_get):
            self.assertFalse(swap.RoleProbe("api").healthy())

    def test_execute_swap_from_stopped_source_preserves_parked(self):
        record = swap.safe_record(sample_inspect("api", status="exited", running=False))
        release = release_for("api")
        docker = FakeDocker(
            {"coderzclub-api": {"id": record["id"], "image": CURRENT, "running": False}}
        )
        env_text = "\n".join(sample_inspect("api")["Config"]["Env"]) + "\n"
        with tempfile.TemporaryDirectory() as tmp:
            result = swap.execute_swap(
                record,
                ["coderzclub-api"],
                docker,
                HealthyProbe(),
                env_text,
                tmp,
                NEW,
                release,
                "api",
                allow_stopped=True,
            )
        self.assertTrue(result["ok"])
        self.assertTrue(result["healthy"])
        self.assertTrue(result["parkedPreserved"])
        self.assertTrue(result["envRemoved"])
        self.assertIn(release["parkedName"], docker.containers)
        self.assertFalse(docker.running(release["parkedName"]))
        self.assertEqual(docker.image_id("coderzclub-api"), NEW)
        self.assertTrue(docker.running("coderzclub-api"))
        # Secrets never appear in result payload.
        dumped = json.dumps(result)
        self.assertNotIn("mongodb://secret", dumped)
        self.assertNotIn("supersecretvalue", dumped)

    def test_execute_swap_stopped_without_flag_fails_closed(self):
        record = swap.safe_record(sample_inspect("api", status="exited", running=False))
        release = release_for("api")
        docker = FakeDocker(
            {"coderzclub-api": {"id": record["id"], "image": CURRENT, "running": False}}
        )
        with tempfile.TemporaryDirectory() as tmp:
            result = swap.execute_swap(
                record,
                ["coderzclub-api"],
                docker,
                HealthyProbe(),
                "A=1\n",
                tmp,
                NEW,
                release,
                "api",
                allow_stopped=False,
            )
        self.assertFalse(result["ok"])
        self.assertFalse(result["mutated"])
        self.assertTrue(any("ALLOW_STOPPED_SOURCE=yes" in e for e in result["errors"]))

    def test_execute_swap_failure_rolls_back_and_cleans_env(self):
        """Running source → start fails → original restored running + health-checked."""
        record = swap.safe_record(sample_inspect("api"))
        release = release_for("api")
        docker = FakeDocker(
            {"coderzclub-api": {"id": record["id"], "image": CURRENT, "running": True}},
            fail_start_names={"coderzclub-api"},
        )
        env_text = "JWT_SECRET=supersecretvalue\nSPRING_PROFILES_ACTIVE=api\n"
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.object(swap, "_wait_ready", side_effect=_fast_wait):
                result = swap.execute_swap(
                    record,
                    ["coderzclub-api"],
                    docker,
                    HealthyProbe(),
                    env_text,
                    tmp,
                    NEW,
                    release,
                    "api",
                )
            leftover = [name for name in os.listdir(tmp) if name.endswith(".env")]
        self.assertFalse(result["ok"])
        self.assertTrue(result["restored"])
        self.assertEqual(result["restoredState"], "running")
        self.assertTrue(result["restoredHealthy"])
        self.assertTrue(result["sourceWasRunning"])
        self.assertTrue(result["envRemoved"])
        self.assertEqual(leftover, [])
        self.assertEqual(docker.container_id("coderzclub-api"), record["id"])
        self.assertEqual(docker.image_id("coderzclub-api"), CURRENT)
        self.assertTrue(docker.running("coderzclub-api"))
        self.assertTrue(all("supersecretvalue" not in e for e in result["errors"]))

    def test_stopped_api_start_failure_restores_stopped(self):
        record = swap.safe_record(sample_inspect("api", status="exited", running=False))
        release = release_for("api")
        docker = FakeDocker(
            {"coderzclub-api": {"id": record["id"], "image": CURRENT, "running": False}},
            fail_start_names={"coderzclub-api"},
        )
        env_text = "\n".join(sample_inspect("api")["Config"]["Env"]) + "\n"
        with tempfile.TemporaryDirectory() as tmp:
            result = swap.execute_swap(
                record,
                ["coderzclub-api"],
                docker,
                HealthyProbe(),
                env_text,
                tmp,
                NEW,
                release,
                "api",
                allow_stopped=True,
            )
            leftover = [name for name in os.listdir(tmp) if name.endswith(".env")]
        self.assertFalse(result["ok"])
        self.assertTrue(result["restored"])
        self.assertEqual(result["restoredState"], "stopped")
        self.assertFalse(result["restoredHealthy"])
        self.assertFalse(result["sourceWasRunning"])
        self.assertTrue(result["envRemoved"])
        self.assertEqual(leftover, [])
        self.assertEqual(docker.container_id("coderzclub-api"), record["id"])
        self.assertEqual(docker.image_id("coderzclub-api"), CURRENT)
        self.assertFalse(docker.running("coderzclub-api"))
        # Only the failed new-container start; parked/original must not be started.
        self.assertEqual(docker.start_calls, ["coderzclub-api"])
        self.assertNotIn(release["parkedName"], docker.start_calls)
        dumped = json.dumps(result)
        self.assertNotIn("mongodb://secret", dumped)
        self.assertNotIn("supersecretvalue", dumped)

    def test_stopped_worker_start_failure_restores_stopped(self):
        record = swap.safe_record(
            sample_inspect("worker", status="exited", running=False, concurrency="6")
        )
        release = release_for("worker")
        docker = FakeDocker(
            {"coderzclub-worker": {"id": record["id"], "image": CURRENT, "running": False}},
            fail_start_names={"coderzclub-worker"},
        )
        env_text = "\n".join(
            sample_inspect("worker", concurrency="6")["Config"]["Env"]
        ) + "\n"
        with tempfile.TemporaryDirectory() as tmp:
            result = swap.execute_swap(
                record,
                ["coderzclub-worker"],
                docker,
                HealthyProbe(),
                env_text,
                tmp,
                NEW,
                release,
                "worker",
                allow_stopped=True,
            )
        self.assertFalse(result["ok"])
        self.assertTrue(result["restored"])
        self.assertEqual(result["restoredState"], "stopped")
        self.assertFalse(docker.running("coderzclub-worker"))
        self.assertEqual(docker.container_id("coderzclub-worker"), record["id"])
        self.assertEqual(docker.image_id("coderzclub-worker"), CURRENT)
        self.assertEqual(docker.start_calls, ["coderzclub-worker"])
        self.assertEqual(record["safeEnv"]["WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY"], "6")

    def test_stopped_source_readiness_failure_restores_stopped(self):
        record = swap.safe_record(sample_inspect("api", status="exited", running=False))
        release = release_for("api")
        docker = FakeDocker(
            {"coderzclub-api": {"id": record["id"], "image": CURRENT, "running": False}}
        )
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.object(swap, "_wait_ready", side_effect=_fast_wait):
                result = swap.execute_swap(
                    record,
                    ["coderzclub-api"],
                    docker,
                    UnhealthyProbe(),
                    "SPRING_PROFILES_ACTIVE=api\nJWT_SECRET=supersecretvalue\n",
                    tmp,
                    NEW,
                    release,
                    "api",
                    allow_stopped=True,
                )
        self.assertFalse(result["ok"])
        self.assertTrue(result["restored"])
        self.assertEqual(result["restoredState"], "stopped")
        self.assertFalse(result["restoredHealthy"])
        self.assertFalse(docker.running("coderzclub-api"))
        self.assertEqual(docker.container_id("coderzclub-api"), record["id"])
        self.assertEqual(docker.image_id("coderzclub-api"), CURRENT)
        # New container was started once then removed; original never started.
        self.assertEqual(docker.start_calls, ["coderzclub-api"])
        self.assertTrue(any("left stopped" in e for e in result["errors"]))
        self.assertTrue(all("supersecretvalue" not in e for e in result["errors"]))

    def test_running_source_readiness_failure_restores_running_and_healthchecks(self):
        record = swap.safe_record(sample_inspect("api"))
        release = release_for("api")
        docker = FakeDocker(
            {"coderzclub-api": {"id": record["id"], "image": CURRENT, "running": True}}
        )
        # First wait (new container) fails; second wait (restored) succeeds.
        waits = [False, True]

        def sequenced_wait(probe, timeout=180):
            return waits.pop(0)

        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.object(swap, "_wait_ready", side_effect=sequenced_wait):
                result = swap.execute_swap(
                    record,
                    ["coderzclub-api"],
                    docker,
                    HealthyProbe(),
                    "SPRING_PROFILES_ACTIVE=api\n",
                    tmp,
                    NEW,
                    release,
                    "api",
                )
        self.assertFalse(result["ok"])
        self.assertTrue(result["restored"])
        self.assertEqual(result["restoredState"], "running")
        self.assertTrue(result["restoredHealthy"])
        self.assertTrue(docker.running("coderzclub-api"))
        self.assertEqual(docker.container_id("coderzclub-api"), record["id"])
        self.assertEqual(docker.image_id("coderzclub-api"), CURRENT)
        self.assertIn(release["parkedName"], docker.start_calls)

    def test_worker_concurrency_preserved_in_env_keys(self):
        record = swap.safe_record(sample_inspect("worker", concurrency="6"))
        self.assertEqual(record["safeEnv"]["WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY"], "6")
        self.assertIn("WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY", record["envKeys"])


if __name__ == "__main__":
    unittest.main()
