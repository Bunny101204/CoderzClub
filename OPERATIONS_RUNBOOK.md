# CoderzClub production operations

## Scope
The committed Compose and Kubernetes files run separate API and worker processes. MongoDB Atlas and Judge0 remain external dependencies; no cloud-managed service is claimed here. Prometheus scrapes `/actuator/prometheus`, which requires an admin credential. Health probes are `/actuator/health/readiness` and `/actuator/health/liveness`; health details are hidden by default.

## Required configuration
Set `MONGODB_URI`, `MONGODB_DATABASE`, `JWT_SECRET`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`, `SPRING_REDIS_PASSWORD`, `JUDGE0_API_KEY`, and `APP_CORS_ALLOWED_ORIGINS`. Use a secret manager or Kubernetes Secret in production. Never put values in manifests committed to source control. Set `APP_ENVIRONMENT=production`, `LOG_LEVEL_APP=INFO`, and `MANAGEMENT_HEALTH_DETAILS=never`.

## Deploy order
1. Provision MongoDB Atlas, Redis, RabbitMQ, and Judge0; verify network policy and credentials.
2. Apply indexes and the queue topology, then apply `k8s/coderzclub.yaml` with a real image tag and secret values.
3. Wait for Redis/RabbitMQ, then scale API and worker deployments independently.
4. Verify readiness, submit a canary job, verify completion, then enable normal traffic.

## Metrics and thresholds
Spring HTTP metrics are emitted as `http_server_requests`. Custom metrics include `submission_admission`, `submission_queue_depth`, `submission_queue_oldest_message_age_seconds`, `submission_worker_active`, `submission_worker_lease_loss`, `submission_job_retry`, `submission_verdict`, `judge0_latency`, and `judge0_rate_limit`. Alert when queue depth exceeds 90% of max, oldest age exceeds 5 minutes, Judge0 429/503 rises above 5% for 5 minutes, readiness fails, or worker lease loss is nonzero.

Scale API on CPU above 65%, sustained request rate, or p95 latency above the product SLO. Scale workers on queue depth and oldest age; CPU HPA is committed, while queue/age metrics should be wired through Prometheus Adapter before enabling custom-metric HPA rules.

## Failure handling
Redis outage: admission and rate limiting fail closed; restore Redis and verify sorted-set/cache recovery. RabbitMQ outage: job creation returns 503 when queue health cannot be checked; restore broker before replaying requests. Judge0 outage: retries are bounded, 429/503 metrics alert, and do not increase worker replicas until provider capacity recovers. MongoDB outage: readiness fails and no queue replay should be attempted until Atlas connectivity and replica-set health are restored.

## DLQ replay
Inspect messages with RabbitMQ management tooling and confirm each body is a known job ID. Check the job status and lease fields in MongoDB. Replay only messages whose job is `QUEUED` or safely retryable; publish one message at a time to the normal exchange, then observe completion. Do not edit a running lease. Job lease claiming is authoritative, so duplicate delivery is idempotent. Malformed messages remain in the DLQ until manually corrected or discarded.

## Retention and cleanup
Submission summaries/history are permanent. Job documents are transient and the optional cleanup service removes terminal jobs older than `SUBMISSION_RETENTION_JOB_DAYS`. Detailed testcase results have a 30-day Mongo TTL; large logs must go to object storage with only a reference retained. Cleanup defaults to disabled and dry-run. Enable with `SUBMISSION_RETENTION_CLEANUP_ENABLED=true` only after observing `submission_retention_cleanup` and validating the dry-run count.

## Security
The direct Judge0 endpoint requires an authenticated user/admin role and should be disabled at the gateway unless explicitly needed. Put WAF/API gateway rate limits in front of login, submission creation, Judge0, and admin endpoints; recommended starting limits are 10 login attempts/minute/IP, 30 submissions/minute/user, and 10 Judge0 calls/minute/user. Restrict CORS to exact HTTPS production origins. Rotate JWT, broker, Redis, Mongo, and Judge0 secrets without committing them.
