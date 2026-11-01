import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "batch-mgmt-release"))
import swap_safety

import api_release as release

SECRET = "mongodb://unit-test-secret-value"
CURRENT_IMAGE_ID = "sha256:" + "b" * 64
NEW_DIGEST = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
OLD_ID = "old-container-id"
FULL_ID = OLD_ID + "0123456789abcdef"
RELEASE_ID = "a37c5a5"


def release_env(**overrides):
    env = {
        "RELEASE_ID": RELEASE_ID,
        "EXPECTED_HOSTNAME": release.EXPECTED_HOSTNAME,
        "EXPECTED_CURRENT_IMAGE_ID": CURRENT_IMAGE_ID,
        "EXPECTED_NEW_IMAGE_ID": NEW_DIGEST,
        "NEW_IMAGE": "coderzclub-backend:api-" + RELEASE_ID,
        "CONFIRM_DEPLOY": "api-" + RELEASE_ID,
        "ROLLBACK_CONTAINER": "coderzclub-api-before-" + RELEASE_ID,
        "ENV_FILENAME": "cz-api-" + RELEASE_ID + ".env",
    }
    env.update(overrides)
    return env


def inspect_payload(secret=SECRET):
    return {
        "Id": OLD_ID + "0123456789abcdef",
        "Name": "/coderzclub-api",
        "Image": CURRENT_IMAGE_ID,
        "State": {"Status": "running", "Running": True, "Health": {"Status": "healthy"}},
        "Config": {
            "Image": "coderzclub-backend:api-814037d",
            "User": "10001",
            "Hostname": (OLD_ID + "0123456789abcdef")[:12],
            "Entrypoint": ["java", "-jar", "/app/app.jar"],
            "Cmd": None,
            "WorkingDir": "",
            "StopSignal": "SIGTERM",
            "Env": [
                "SPRING_PROFILES_ACTIVE=api",
                "APP_CORS_ALLOWED_ORIGINS=" + release.REQUIRED_CORS,
                "APP_FRONTEND_URL=https://coderzclub.site",
                "MONGODB_URI=" + secret,
                "SUBMISSION_QUEUE_NAME=coderzclub.submissions",
                "CONTEST_RUN_QUEUE_NAME=coderzclub.contest-runs",
            ],
            "Labels": {
                "org.opencontainers.image.created": "2026-10-17T00:00:00Z",
                "org.opencontainers.image.description": "CoderzClub API",
                "org.opencontainers.image.title": "coderzclub-backend",
                "org.opencontainers.image.version": "batch-mgmt-20261017",
            },
            "Healthcheck": {
                "Test": ["CMD-SHELL", "wget -q -O - http://127.0.0.1:8081/actuator/health/readiness"],
                "Interval": 30_000_000_000,
                "Timeout": 5_000_000_000,
                "Retries": 3,
                "StartPeriod": 20_000_000_000,
            },
        },
        "HostConfig": {
            "RestartPolicy": {"Name": "unless-stopped", "MaximumRetryCount": 0},
            "NetworkMode": "bridge",
            "PortBindings": {
                "8080/tcp": [{"HostIp": "10.20.0.4", "HostPort": "8080"}],
                "8081/tcp": [{"HostIp": "10.20.0.4", "HostPort": "8081"}],
            },
            "Binds": [],
            "LogConfig": {"Type": "json-file", "Config": {"max-size": "10m", "max-file": "3"}},
            "Memory": 536870912,
            "MemoryReservation": 268435456,
            "MemorySwap": 1073741824,
            "NanoCpus": 1500000000,
            "CpuShares": 1024,
            "PidsLimit": 256,
            "Ulimits": [{"Name": "nofile", "Soft": 1024, "Hard": 2048}],
            "ReadonlyRootfs": True,
            "CapAdd": ["NET_BIND_SERVICE"],
            "CapDrop": ["ALL"],
            "SecurityOpt": ["no-new-privileges:true"],
            "Privileged": False,
            "Init": True,
            "ShmSize": 32 * 1024 * 1024,
            "Tmpfs": {"/tmp": "rw,noexec,nosuid,size=64m"},
            "Sysctls": {"net.core.somaxconn": "1024"},
            "Dns": ["169.254.169.254"],
            "ExtraHosts": ["rabbit:10.20.0.2"],
            "GroupAdd": ["10001"],
            "Devices": [{
                "PathOnHost": "/dev/net/tun",
                "PathInContainer": "/dev/net/tun",
                "CgroupPermissions": "rwm",
            }],
            "CgroupnsMode": "private",
            "Runtime": "runc",
            "IpcMode": "private",
            "StopTimeout": 25,
            "PublishAllPorts": False,
            "AutoRemove": False,
        },
        "Mounts": [{
            "Type": "bind",
            "Source": "/var/coderzclub/tmp",
            "Destination": "/tmp/scratch",
            "RW": False,
            "Propagation": "rprivate",
        }],
    }


def enrolled_body():
    return {
        "batches": [],
        "enrolled": {
            "items": [{
                "batchId": "batch-object-id",
                "name": "batch-1",
                "description": "Morning batch",
                "active": True,
                "assignmentCount": 1,
                "problems": [{
                    "problemId": "problem-1",
                    "numericId": 12,
                    "title": "Add two numbers",
                    "difficulty": "EASY",
                }],
            }],
            "currentPage": 0,
            "totalPages": 1,
            "totalItems": 1,
        },
    }


def practice_body():
    return {
        "batchId": "batch-object-id",
        "name": "batch-1",
        "problems": [{
            "problemId": "problem-1",
            "numericId": 12,
            "title": "Add two numbers",
            "difficulty": "EASY",
        }],
        "currentPage": 0,
        "totalPages": 1,
        "totalItems": 1,
    }


class Clock:
    def __init__(self):
        self.now = 0

    def __call__(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class FakeDocker:
    def __init__(self, fail=None, leave_created_on_failure=False):
        self.fail = fail
        self.leave_created_on_failure = leave_created_on_failure
        self.calls = []
        self.removed = []
        self.containers = {
            release.LIVE_NAME: {"id": FULL_ID, "running": True, "image": CURRENT_IMAGE_ID},
            "coderzclub-api-before-batch-mgmt": {
                "id": "october-parked", "running": False, "image": "sha256:historical",
            },
        }

    def _hit(self, name):
        self.calls.append(name)
        if self.fail == name:
            self.fail = None
            raise RuntimeError(name + " failed " + SECRET)

    def exists(self, name):
        return name in self.containers

    def running(self, name):
        return bool(self.containers.get(name, {}).get("running"))

    def container_id(self, name):
        container = self.containers.get(name)
        return None if container is None else container["id"]

    def image_id(self, name):
        return self.containers[name]["image"]

    def rename(self, source, target):
        self._hit("rename")
        self.containers[target] = self.containers.pop(source)

    def stop(self, name):
        self._hit("stop")
        self.containers[name]["running"] = False

    def start(self, name):
        self._hit("start")
        self.containers[name]["running"] = True

    def create(self, argv):
        self.calls.append("create")
        if self.fail == "create":
            if self.leave_created_on_failure:
                self.containers[release.LIVE_NAME] = {
                    "id": "partial-new", "running": False, "image": NEW_DIGEST,
                }
            raise RuntimeError("create failed " + SECRET)
        self.containers[release.LIVE_NAME] = {
            "id": "new-container", "running": False, "image": NEW_DIGEST,
        }

    def remove(self, name):
        self._hit("remove")
        self.removed.append(name)
        del self.containers[name]


class FakeHttp:
    def __init__(self, health=True, enrollment=True, forbidden=False, configured=True):
        self.health_ok = health
        self.enrollment_ok = enrollment
        self.forbidden = forbidden
        self.enrollment_configured = configured
        self.practice_calls = []

    def health(self):
        if not self.health_ok:
            return 503, '{"status":"DOWN"}', 503, "down"
        return 200, '{"status":"UP","components":{"rabbit":{"status":"UP"}}}', 200, "Problems endpoint is working"

    def batches(self):
        if not self.enrollment_ok:
            return 200, json.dumps({"batches": []})
        body = enrolled_body()
        if self.forbidden:
            body["enrolled"]["items"][0]["email"] = "student@example.com"
        return 200, json.dumps(body)

    def practice(self, batch_id):
        self.practice_calls.append(batch_id)
        if not self.enrollment_ok:
            return 404, "{}"
        body = practice_body()
        if self.forbidden:
            body["problems"][0]["hiddenTestCases"] = [{"input": "secret"}]
        return 200, json.dumps(body)


class ApiReleaseSafetyTest(unittest.TestCase):
    def setUp(self):
        self.record = release.safe_record(inspect_payload())
        self.names = [release.LIVE_NAME, "coderzclub-api-before-batch-mgmt", "coderzclub-api-before-cors"]
        self.settings = release.parse_release(release_env(), True)
        self.directory = tempfile.TemporaryDirectory()
        self.chmod_modes = []

    def tearDown(self):
        self.directory.cleanup()

    def chmod(self, path, mode):
        self.chmod_modes.append(mode)
        os.chmod(path, mode)

    def swap(self, docker, http, require=True, clock=None, sleep=None):
        return release.execute_swap(
            self.record,
            self.names,
            docker,
            http,
            "MONGODB_URI=" + SECRET + "\n",
            self.directory.name,
            NEW_DIGEST,
            self.settings,
            require_enrollment=require,
            expected_batch_name="batch-1",
            clock=clock or (lambda: 0),
            sleep=sleep or (lambda _seconds: None),
            chmod=self.chmod,
        )

    def test_summary_lists_runtime_settings_without_secret_values(self):
        text = json.dumps(release.public_summary(self.record), sort_keys=True)
        self.assertNotIn(SECRET, text)
        self.assertNotIn("unit-test-secret", text)
        self.assertIn("MONGODB_URI", self.record["envKeys"])
        self.assertEqual(self.record["user"], "10001")
        self.assertEqual(self.record["network"], "bridge")
        self.assertEqual(self.record["restart"], "unless-stopped")
        self.assertEqual(self.record["logDriver"], "json-file")
        self.assertEqual(self.record["logOptions"]["max-file"], "3")
        self.assertEqual(self.record["memory"], 536870912)
        self.assertEqual(self.record["mounts"][0]["destination"], "/tmp/scratch")
        self.assertEqual(self.record["capDrop"], ["ALL"])
        self.assertEqual(self.record["securityOpt"], ["no-new-privileges:true"])
        self.assertEqual(self.record["healthcheck"]["interval"], "30s")
        self.assertEqual(self.record["safeEnv"]["SPRING_PROFILES_ACTIVE"], "api")
        self.assertEqual(self.record["hostname"], "")
        self.assertEqual(self.record["unsupported"], [])

    def test_new_command_preserves_settings_the_october_command_drops(self):
        argv = release.create_argv(self.record, self.settings["image"], "/tmp/" + self.settings["envFilename"])
        self.assertEqual(release.preservation_gaps(self.record, argv), [])
        self.assertIn("--memory=536870912", argv)
        self.assertIn("--log-driver=json-file", argv)
        self.assertIn("--log-opt=max-size=10m", argv)
        self.assertIn("--mount=type=bind,dst=/tmp/scratch,src=/var/coderzclub/tmp,readonly,bind-propagation=rprivate", argv)
        self.assertIn("--health-interval=30s", argv)
        self.assertIn("--cap-add=NET_BIND_SERVICE", argv)
        self.assertIn("--ulimit=nofile=1024:2048", argv)
        self.assertIn("--tmpfs=/tmp:rw,noexec,nosuid,size=64m", argv)
        self.assertIn("--device=/dev/net/tun:/dev/net/tun:rwm", argv)
        self.assertIn("--init", argv)
        self.assertIn("--env-file=/tmp/" + self.settings["envFilename"], argv)
        joined = "\n".join(argv)
        self.assertNotIn(SECRET, joined)
        self.assertNotIn("--label", joined)
        self.assertNotIn("batch-mgmt-20261017", joined)
        self.assertNotIn("2026-10-17T00:00:00Z", joined)
        self.assertEqual(argv[-1], self.settings["image"])
        omissions = release.previous_plan_omissions(self.record)
        self.assertIn("logging", omissions)
        self.assertIn("resource limits", omissions)
        self.assertIn("mounts", omissions)
        self.assertIn("healthcheck", omissions)
        old, old_errors = swap_safety.create_command({
            "name": release.LIVE_NAME,
            "network": "bridge",
            "user": "10001",
            "readonlyRootfs": True,
            "capDrop": ["ALL"],
            "securityOpt": ["no-new-privileges:true"],
            "stopTimeout": 25,
            "bindings": self.record["bindings"],
            "binds": [],
            "entrypoint": release.IMAGE_ENTRYPOINT,
            "cmd": [],
        }, "coderzclub-backend:batch-mgmt-20261017")
        self.assertEqual(old_errors, [])
        old_text = "\n".join(old)
        self.assertNotIn("--log-driver", old_text)
        self.assertNotIn("--memory", old_text)
        self.assertNotIn("--mount", old_text)
        self.assertNotIn("--health-cmd", old_text)
        self.assertNotIn("--cap-add", old_text)
        self.assertNotIn(self.settings["image"], old_text)

    def test_existing_rollback_names_do_not_conflict_until_the_new_name_exists(self):
        parked = self.settings["parkedName"]
        self.assertEqual(release.name_conflicts(self.names, parked), [])
        self.assertTrue(release.name_conflicts(self.names + [parked], parked))

    def test_contract_rejects_missing_enrolled_field_and_forbidden_keys(self):
        self.assertTrue(release.enrollment_errors(200, {"batches": []}))
        self.assertEqual(release.enrollment_errors(200, enrolled_body()), [])
        forbidden = enrolled_body()
        forbidden["enrolled"]["items"][0]["email"] = "student@example.com"
        self.assertTrue(release.enrollment_errors(200, forbidden))
        self.assertEqual(
            release.bunny_acceptance(200, enrolled_body(), 200, practice_body(), "batch-1"),
            [],
        )
        self.assertTrue(release.service_ready(
            200, '{"status":"UP"}', 200, "Problems endpoint is working"
        ))
        self.assertFalse(release.service_ready(200, '{"status":"DOWN"}', 200, "Problems endpoint is working"))
        self.assertFalse(release.service_ready(200, "UP", 200, "Problems endpoint is working"))

    def test_swap_refuses_without_enrollment_check_or_loaded_image(self):
        docker = FakeDocker()
        result = self.swap(docker, FakeHttp(configured=False), require=True)
        self.assertFalse(result["mutated"])
        self.assertEqual(docker.calls, [])
        self.assertIn("enrollment", result["errors"][0])
        missing = release.execute_swap(
            self.record, self.names, docker, FakeHttp(), "MONGODB_URI=" + SECRET,
            self.directory.name, None, self.settings, require_enrollment=True,
        )
        self.assertFalse(missing["mutated"])
        self.assertTrue(any("digest" in item for item in missing["errors"]))

    def test_create_failure_restores_previous_container_and_removes_env(self):
        docker = FakeDocker(fail="create")
        result = self.swap(docker, FakeHttp())
        self.assertFalse(result["ok"])
        self.assertTrue(result["restored"])
        self.assertTrue(result["envRemoved"])
        self.assertIn(0o600, self.chmod_modes)
        self.assertFalse(os.path.exists(os.path.join(self.directory.name, self.settings["envFilename"])))
        self.assertEqual(docker.container_id(release.LIVE_NAME), FULL_ID)
        self.assertTrue(docker.running(release.LIVE_NAME))
        self.assertNotIn(self.settings["parkedName"], docker.containers)
        self.assertIn("coderzclub-api-before-batch-mgmt", docker.containers)
        self.assertNotIn(SECRET, "\n".join(result["errors"]))

    def test_partial_create_failure_removes_only_the_new_container(self):
        docker = FakeDocker(fail="create", leave_created_on_failure=True)
        result = self.swap(docker, FakeHttp())
        self.assertTrue(result["restored"])
        self.assertIn(release.LIVE_NAME, docker.removed)
        self.assertNotIn("coderzclub-api-before-batch-mgmt", docker.removed)
        self.assertEqual(docker.container_id(release.LIVE_NAME), FULL_ID)

    def test_startup_failure_and_script_exception_restore(self):
        started = FakeDocker(fail="start")
        result = self.swap(started, FakeHttp())
        self.assertTrue(result["restored"])
        self.assertTrue(result["envRemoved"])
        self.assertEqual(started.container_id(release.LIVE_NAME), FULL_ID)
        self.assertNotIn(SECRET, "\n".join(result["errors"]))

        exploded = FakeDocker()
        original_stop = exploded.stop

        def boom(name):
            original_stop(name)
            raise RuntimeError("stop exploded")

        exploded.stop = boom
        result = self.swap(exploded, FakeHttp())
        self.assertTrue(result["restored"])
        self.assertTrue(result["envRemoved"])
        self.assertFalse(os.path.exists(os.path.join(self.directory.name, self.settings["envFilename"])))

    def test_readiness_timeout_and_bad_enrollment_roll_back(self):
        clock = Clock()
        docker = FakeDocker()
        result = self.swap(docker, FakeHttp(health=False), clock=clock, sleep=clock.sleep)
        self.assertFalse(result["healthy"])
        self.assertTrue(result["restored"])
        self.assertGreaterEqual(clock.now, 120)
        self.assertEqual(docker.container_id(release.LIVE_NAME), FULL_ID)

        contract = FakeDocker()
        failed = self.swap(contract, FakeHttp(enrollment=False))
        self.assertTrue(failed["enrollmentChecked"])
        self.assertFalse(failed["accepted"])
        self.assertTrue(failed["restored"])
        self.assertEqual(contract.container_id(release.LIVE_NAME), FULL_ID)
        self.assertTrue(contract.running(release.LIVE_NAME))

    def test_successful_swap_keeps_parked_container_and_deletes_env(self):
        docker = FakeDocker()
        result = self.swap(docker, FakeHttp())
        self.assertTrue(result["accepted"])
        self.assertTrue(result["envRemoved"])
        self.assertFalse(result["restored"])
        self.assertEqual(docker.container_id(release.LIVE_NAME), "new-container")
        self.assertEqual(docker.image_id(self.settings["parkedName"]), CURRENT_IMAGE_ID)
        self.assertFalse(docker.running(self.settings["parkedName"]))
        self.assertIn("coderzclub-api-before-batch-mgmt", docker.containers)
        self.assertEqual(docker.calls.count("remove"), 0)
        self.assertFalse(os.path.exists(os.path.join(self.directory.name, self.settings["envFilename"])))

    def test_rollback_removes_only_the_new_live_container(self):
        docker = FakeDocker()
        self.swap(docker, FakeHttp())
        outcome = release.rollback(docker, NEW_DIGEST, self.settings["parkedName"], CURRENT_IMAGE_ID)
        self.assertTrue(outcome["ok"])
        self.assertEqual(docker.removed, [release.LIVE_NAME])
        self.assertEqual(docker.container_id(release.LIVE_NAME), FULL_ID)
        self.assertTrue(docker.running(release.LIVE_NAME))
        self.assertIn("coderzclub-api-before-batch-mgmt", docker.containers)

    def test_rollback_stops_when_live_image_is_not_the_new_digest(self):
        docker = FakeDocker()
        docker.containers[self.settings["parkedName"]] = {
            "id": "parked-old", "running": False, "image": CURRENT_IMAGE_ID,
        }
        outcome = release.rollback(docker, NEW_DIGEST, self.settings["parkedName"], CURRENT_IMAGE_ID)
        self.assertFalse(outcome["ok"])
        self.assertEqual(docker.removed, [])
        self.assertEqual(docker.container_id(release.LIVE_NAME), FULL_ID)
        self.assertIn(self.settings["parkedName"], docker.containers)
        historical = release.rollback(docker, NEW_DIGEST, "coderzclub-api-before-batch-mgmt", CURRENT_IMAGE_ID)
        self.assertFalse(historical["ok"])
        self.assertEqual(docker.removed, [])
        self.assertIn("coderzclub-api-before-batch-mgmt", docker.containers)

    def test_workstation_and_mixed_flags_cannot_select_a_swap(self):
        ready = release_env(
            ENROLLMENT_BEARER="present-but-not-printed",
        )
        self.assertEqual(release.deployment_mode(ready, "DESKTOP-WORKSTATION")[0], "refuse")
        mixed = dict(ready)
        mixed["DRY_RUN"] = "yes"
        self.assertEqual(release.deployment_mode(mixed, release.EXPECTED_HOSTNAME)[0], "refuse")
        dry = release_env()
        dry.pop("CONFIRM_DEPLOY")
        dry["DRY_RUN"] = "yes"
        self.assertEqual(release.deployment_mode(dry, release.EXPECTED_HOSTNAME)[0], "dry-run")
        self.assertEqual(release.deployment_mode({"DRY_RUN": "yes", "EXPECTED_HOSTNAME": release.EXPECTED_HOSTNAME}, release.EXPECTED_HOSTNAME)[0], "refuse")
        rollback_env = release_env(ROLLBACK="yes")
        rollback_env.pop("ENROLLMENT_BEARER", None)
        self.assertEqual(release.deployment_mode(rollback_env, release.EXPECTED_HOSTNAME)[0], "rollback")
        self.assertEqual(release.deployment_mode(rollback_env, "DESKTOP-WORKSTATION")[0], "refuse")
        self.assertEqual(release.main(mixed, release.EXPECTED_HOSTNAME), 2)

    def test_parked_name_conflict_blocks_before_mutation(self):
        docker = FakeDocker()
        result = release.execute_swap(
            self.record,
            self.names + [self.settings["parkedName"]],
            docker,
            FakeHttp(),
            "MONGODB_URI=" + SECRET,
            self.directory.name,
            NEW_DIGEST,
            self.settings,
            require_enrollment=True,
        )
        self.assertFalse(result["mutated"])
        self.assertEqual(docker.calls, [])
        self.assertTrue(any(self.settings["parkedName"] in item for item in result["errors"]))

    def test_standard_oci_labels_are_accepted_and_not_copied(self):
        self.assertEqual(self.settings["errors"], [])
        self.assertEqual(release.unexpected_labels(self.record["labelKeys"]), [])
        self.assertEqual(release.preflight(self.record, self.names, NEW_DIGEST, self.settings), [])
        self.assertEqual(
            set(self.record["ociLabels"]),
            set(release.OCI_IMAGE_LABELS),
        )
        argv = release.create_argv(self.record, self.settings["image"], "/tmp/" + self.settings["envFilename"])
        self.assertNotIn(self.record["ociLabels"]["org.opencontainers.image.version"], "\n".join(argv))
        self.assertNotIn(self.record["ociLabels"]["org.opencontainers.image.created"], "\n".join(argv))

    def test_unexpected_labels_are_rejected_before_mutation(self):
        labeled = dict(self.record)
        labeled["labelKeys"] = list(self.record["labelKeys"]) + ["com.coderzclub.deployed-by"]
        docker = FakeDocker()
        result = release.execute_swap(
            labeled, self.names, docker, FakeHttp(), "MONGODB_URI=" + SECRET,
            self.directory.name, NEW_DIGEST, self.settings, require_enrollment=True,
        )
        self.assertFalse(result["mutated"])
        self.assertEqual(docker.calls, [])
        self.assertTrue(any("com.coderzclub.deployed-by" in item for item in result["errors"]))

    def test_release_a37c5a5_configuration_and_invalid_values(self):
        parsed = self.settings
        self.assertEqual(parsed["releaseId"], "a37c5a5")
        self.assertEqual(parsed["image"], "coderzclub-backend:api-a37c5a5")
        self.assertEqual(parsed["confirm"], "api-a37c5a5")
        self.assertEqual(parsed["parkedName"], "coderzclub-api-before-a37c5a5")
        self.assertEqual(parsed["envFilename"], "cz-api-a37c5a5.env")
        self.assertEqual(parsed["directoryName"], "cz-api-a37c5a5")
        self.assertEqual(parsed["currentImageId"], CURRENT_IMAGE_ID)
        missing = release.parse_release({}, True)
        self.assertTrue(missing["errors"])
        invalid = release.parse_release(release_env(
            RELEASE_ID="latest",
            NEW_IMAGE="coderzclub-backend:latest",
            CONFIRM_DEPLOY="api-aa1ea866bcd",
            ROLLBACK_CONTAINER="coderzclub-api",
        ), True)
        self.assertTrue(invalid["errors"])
        self.assertEqual(release.deployment_mode(release_env(CONFIRM_DEPLOY="api-aa1ea866bcd"), release.EXPECTED_HOSTNAME)[0], "refuse")
        self.assertEqual(release.deployment_mode(release_env(NEW_IMAGE="coderzclub-backend:batch-mgmt-20261017"), release.EXPECTED_HOSTNAME)[0], "refuse")

    def test_wrong_expected_image_digest_is_rejected(self):
        wrong = dict(self.settings)
        wrong["currentImageId"] = "sha256:" + ("d" * 64)
        wrong["errors"] = []
        docker = FakeDocker()
        result = release.execute_swap(
            self.record, self.names, docker, FakeHttp(), "MONGODB_URI=" + SECRET,
            self.directory.name, NEW_DIGEST, wrong, require_enrollment=True,
        )
        self.assertFalse(result["mutated"])
        self.assertEqual(docker.calls, [])
        self.assertTrue(any("expected digest" in item for item in result["errors"]))

    def test_swap_uses_immutable_image_and_checks_created_container(self):
        docker = FakeDocker()
        result = self.swap(docker, FakeHttp())
        self.assertIn(NEW_DIGEST, result["argv"])
        self.assertNotIn(self.settings["image"], result["argv"])
        wrong = FakeDocker()
        original_create = wrong.create
        def create_bad(argv):
            original_create(argv)
            wrong.containers[release.LIVE_NAME]["image"] = "sha256:" + "c" * 64
        wrong.create = create_bad
        failed = self.swap(wrong, FakeHttp())
        self.assertFalse(failed["ok"])
        self.assertTrue(failed["restored"])

    def test_rollback_rejects_wrong_parked_image_before_stopping_live(self):
        docker = FakeDocker()
        self.swap(docker, FakeHttp())
        docker.containers[self.settings["parkedName"]]["image"] = "sha256:" + "c" * 64
        calls = list(docker.calls)
        outcome = release.rollback(docker, NEW_DIGEST, self.settings["parkedName"], CURRENT_IMAGE_ID)
        self.assertFalse(outcome["ok"])
        self.assertEqual(docker.calls, calls)
        self.assertTrue(docker.running(release.LIVE_NAME))

    def test_probe_requires_dependency_health_not_only_readiness(self):
        for status, body in [(503, '{"status":"DOWN"}'), (200, '{"status":"DOWN"}'), (200, 'bad')]:
            with patch.object(release, "_http_get", side_effect=[
                (200, '{"status":"UP"}'), (200, "Problems endpoint is working"), (status, body)
            ]) as get:
                self.assertFalse(release.service_ready(*release.ApiProbe("").health()))
                self.assertEqual(get.call_count, 3)
        with patch.object(release, "_http_get", side_effect=[
            (200, '{"status":"UP"}'), (200, "Problems endpoint is working"), (200, '{"status":"UP"}')
        ]):
            self.assertTrue(release.service_ready(*release.ApiProbe("").health()))

    def test_dry_run_lists_full_preflight_failures_and_redacts_srv_uri(self):
        summary = release.dry_run_summary(self.record, self.names, self.settings, None)
        self.assertIn("new image is not loaded at the expected digest", summary["preflightErrors"])
        self.assertNotIn("mongodb+srv://", release.redact("mongodb+srv://user:secret@invalid", []))

    def test_restore_refuses_changed_backup_before_removing_new_container(self):
        docker = FakeDocker()
        create = docker.create
        def changed_backup(argv):
            create(argv)
            docker.containers[self.settings["parkedName"]]["id"] = "unexpected-container"
            raise RuntimeError("create response lost")
        docker.create = changed_backup
        result = self.swap(docker, FakeHttp())
        self.assertFalse(result["restored"])
        self.assertEqual(docker.removed, [])
        self.assertEqual(docker.container_id(release.LIVE_NAME), "new-container")

    def test_docker_config_stop_timeout_and_unlimited_swap_are_preserved(self):
        payload = inspect_payload()
        payload["Config"]["StopTimeout"] = 90
        payload["HostConfig"]["MemorySwap"] = -1
        record = release.safe_record(payload)
        self.assertEqual(record["stopTimeout"], 90)
        argv = release.create_argv(record, NEW_DIGEST, "/tmp/audit.env")
        self.assertIn("--stop-timeout=90", argv)
        self.assertIn("--memory-swap=-1", argv)

    def test_automatic_restore_reports_health_separately(self):
        result = self.swap(FakeDocker(fail="create"), FakeHttp())
        self.assertTrue(result["restored"])
        self.assertTrue(result["restoredHealthy"])
        result = self.swap(FakeDocker(fail="create"), FakeHttp(health=False))
        self.assertTrue(result["restored"])
        self.assertFalse(result["restoredHealthy"])

    def test_dry_run_reports_labels_without_docker_changes(self):
        docker = FakeDocker()
        summary = release.dry_run_summary(self.record, self.names, self.settings, None)
        self.assertEqual(docker.calls, [])
        self.assertEqual(summary["mutation"], "none")
        self.assertTrue(summary["parkedNameFree"])
        self.assertEqual(summary["releaseId"], "a37c5a5")
        self.assertEqual(summary["expectedCurrentImageId"], CURRENT_IMAGE_ID)
        for key in release.OCI_IMAGE_LABELS:
            self.assertIn(key, summary["labelKeys"])
            self.assertIn(key, summary["ociLabels"])
        self.assertEqual(summary["labelErrors"], [])
        self.assertNotIn(SECRET, json.dumps(summary))
        self.assertNotIn("MONGODB_URI=" + SECRET, json.dumps(summary))


if __name__ == "__main__":
    unittest.main()
