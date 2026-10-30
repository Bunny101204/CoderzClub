# Repeatable local release checklist

This document prepares a later release. It does not deploy anything.

The untracked scripts in `ops/api-aa1ea86-release/` and the historical helper in `ops/batch-mgmt-release/` stay as they are. Use them only after a human reviews the image tag, and only with an explicit deploy confirmation. Do not copy an old image digest into a future script.

## Preconditions

- `main` matches the commit you intend to ship.
- Local Maven tests, frontend tests, Playwright, and both production builds have passed.
- Mongo, Redis, and RabbitMQ for tests are local. Do not point a test run at Atlas, production Redis, production RabbitMQ, or production Judge0.
- No index drops and no TTL changes are part of this release.

## Image identity

Tag the API and worker images with the full Git commit SHA, for example `coderzclub-backend:<sha>`. Record the image id from `docker image inspect` before the VM pull. Reject a deploy when the pulled digest does not match that id.

## Order

1. Build the backend jar locally with `mvn -DskipTests package` only after the test suite is green. This jar is not a production image by itself.
2. Build the frontend with `VITE_API_BASE_URL=https://api.coderzclub.site`. That value is a build-time API address. Do not run tests against it.
3. Deploy the API container first, preserving its current environment variables. Wait for the existing readiness probe.
4. Confirm `GET /api/my/batches` from a student session includes `enrolled`, and `GET /api/does-not-exist-audit` returns 404.
5. Deploy the Cloudflare frontend assets only after that API check. The current worker does not need a new image for these frontend files.
6. Leave the background worker on its current image unless the release changes queue consumers. This release does not.
7. Smoke-test one admin session and one student session. Roll back the API container to the previous image id, then roll back the Worker version, if student batches or contest access regress.

## Dry run

A dry run prints the image tag, the saved environment keys (names only), the health URL, and the rollback image id. It does not SSH, pull, rename, or delete containers.

## Confirmation

A real deploy requires a separate typed confirmation. Scripts must refuse to continue when that confirmation is absent.
