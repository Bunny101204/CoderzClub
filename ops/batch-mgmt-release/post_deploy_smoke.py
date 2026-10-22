#!/usr/bin/env python3
"""Post-deploy smoke checks against dedicated release-smoke accounts.

The script creates one batch named release-smoke-<timestamp>, changes only that
batch, and archives it. It refuses usernames that do not start with
release-smoke-. Credentials come from the environment and are not stored.
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

API = os.environ.get("SMOKE_API_BASE", "https://api.coderzclub.site").rstrip("/")
ORIGIN = "https://coderzclub.site"
PREFIX = "release-smoke-"


class SmokeFailure(Exception):
    pass


def request(method, path, body=None, token=None, origin=None, accept="application/json"):
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Accept": accept}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if origin:
        headers["Origin"] = origin
    req = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            payload = response.read()
            return response.status, dict(response.headers), payload
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read()


def json_body(status, payload, expected=200):
    if status != expected:
        raise SmokeFailure(f"HTTP {status}: {payload[:300]!r}")
    if not payload:
        return {}
    return json.loads(payload.decode("utf-8"))


def require_account(name):
    value = os.environ.get(name, "")
    if not value.startswith(PREFIX):
        raise SmokeFailure(f"{name} must be a dedicated {PREFIX}* account")
    return value


def login(username, password):
    status, _, payload = request("POST", "/api/login", {"username": username, "password": password})
    body = json_body(status, payload)
    if not body.get("token"):
        raise SmokeFailure(f"login for {username} did not return a token")
    return body["token"]


def cors():
    status, headers, _ = request("OPTIONS", "/api/login", origin=ORIGIN)
    allow = headers.get("Access-Control-Allow-Origin") or headers.get("access-control-allow-origin")
    if status not in {200, 204} or allow != ORIGIN:
        raise SmokeFailure(f"CORS preflight returned {status} allow={allow}")
    print("CORS preflight allows https://coderzclub.site")


def readiness():
    status, _, payload = request("GET", "/api/problems/test", accept="text/plain")
    if status != 200 or b"Problems endpoint is working" not in payload:
        raise SmokeFailure(f"API readiness returned {status}")
    print("Public API readiness passed")


def user_id(token, username):
    status, _, payload = request(
        "GET",
        f"/api/admin/batches/users?search={username}&page=0&size=20",
        token=token,
    )
    body = json_body(status, payload)
    matches = [user for user in body.get("users", []) if user.get("username") == username]
    if len(matches) != 1:
        raise SmokeFailure(f"expected one registered user named {username}")
    return matches[0]["id"]


def batch_flow(admin):
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
    name = f"{PREFIX}{stamp}"
    status, _, payload = request("POST", "/api/admin/batches", {"name": name, "description": "release smoke"}, token=admin)
    batch = json_body(status, payload)
    batch_id = batch["id"]
    print(f"Created {name} {batch_id}")
    student = require_account("SMOKE_STUDENT_USERNAME")
    student_id = user_id(admin, student)
    status, _, payload = request(
        "POST", f"/api/admin/batches/{batch_id}/members", {"userIds": [student_id]}, token=admin
    )
    added = json_body(status, payload)
    if student_id not in {member.get("userId") for member in added.get("members", [])}:
        raise SmokeFailure("student add did not return the new membership")
    status, _, payload = request(
        "POST", f"/api/admin/batches/{batch_id}/members", {"userIds": [student_id]}, token=admin
    )
    duplicate = json_body(status, payload)
    if student_id not in duplicate.get("alreadyMemberIds", []):
        raise SmokeFailure("duplicate membership was not reported")
    status, _, payload = request(
        "POST",
        f"/api/admin/batches/{batch_id}/members/import",
        {"usernames": [student, f"{PREFIX}missing"]},
        token=admin,
    )
    imported = json_body(status, payload)
    if student not in imported.get("alreadyMembers", []) or f"{PREFIX}missing" not in imported.get("notFound", []):
        raise SmokeFailure("bulk import did not separate the existing and missing identifiers")
    status, _, payload = request("DELETE", f"/api/admin/batches/{batch_id}/members/{student_id}", token=admin)
    json_body(status, payload)
    status, _, payload = request("GET", f"/api/admin/batches/users?search={student}&batchId={batch_id}&page=0&size=20", token=admin)
    after = json_body(status, payload)
    still = [user for user in after.get("users", []) if user.get("id") == student_id]
    if not still or still[0].get("alreadyMember") is True:
        raise SmokeFailure("removing the batch membership removed or hid the user account")
    problem_id = os.environ.get("SMOKE_PROBLEM_ID", "")
    if problem_id:
        status, _, payload = request(
            "POST", f"/api/admin/batches/{batch_id}/assignments", {"problemIds": [problem_id]}, token=admin
        )
        json_body(status, payload)
        status, _, payload = request("GET", f"/api/admin/batches/{batch_id}/assignments", token=admin)
        listed = json_body(status, payload)
        if problem_id not in {item.get("problemId") for item in listed.get("assignments", [])}:
            raise SmokeFailure("assignment was not listed")
        status, _, payload = request(
            "DELETE", f"/api/admin/batches/{batch_id}/assignments/{problem_id}", token=admin
        )
        json_body(status, payload)
        print("Assignment add and remove passed")
    status, _, payload = request("GET", f"/api/admin/batches/{batch_id}/report?page=0&size=20", token=admin)
    report = json_body(status, payload)
    if "summary" not in report:
        raise SmokeFailure("report did not include a summary")
    status, headers, payload = request(
        "GET", f"/api/admin/batches/{batch_id}/report.csv", token=admin, accept="text/csv"
    )
    if status != 200 or b"username" not in payload.lower():
        raise SmokeFailure(f"CSV export returned {status}")
    print("Report and CSV passed")
    return batch_id


def isolation(admin, batch_a):
    coord_a = require_account("SMOKE_COORD_A_USERNAME")
    coord_b = require_account("SMOKE_COORD_B_USERNAME")
    batch_b_status, _, batch_b_payload = request(
        "POST", "/api/admin/batches",
        {"name": f"{PREFIX}other-{time.time_ns()}", "description": "isolation"},
        token=admin,
    )
    batch_b = json_body(batch_b_status, batch_b_payload)["id"]
    for batch_id, username, institution in (
        (batch_a, coord_a, "North"),
        (batch_b, coord_b, "South"),
    ):
        user = user_id(admin, username)
        status, _, payload = request(
            "POST",
            f"/api/admin/batches/{batch_id}/coordinators",
            {"userId": user, "institution": institution},
            token=admin,
        )
        json_body(status, payload)
    token_a = login(coord_a, os.environ["SMOKE_COORD_A_PASSWORD"])
    status, _, _ = request("GET", f"/api/admin/batches/{batch_a}", token=token_a)
    if status != 200:
        raise SmokeFailure("coordinator A could not read batch A")
    for path in (
        f"/api/admin/batches/{batch_b}",
        f"/api/admin/batches/{batch_b}/report",
        f"/api/admin/batches/{batch_b}/report.csv",
        "/api/admin/batches",
        "/api/admin/contests",
    ):
        status, _, _ = request("GET", path, token=token_a)
        if status != 403:
            raise SmokeFailure(f"coordinator A received {status} for {path}")
    status, _, _ = request(
        "POST", f"/api/admin/batches/{batch_a}/coordinators", {"userId": "other", "institution": "North"}, token=token_a
    )
    if status != 403:
        raise SmokeFailure("coordinator A was able to grant a coordinator")
    admin_user = user_id(admin, coord_a)
    status, _, _ = request("DELETE", f"/api/admin/batches/{batch_a}/coordinators/{admin_user}", token=admin)
    if status != 200:
        raise SmokeFailure("admin could not revoke coordinator A")
    status, _, _ = request("GET", f"/api/admin/batches/{batch_a}", token=token_a)
    if status != 403:
        raise SmokeFailure("revoked coordinator A can still read batch A")
    print("Coordinator isolation passed")
    request("POST", f"/api/admin/batches/{batch_b}/archive", token=admin)
    return batch_b


def judge0(token):
    source = os.environ.get("SMOKE_PYTHON_SOURCE", "")
    problem = os.environ.get("SMOKE_PROBLEM_ID", "")
    if not source or not problem:
        raise SmokeFailure("Set SMOKE_PROBLEM_ID and SMOKE_PYTHON_SOURCE for the Judge0 checks")
    status, _, payload = request(
        "POST",
        f"/api/problems/{problem}/run-public",
        {"source_code": source, "language_id": 71},
        token=token,
    )
    run = json_body(status, payload)
    if "results" not in run:
        raise SmokeFailure("public run did not return results")
    print("Judge0 public run returned results")
    status, _, payload = request(
        "POST",
        "/api/submission-jobs",
        {"problemId": problem, "code": source, "language": "Python", "languageId": 71, "codingDurationSeconds": 1},
        token=token,
    )
    job = json_body(status, payload)
    job_id = job.get("jobId")
    if not job_id:
        raise SmokeFailure("submission job was not queued")
    deadline = time.time() + 180
    last = {}
    while time.time() < deadline:
        status, _, payload = request("GET", f"/api/submission-jobs/{job_id}", token=token)
        last = json_body(status, payload)
        if last.get("status") in {"COMPLETED", "FAILED", "CANCELLED"}:
            break
        time.sleep(3)
    else:
        raise SmokeFailure("worker did not finish the queued submission within 180 seconds")
    if last.get("status") != "COMPLETED" or last.get("finalResult") != "ACCEPTED":
        raise SmokeFailure(f"worker finished with {last.get('status')} {last.get('finalResult')}")
    print(f"Worker consumed submission job {job_id} and scored ACCEPTED")


def editor_instructions():
    print("Browser editor check, using the release-smoke student on https://coderzclub.site:")
    print("1. Open one practice problem and confirm the editor starts on Java, language id 62.")
    print("2. Type a Java-only marker, switch to Python, and confirm the Python starter appears.")
    print("3. Type a Python marker, switch back to Java, and confirm the Java marker is still there.")
    print("4. Return to Python, refresh the page, and confirm the Python marker remains.")
    print("5. Run and submit from that problem only if SMOKE_PYTHON_SOURCE is the known accepted solution.")


def main():
    try:
        admin_user = require_account("SMOKE_ADMIN_USERNAME")
        cors()
        readiness()
        admin = login(admin_user, os.environ["SMOKE_ADMIN_PASSWORD"])
        print("Admin login passed")
        batch_id = batch_flow(admin)
        isolation(admin, batch_id)
        if os.environ.get("SMOKE_ALLOW_JUDGE0") == "yes":
            student = login(require_account("SMOKE_STUDENT_USERNAME"), os.environ["SMOKE_STUDENT_PASSWORD"])
            judge0(student)
        else:
            print("Judge0 and worker scoring were not requested. Set SMOKE_ALLOW_JUDGE0=yes to run them.")
        request("POST", f"/api/admin/batches/{batch_id}/archive", token=admin)
        print(f"Archived smoke batch {batch_id}")
        editor_instructions()
    except KeyError as missing:
        print(f"Missing environment value {missing}", file=sys.stderr)
        return 2
    except (SmokeFailure, urllib.error.URLError, json.JSONDecodeError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
