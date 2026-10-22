# Submission execution scaling

New jobs keep only job metadata and `problemId` plus `testcaseVersion`. The worker reads the current problem from the secure problem store; hidden testcase input and expected output never enter the job document or HTTP response.

Each testcase remains a separate Judge0 sandbox request with the current provider (`wait=true` terminal POST; no token polling). The worker applies bounded queue worker concurrency, per-JVM global Judge0 permits, and per-language permits. Public cases always run for feedback. Hidden cases stop after the first failure when `worker.stop-hidden-on-failure=true`; set it to `false` to run every hidden case.

## Per-JVM permits are not cluster-global

Judge0 production capacity is **not** known in-repo. Do not treat the numbers below as a safe Judge0 cluster limit. They are only an application-side bound.

Each JVM has its own semaphore (`worker.max-global-judge0-concurrency`). Profiles bind **independent** env vars:

- `worker` profile: `WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY` (default **2**)
- `api` profile: `JUDGE0_API_MAX_CONCURRENCY` (default **2**)
- API does **not** fall back to `WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY`

Worker default 2 matches `submission.queue.concurrency` (two blocking consumers). Intra-job public testcase threads (`worker.max-testcase-concurrency`, default 2) share that same semaphore, so they cannot raise Judge0 concurrency above the per-JVM cap.

```
max_submit_provider_activity ≈ max_worker_replicas × WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY
                            ≈ 4 × 2 = 8

max_run_provider_activity    ≈ max_api_replicas × JUDGE0_API_MAX_CONCURRENCY
                            ≈ 6 × 2 = 12

combined application-side ceiling ≈ 8 + 12 = 20
```

Kubernetes autoscaling keeps limited headroom (not the previous 20×8 / 30×8 configuration):

- API HPA: min 3, max **6**
- Worker KEDA: min 2, max **4**

Raise replica maxima or per-pod caps only after measuring Judge0. There is no Redis distributed semaphore.

## Rabbit listener concurrency

Canonical setting: `submission.queue.concurrency` / `SUBMISSION_QUEUE_CONCURRENCY` (default 2). Prefetch default is 1 (`SUBMISSION_QUEUE_PREFETCH`). `worker.concurrency` / `WORKER_CONCURRENCY` does **not** control the listener.

Results are stored in `submission_test_results`, with public payload fields only for `PUBLIC` rows. Provider response maps are not persisted. Existing output truncation limits remain active. If full logs are retained later, store them in object storage and keep only a reference in the result row.

Run `backend/mongodb-indexes.js` before production traffic and run `backend/migrate-submission-results.js` asynchronously for historical jobs. The migration is idempotent and removes legacy embedded arrays after each job's result rows are upserted. Legacy fields remain readable by the application until the migration is complete.

For the profile read model, backfill `user_stats` from historical submissions before relying on counters for old accounts. New final submissions update counters with an atomic per-submission claim, so retries do not double-count.