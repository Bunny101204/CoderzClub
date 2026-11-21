import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const bootstrap = read("./bootstrap-staging-gcp.ps1");
const secrets = read("./initialize-staging-secrets.ps1");
const seed = read("./seed-staging-synthetic.ps1");
const app = read("./remote/bootstrap-app.sh");
const judge = read("./remote/bootstrap-judge0.sh");
const infra = read("./remote/bootstrap-infra.sh");

test("execution scripts are approval-gated and non-destructive", () => {
  assert.match(bootstrap, /-Apply/);
  assert.match(bootstrap, /-Approve/);
  assert.match(secrets, /never printed|not printed/);
  assert.match(seed, /ConfirmSyntheticOnly/);
  for (const script of [app, judge, infra]) {
    assert.doesNotMatch(script, /patch-judge0-no-cgroup|fix-judge0-init|docker compose down -v|FLUSHALL|purge/i);
  }
});

test("remote services use private targets, Secret Manager, and health gates", () => {
  assert.match(app, /gcloud secrets versions access/);
  assert.match(app, /SPRING_DATA_MONGODB_DATABASE=coderzclub_staging/);
  assert.match(app, /coderzclub\.staging\.submissions/);
  assert.match(app, /actuator\/health\/readiness/);
  assert.match(judge, /CgroupVersion/);
  assert.match(judge, /ENABLE_NETWORK:|docker-compose/);
  assert.match(infra, /docker compose up -d --wait/);
});
