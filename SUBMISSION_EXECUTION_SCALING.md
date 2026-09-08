# Submission execution scaling

New jobs keep only job metadata and `problemId` plus `testcaseVersion`. The worker reads the current problem from the secure problem store; hidden testcase input and expected output never enter the job document or HTTP response.

Each testcase remains a separate Judge0 sandbox request with the current provider. The worker applies bounded queue worker concurrency, global Judge0 permits, and per-language permits. Public cases always run for feedback. Hidden cases stop after the first failure when `worker.stop-hidden-on-failure=true`; set it to `false` to run every hidden case.

Results are stored in `submission_test_results`, with public payload fields only for `PUBLIC` rows. Provider response maps are not persisted. Existing output truncation limits remain active. If full logs are retained later, store them in object storage and keep only a reference in the result row.

Run `backend/mongodb-indexes.js` before production traffic and run `backend/migrate-submission-results.js` asynchronously for historical jobs. The migration is idempotent and removes legacy embedded arrays after each job's result rows are upserted. Legacy fields remain readable by the application until the migration is complete.

For the profile read model, backfill `user_stats` from historical submissions before relying on counters for old accounts. New final submissions update counters with an atomic per-submission claim, so retries do not double-count.