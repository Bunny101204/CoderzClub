import unittest

import swap_safety as safety


class SwapSafetyTest(unittest.TestCase):
    def test_worker_ports_ignore_api_server_port(self):
        self.assertEqual(safety.service_ports("worker"), safety.WORKER_BINDINGS)
        self.assertEqual(safety.service_ports("api")[0][2], "8080")

    def test_worker_binding_mismatch_is_reported(self):
        errors = safety.binding_errors("worker", [
            {"containerPort": "8080/tcp", "hostIp": "10.20.0.4", "hostPort": "8080"},
        ])
        self.assertEqual(len(errors), 2)
        self.assertIn("8082", errors[0])

    def test_readiness_requires_top_level_status(self):
        self.assertTrue(safety.ready_body('{"status":"UP","components":{"rabbit":{"status":"UP"}}}'))
        self.assertFalse(safety.ready_body('{"status":"DOWN","components":{"rabbit":{"status":"UP"}}}'))
        self.assertFalse(safety.ready_body("UP"))

    def test_consumer_evidence_does_not_match_dlq_or_secrets(self):
        secret = "mongodb://user:secret@atlas"
        log = (
            "Started submission worker fleet with concurrency=2\n"
            f"Started on queue 'coderzclub.submissions.dlq' {secret}\n"
            "Started on queue 'coderzclub.submissions'\n"
            "Started contest run worker concurrency=1\n"
            "Started on queue 'coderzclub.contest-runs'\n"
        )
        self.assertEqual(safety.consumer_gaps(log, "coderzclub.submissions", "coderzclub.contest-runs"), [])
        gaps = safety.consumer_gaps(log, "coderzclub.submissions", "missing.queue")
        self.assertEqual(gaps, ["contest-run"])
        self.assertNotIn(secret, " ".join(gaps))

    def test_queues_must_be_distinct_and_dry_run_excludes_confirm(self):
        _, _, errors = safety.queue_names({
            "SUBMISSION_QUEUE_NAME": "coderzclub.submissions",
            "CONTEST_RUN_QUEUE_NAME": "coderzclub.submissions",
        })
        self.assertTrue(errors)
        self.assertIsNone(safety.mode_error({"DRY_RUN": "yes"}))
        self.assertIn("mutually exclusive", safety.mode_error({
            "DRY_RUN": "yes",
            "CONFIRM_DEPLOY": safety.CONFIRM,
        }))
        self.assertIn("CONFIRM_DEPLOY", safety.mode_error({}))

    def test_handoff_and_historical_containers_are_protected(self):
        self.assertTrue(safety.handoff_blocked(True))
        self.assertFalse(safety.handoff_blocked(False))
        self.assertFalse(safety.removal_allowed(
            "coderzclub-api-before-cors", "new", "parked", "coderzclub-api-before-cors"
        ))
        self.assertFalse(safety.removal_allowed(
            "coderzclub-worker-before-batch-mgmt", "new", "parked", "coderzclub-worker"
        ))
        self.assertTrue(safety.removal_allowed("coderzclub-worker", "new", "parked", "coderzclub-worker"))
        self.assertFalse(safety.removal_allowed("coderzclub-worker", "parked", "parked", "coderzclub-worker"))

    def test_create_command_preserves_runtime_and_refuses_incomplete_inspect(self):
        command, errors = safety.create_command({
            "name": "coderzclub-worker",
            "network": "bridge",
            "user": "10001",
            "readonlyRootfs": True,
            "capDrop": ["ALL"],
            "securityOpt": ["no-new-privileges"],
            "stopTimeout": 10,
            "bindings": [{"containerPort": "8082/tcp", "hostIp": "10.20.0.7", "hostPort": "8082"}],
            "binds": [],
            "entrypoint": ["java", "-jar", "/app/app.jar"],
            "cmd": [],
        }, "coderzclub-backend:batch-mgmt-20261017")
        self.assertEqual(errors, [])
        self.assertIn("--read-only", command)
        self.assertIn("--cap-drop", command)
        self.assertIn("10001", command)
        self.assertIn("10.20.0.7:8082:8082", command)
        self.assertNotIn("/api/problems/test", command)
        missing, missing_errors = safety.create_command({"name": "coderzclub-worker"}, "image")
        self.assertEqual(missing, [])
        self.assertTrue(any("readonlyRootfs" in item for item in missing_errors))


if __name__ == "__main__":
    unittest.main()
