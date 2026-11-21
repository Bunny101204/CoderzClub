# Docker rollout kit for `6cf84e7`

Offline-safe scripts for the GCP load-test cutover of API, worker, and Judge0 on
`coderzclub-load-test` (asia-south1-a). Default mode is dry-run; mutation requires
explicit approval flags.

## Contents

- `prepare-image.ps1` / `transfer-image-iap.ps1` — build/verify local image and IAP transfer
- `rollout-api.sh` / `rollout-worker.sh` + matching `rollback-*.sh` — park/swap Docker containers
- `docker_swap.py` (+ tests) — shared park/swap helper
- `judge0-harden.sh` (+ validators/tests) — harden Judge0 1.13.1 (`judge0-v1131`) without touching db/redis
- `health-check.sh` / `smoke-judge0-sandbox.sh` — post-cutover checks
- `validate-local.ps1` — local syntax/unit validation without GCP access

## Safety

- Do **not** point these scripts at production MongoDB Atlas.
- Judge0 harden recreates **only** `server` and `workers` (`--no-deps`).
- Dry-run is the default (`DRY_RUN=yes`). Live mutate requires `DRY_RUN=no APPROVE=yes`.
- Fixture `testdata/judge0-v1131/judge0.conf` uses redacted placeholders only.

## Local validation

```bash
python3 docker_swap_test.py
bash judge0-harden_test.sh
```
