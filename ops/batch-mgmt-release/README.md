# Batch management release runbook

Release image: `coderzclub-backend:batch-mgmt-20261017`

Expected image id:

`sha256:5fdb39d431e09d729a6b8216b67368d9e727163d84d40eec056d854aa553f6e0`

Rollback image, which this procedure does not delete or retag:

`coderzclub-backend:62006997f25912159bc8ff589542a7d5364d0c33`

The API and worker use that same new image. `SPRING_PROFILES_ACTIVE` stays `api` on the API VM and `worker` on the worker VM. Do not start a VM, load an image, change MongoDB, or deploy until this release is approved.

The worker container name, image id, and port bindings must be read from the live VM. Older notes are not assumed to be current.

## Order

1. Load the verified image on `coderzclub-api-1` and `coderzclub-worker-1`.
2. Inspect both VMs. Stop if the inspect script reports any error.
3. Apply coordinator indexes.
4. Swap the API container and wait for readiness.
5. Swap the worker container and wait for readiness.
6. Deploy the Cloudflare Worker `coderzclub` from this worktree.
7. Run the smoke checks with dedicated `release-smoke-*` accounts.

## Load the image

Copy the checksummed archive to the VM, then:

```bash
gunzip -c coderzclub-backend-batch-mgmt-20261017.tar.gz | docker load
docker image inspect --format '{{.Id}}' coderzclub-backend:batch-mgmt-20261017
```

The printed id must equal the expected id above. Confirm the rollback tag still resolves to `sha256:cac46b8628dbc4646bd457b056f7713d8f03bd0dc2e5b28c210c1008e5390131`.

## Inspect a VM

On `coderzclub-api-1`:

```bash
export EXPECTED_HOSTNAME=coderzclub-api-1
export RELEASE_ROLE=api
export CONTAINER_NAME=coderzclub-api
export INSPECT_OUT=/root/batch-mgmt-api-inspect.json
python3 ops/batch-mgmt-release/inspect_host.py
```

On `coderzclub-worker-1`, set `EXPECTED_HOSTNAME=coderzclub-worker-1`, `RELEASE_ROLE=worker`, and `CONTAINER_NAME` from `docker ps`. Do not guess that name. The inspect file records the image id, restart policy, private port bindings, environment names, and an environment hash. It does not record secret values.

The API inspect fails unless the live environment still has:

```text
APP_CORS_ALLOWED_ORIGINS=https://coderzclub.site,https://coderzclub.coderzclub1.workers.dev
APP_FRONTEND_URL=https://coderzclub.site
```

Any public port binding, privileged container, unexpected restart policy, or hostname mismatch is a hard stop. Fix nothing automatically.

## Indexes

From a host that can reach Atlas, with `MONGODB_URI` only in the environment:

```bash
export CONFIRM_INDEXES=batch-mgmt-20261017
python3 ops/batch-mgmt-release/apply_coordinator_indexes.py
```

The check script runs first. It creates indexes only when `batch_coordinators` or one of the two indexes is absent. Duplicate keys or a non-unique index on `{batchId, userId}` stop the script. Do not run `backend/mongodb-indexes.js` for this release.

## Swap one container

Re-run inspect immediately before the swap. Then, on that VM only:

```bash
export CONFIRM_DEPLOY=batch-mgmt-20261017
export INSPECT_FILE=/root/batch-mgmt-api-inspect.json
python3 ops/batch-mgmt-release/swap_container.py
```

The swap stops if the live container id, image id, ports, mounts, environment hash, or CORS values differ from the inspect file. It renames the current container to `<name>-before-batch-mgmt`, starts the release image with the same private bindings and environment, and keeps the old container. If readiness does not pass within 120 seconds, it removes the new container and starts the old one under the original name.

API readiness is `http://<private-bind>/api/problems/test` plus `/actuator/health/readiness`. The worker gate is readiness on the management binding recorded from the live container.

Leave the named tunnel on `http://10.20.0.4:8080`. Do not change RabbitMQ, Redis, Atlas, or Judge0 settings.

## Frontend

After the API is ready, from this worktree:

```bash
npx wrangler deploy
```

The Worker name in `wrangler.jsonc` is `coderzclub`. The production `dist/` build already targets `https://api.coderzclub.site`. Deploying it before the API swap would send the new batch pages to the previous API.

## Smoke

Create dedicated accounts whose usernames start with `release-smoke-`: one admin, one student, and two coordinators. Then:

```bash
export SMOKE_ADMIN_USERNAME=release-smoke-admin
export SMOKE_ADMIN_PASSWORD=...
export SMOKE_STUDENT_USERNAME=release-smoke-student
export SMOKE_STUDENT_PASSWORD=...
export SMOKE_COORD_A_USERNAME=release-smoke-coord-a
export SMOKE_COORD_A_PASSWORD=...
export SMOKE_COORD_B_USERNAME=release-smoke-coord-b
export SMOKE_PROBLEM_ID=<dedicated practice problem>
export SMOKE_PYTHON_SOURCE="$(cat accepted-python-solution.py)"
export SMOKE_ALLOW_JUDGE0=yes
python3 ops/batch-mgmt-release/post_deploy_smoke.py
```

The script checks CORS, public readiness, admin login, add, duplicate add, import, membership removal without deleting the user, assignment add and remove, report JSON, CSV, coordinator isolation, revocation, a public Judge0 run, and a queued submission that reaches `ACCEPTED`. It archives the smoke batches. Finish with the browser editor steps printed by the script: Java marker, Python starter, return to Java, refresh on Python.

Leave `SMOKE_ALLOW_JUDGE0` unset to skip real execution.

## Rollback

1. `npx wrangler deployments list`, then `npx wrangler rollback <previous-version-id>`. Wrangler 4.151.1 accepts that form. Confirm the version id before rolling back.
2. On the worker VM, stop the new container and start `<worker-name>-before-batch-mgmt` under the original name. Keep its recorded environment and private bindings.
3. Do the same on the API VM. The previous API image is `coderzclub-backend:62006997f25912159bc8ff589542a7d5364d0c33`. Preserve the two CORS values above.
4. Leave `batch_coordinators` and its indexes in place. The previous application does not read that collection.

Expected API pause while its port moves: one to two minutes. Worker replacement pauses judging for about the same time; RabbitMQ keeps queued jobs.
