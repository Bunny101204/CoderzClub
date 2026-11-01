# Submission reliability release: a37c5a5

This is a proposed, gated runbook, not authorization to deploy. No production commands were executed during the local audit. Every production phase requires the owner's explicit approval. Stay on main; do not use the October batch-management swap script for this release.

## Identity and local evidence

- Application commit: `a37c5a54c02277b6a1332dc3548e94a3bf2d66d9`.
- Backend currently reported in production: `814037d`; its actual live image ID must be independently recorded, not guessed from that tag.
- Built directly from `git archive` of the application commit with the committed Dockerfile, not a working-tree JAR or `.env`.
- Local tag: `coderzclub-backend:api-a37c5a5` (the same image runs the worker profile).
- Verified local image ID: `sha256:54362a13396baeadf3e47d238af88e60638b73ab75c84f29e168947a059e1cff`; Linux amd64, user 10001, entrypoint `java -jar /app/app.jar`.
- Base tags in the Dockerfile are mutable. A rebuild may have a different image ID; repeat image validation and approve the new manifest. Do not silently substitute a different ID.
- API and worker both started in a new internal-only Docker network with a new Mongo replica set, Redis and RabbitMQ. Both aggregate health endpoints reported UP.
- Real HTTP admission, replay, outbox, Rabbit delivery, worker and result persistence worked. The available local Judge0 1.13.1 API responded, but Java sandbox execution failed on this host's missing cgroup-v1 memory layout. A non-cgroup attempt also failed compilation. This is NOT a verified successful compiler run.
- Detailed logs are retained under `backend/target/release-audit-a37c5a5/` (ignored; do not commit).

The release helper changes in this audit are NOT part of a37c5a5. Review and checksum the exact helper files separately before transferring them. No application Java or frontend source was changed by this audit.

## Required approval gates

Do not swap containers until these gates have passed:

1. Read-only production inventory confirms the API and worker hostnames, actual container/image IDs, private bindings, environment hashes, architecture, disk/memory headroom and preserved rollback containers. Worker container name is not assumed from old notes.
2. Required Mongo definitions and effective application privileges are verified read-only using the script below. Review existing pending jobs, published-but-unprocessed events and queue backlog.
3. API and worker point to the same Mongo database, Rabbit vhost/exchanges/queues and Redis database/channel. Never print URI/password/token values in audit output.
4. A known-working nonproduction Judge0 environment completes an accepted Java submission with reward persistence, or the owner explicitly accepts this outstanding validation risk. No local sandbox privilege/host-cgroup changes were made to force a pass.
5. The worker's concrete create command, generated from its fresh inventory below, is reviewed. The old helper is hardcoded to the October image and also relies on worker log strings that are not a reliable current readiness test.
6. The exact frontend rollback version and current automatic-build state are recorded through separately approved read-only Cloudflare checks. Automatic builds were reported disabled; this audit did not query Cloudflare.

Current verdict is NOT READY until these outstanding gates have evidence or explicit risk acceptance. Successful local health does not prove production permissions, bindings or compiler availability.

## Read-only production checks (later, separately authorized)

Run on the appropriate already-authorized host. Use a restricted audit directory and the reviewed helper files. These commands do not restart containers or print environment values.

```bash
set -euo pipefail
umask 077
export LIVE_CONTAINER=coderzclub-api   # On worker: set the actual name from docker ps.
docker ps --format '{{.Names}} {{.Image}} {{.Status}}'
python3 - <<'PY'
import importlib.util,json,os,subprocess
spec=importlib.util.spec_from_file_location('release','ops/api-aa1ea86-release/api_release.py')
r=importlib.util.module_from_spec(spec); spec.loader.exec_module(r)
raw=json.loads(subprocess.check_output(['docker','inspect',os.environ['LIVE_CONTAINER']],text=True))[0]
record=r.safe_record(raw)
with open('approved-'+os.environ['LIVE_CONTAINER']+'.json','x') as f: json.dump(record,f,indent=2)
print(json.dumps(record,indent=2))
PY
```

Record both containers' `.Image` values from this output. Keep the inventory files private and review command/mount fields before sharing. Confirm API host `coderzclub-api-1`, bridge network, `10.20.0.4:8080/8081`; worker notes expect `coderzclub-worker-1`, `10.20.0.7:8082/8083` but require verification. Preserve environment values exactly during the swap.

Read-only service checks on the corresponding private hosts:

```bash
curl --fail --silent --show-error --max-time 10 http://10.20.0.4:8081/actuator/health/readiness
curl --fail --silent --show-error --max-time 10 http://10.20.0.4:8081/actuator/health
curl --fail --silent --show-error --max-time 10 http://10.20.0.4:8080/api/problems/test
# On the worker host:
curl --fail --silent --show-error --max-time 10 http://10.20.0.7:8083/actuator/health/readiness
curl --fail --silent --show-error --max-time 10 http://10.20.0.7:8083/actuator/health
```

On the authorized Rabbit host, with its existing administrative access (do not change users, permissions or definitions):

```bash
rabbitmqctl list_queues -p "$APP_RABBIT_VHOST" name durable arguments messages_ready messages_unacknowledged consumers
rabbitmqctl list_bindings -p "$APP_RABBIT_VHOST" source_name destination_name destination_kind routing_key
rabbitmqctl list_consumers -p "$APP_RABBIT_VHOST"
rabbitmqctl list_permissions -p "$APP_RABBIT_VHOST"
```

Expected submission queue: durable, max length 10000, overflow reject-publish, same exchange and DLQ routing as the live config. Default consumers: submission 2, contest-run 1 per worker JVM. Retry queues default to 5000 ms TTL and route back to the corresponding main queue. Both roles need Rabbit configure/write/read privileges for declared resources; credentials and vhost must match. Verify Redis PING through existing secure authentication and compare API/worker database index and pub/sub channel. Do not use KEYS, FLUSH*, purge queues, or change queue arguments.

### MongoDB (read-only only)

The verifier performs hello, listCollections/listIndexes, connectionStatus and a read-only snapshot transaction followed by abort. It never creates collections/indexes or changes data. Select the actual application database, not admin. Use an existing secure mongosh connection/profile; do not put credentials in shell history. With MONGODB_URI securely present only in the process environment:

```bash
mongosh --nodb --quiet --eval 'db = connect(process.env.MONGODB_URI); load("ops/release/verify-submission-mongo.js")'
```

Review warnings as well as failures. App config defaults the database to `coderzclub`, which can override the URI database: verify `SPRING_DATA_MONGODB_DATABASE` and select the actual database in the verification connection. Required unique/partial/TTL definitions are encoded in the verifier. Do not run `backend/mongodb-indexes.js` as a check: it creates AND drops indexes. Missing/conflicting definitions require a separate migration proposal and approval; do not weaken uniqueness or change TTLs to get startup green.

Review effective privileges for each API/worker identity: `find`, `insert`, `update`, `remove` as used by the application, plus `listCollections`, `listIndexes`; startup auto-index creation may need `createCollection`/`createIndex`. The idempotency initializer can accept existing matching definitions without createIndex privileges, but this does not waive other Spring startup index requirements. Replica-set primary and sessions are required: current topology detection uses setName and does not support treating mongos as a replica set. There is no separate blanket "transaction permission" that replaces collection permissions. Privilege inspection and a read-only transaction cannot prove write privileges without authorized writes.

Inspect counts and ages only, with bounded execution; avoid printing student source or error payloads:

```javascript
// In the already-authorized mongosh application database session:
db.submission_jobs.aggregate([
  {$group:{_id:"$status",count:{$sum:1},oldest:{$min:"$createdAt"}}}
],{maxTimeMS:10000}).toArray();
db.submission_outbox.aggregate([
  {$group:{_id:"$status",count:{$sum:1},oldest:{$min:"$createdAt"}}}
],{maxTimeMS:10000}).toArray();
db.submission_jobs.find({status:"RUNNING",lockedUntil:{$lte:new Date()}},
  {_id:1,status:1,lockedUntil:1,attemptCount:1}).limit(20).maxTimeMS(5000).toArray();
db.submission_jobs.find({status:"QUEUED",createdAt:{$lt:new Date(Date.now()-300000)}},
  {_id:1,status:1,createdAt:1}).limit(20).maxTimeMS(5000).toArray();
// For each approved sampled job ID, inspect only event state:
db.submission_outbox.find({aggregateId:"<sampled-job-id>"},
  {_id:1,status:1,publishedAt:1,lockedUntil:1,nextAttemptAt:1,attemptCount:1}).maxTimeMS(5000).toArray();
```

A QUEUED job with a PUBLISHED event and no corresponding broker work may be a historical lost delivery. The new return check prevents future false success; it does not reset existing PUBLISHED events. Orphan repair only inserts a missing event. Requeue/reconciliation of historical rows requires a separate reviewed operation; never reset all outbox rows or purge claims.

## Environment and compatibility

- Required: MONGODB_URI, JWT_SECRET, Rabbit host/port/user/password/vhost, effective Spring Data Redis host/port/password/database, profiles, Judge0 URL/auth, CORS and frontend URL. Preserve values and compare hashes without printing credentials.
- Boot 3 binds `spring.data.redis.*`. Use `SPRING_DATA_REDIS_HOST/PORT/PASSWORD`, especially when the service is not on localhost. Legacy `SPRING_REDIS_HOST` alone is insufficient. The production Compose file now uses the correct host key and requires an explicit Judge0 URL for BOTH API Run requests and worker submissions.
- Judge0 URL must end in `/submissions`; wait=true. Keep compile + CPU + slack (default 105s) below HTTP timeout (120s), below worker lease (180s); heartbeat 45s. Do not repoint production to the local audit service.
- API and worker both run OutboxPublisher because it is `!local`, not API-only. Worker also emits SSE summaries. Update both JVMs; an old publisher can still incorrectly mark returned events as PUBLISHED.
- Between 814037d and a37c5a5, job/outbox models, enum values, queue payload (job ID), queue definitions, consumer classes and index definitions did not change. No data rewrite or new schema migration is introduced. Existing pending/failed events and queued/running/retrying jobs remain readable. Keep all indexes/data when rolling back.
- Existing leases and unique job/submission/solved-record indexes protect redelivery. Drain in-flight work when possible; a killed worker may take lease expiry/retry time to recover. Do not start old and new workers simultaneously during handoff.
- Readiness UP alone is insufficient: locally it stayed UP while Redis/Rabbit was unavailable. Require aggregate health and actual consumers, then an explicitly authorized dedicated-account submission/replay/reward smoke check.

## Recommended order

1. Approve read-only inventory, Mongo/queue checks and exact image/helper/frontend manifests. Resolve the compiler gap and worker runtime plan.
2. Separately authorize artifact transfer/load on both hosts; verify IDs/checksums before any stop.
3. Prefer a controlled submission maintenance window. Drain running/unacknowledged jobs without purging queued work. Update worker first, verify dependency health and both consumers; then API. Mixed wire formats are compatible, but the old publisher remains defective during the interval, so minimize it and keep routes unchanged.
4. API helper requires private dependency/readiness checks plus an authenticated enrolled-student read-only contract check. Verify submission status/history/SSE and a separately approved real submission/replay smoke test.
5. Separately deploy frontend assets after both backend roles pass. Observe before reopening normal submission traffic.
6. If regression occurs, stop the rollout. Roll back frontend if deployed, then API and worker as required; keep database, queue definitions, messages and indexes intact.

## Exact proposed API commands (ONLY after separate approval)

On the workstation, retain the already tested image; building again changes the artifact being approved:

```powershell
$releaseImage = 'coderzclub-backend:api-a37c5a5'
$expectedImage = 'sha256:54362a13396baeadf3e47d238af88e60638b73ab75c84f29e168947a059e1cff'
if ((docker image inspect --format '{{.Id}}' $releaseImage) -ne $expectedImage) { throw 'Image identity changed' }
docker save --output backend/target/release-audit-a37c5a5/backend-a37c5a5.tar $releaseImage
Get-FileHash backend/target/release-audit-a37c5a5/backend-a37c5a5.tar -Algorithm SHA256
Get-FileHash ops/api-aa1ea86-release/api_release.py -Algorithm SHA256
```

Transfer only the approved archive, reviewed helper(s), verifier and runbook using the separately approved transfer method. No registry push is required by this plan. On each authorized VM verify the recorded archive/helper SHA256 first, then (this loads an image but does not deploy it):

```bash
sha256sum -c approved-artifact-checksums.txt
docker load --input backend-a37c5a5.tar
test "$(docker image inspect --format '{{.Id}}' coderzclub-backend:api-a37c5a5)" =   'sha256:54362a13396baeadf3e47d238af88e60638b73ab75c84f29e168947a059e1cff'
```

If a different Docker image-store representation changes the inspect ID, STOP and reconcile the archive manifest/config digest; do not accept a mismatch automatically.

On the API VM, set EXPECTED_CURRENT_IMAGE_ID to the independently approved LIVE 814037d image ID, not the old October constant and not an unreviewed dynamic lookup. ENROLLMENT_BEARER must be securely supplied for a known enrolled student; ENROLLED_BATCH_NAME must be its expected batch.

```bash
set -euo pipefail
export RELEASE_ID=a37c5a5
export EXPECTED_HOSTNAME=coderzclub-api-1
export EXPECTED_NEW_IMAGE_ID=sha256:54362a13396baeadf3e47d238af88e60638b73ab75c84f29e168947a059e1cff
: "${EXPECTED_CURRENT_IMAGE_ID:?set independently approved live image ID}"
: "${ENROLLED_BATCH_NAME:?set reviewed expected enrolled batch name}"
unset CONFIRM_DEPLOY ROLLBACK
DRY_RUN=yes python3 ops/api-aa1ea86-release/api_release.py
```

Review zero preflight errors and preservation gaps. After explicit approval of the swap:

```bash
unset DRY_RUN ROLLBACK
: "${ENROLLMENT_BEARER:?supply token securely without printing it}"
CONFIRM_DEPLOY=api-a37c5a5 python3 ops/api-aa1ea86-release/api_release.py
```

The helper pins creation to the verified image ID, checks the created container ID, preserves runtime settings/environment and retains `coderzclub-api-before-a37c5a5`. Failure restores that same container and reports its health separately. The stop/start handoff causes a short API outage. Never delete historical backups to free a conflicting name. Run as the existing authorized Docker operator; do not use sudo in a way that silently drops required environment variables.

API rollback after separate authorization, in the same shell with approved image IDs:

```bash
unset DRY_RUN
ROLLBACK=yes CONFIRM_DEPLOY=api-a37c5a5 python3 ops/api-aa1ea86-release/api_release.py
```

Rollback refuses if the current image is not the new image or the parked image is not the approved previous image. It checks readiness/dependency health after restoration. No Mongo rollback is part of this operation.

## Worker handoff and rollback (inventory-gated)

Do NOT use `ops/batch-mgmt-release/swap_container.py`: its image, confirmation and parked name are for the October release. The exact worker create command must be generated from the newly approved inventory, including every mount, capability, resource limit, private binding and environment hash. The existing API helper's `safe_record`, `create_argv` and `preservation_gaps` can generate that command without performing a swap, but its API-only preflight/main must not be pointed at a worker.

The worker's actual inventory was not available under this task's production-access restriction. Therefore a complete verified worker create command is an explicit remaining blocker; no guessed `docker run` command is provided. After the concrete command has been reviewed, the handoff must rename the previous container to `<actual-worker-name>-before-a37c5a5`, stop it (allow at least the reviewed execution/drain budget), create the new container by immutable image ID with its exact saved environment/runtime settings, and verify both health endpoints and actual Rabbit consumers. Preserve the old container. A failed new container can be parked separately for investigation rather than deleted.

The following rollback commands are concrete but deliberately require the names/digests from that reviewed worker plan. They stop before mutation if any prerequisite is missing or mismatched:

```bash
set -euo pipefail
: "${WORKER_CONTAINER:?actual reviewed live worker name}"
: "${WORKER_OLD_IMAGE_ID:?approved previous worker image ID}"
: "${EXPECTED_NEW_IMAGE_ID:?approved new image ID}"
PARKED_WORKER="${WORKER_CONTAINER}-before-a37c5a5"
FAILED_WORKER="${WORKER_CONTAINER}-failed-a37c5a5"
test "$(hostname)" = coderzclub-worker-1
test "$(docker inspect --format '{{.Image}}' "$WORKER_CONTAINER")" = "$EXPECTED_NEW_IMAGE_ID"
test "$(docker inspect --format '{{.Image}}' "$PARKED_WORKER")" = "$WORKER_OLD_IMAGE_ID"
if docker container inspect "$FAILED_WORKER" >/dev/null 2>&1; then echo 'Failed-container backup name already exists'; exit 1; fi
docker stop --time 150 "$WORKER_CONTAINER"
docker rename "$WORKER_CONTAINER" "$FAILED_WORKER"
docker start "$PARKED_WORKER"
docker rename "$PARKED_WORKER" "$WORKER_CONTAINER"
curl --fail --silent --show-error --retry 40 --retry-connrefused --retry-delay 3 --max-time 10 http://10.20.0.7:8083/actuator/health
curl --fail --silent --show-error --max-time 10 http://10.20.0.7:8083/actuator/health/readiness
# Recheck actual consumers on the Rabbit host before calling rollback complete.
```

## Separate frontend release

Yes, frontend deployment is required for retry-key recovery, error filtering, Java starters and draft preservation. A backend container swap cannot deliver these assets. `wrangler.jsonc` describes Cloudflare Worker `coderzclub` with static assets; this is separate from the Java background worker. Do not re-enable automatic builds as part of this plan.

The committed frontend was rebuilt locally with `VITE_API_BASE_URL=https://api.coderzclub.site`; this is a build-time string only, and no production API calls were made. Keep the tested dist artifact isolated from unrelated root dist output. Before any deployment, record the prior Cloudflare version and validate the pinned Wrangler version's commands. Later, after separate read-only Cloudflare authorization:

```bash
npx --offline wrangler deployments list --name coderzclub
```

After backend approval and separate frontend deployment authorization, deploy the reviewed committed-source asset directory using the reviewed Wrangler installation:

```bash
npx --offline wrangler deploy --config wrangler.jsonc --assets backend/target/release-audit-a37c5a5/frontend-dist
```

If frontend rollback is separately authorized:

```bash
: "${PREVIOUS_FRONTEND_VERSION:?set independently recorded previous version ID}"
npx --offline wrangler rollback "$PREVIOUS_FRONTEND_VERSION" --name coderzclub
```

Do not silently download a different Wrangler version if offline resolution fails; pin/review it first. Record the new version ID. In a browser smoke test verify new Java starter, unsupported saved language fallback, nonempty drafts, intentional empty drafts, same-key retry and safe errors. Confirm old frontend assets are recoverable before deployment.
