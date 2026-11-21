import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "../..");
const files = [
  "docker-compose.staging.yml",
  "backend/src/main/resources/application.properties",
  ".env.staging.example",
  "ops/staging/judge0.env.example",
  "ops/staging/JUDGE0_SANDBOX_CHECKLIST.md",
  "scripts/ops/start-staging.ps1",
  "scripts/ops/staging-smoke.ps1",
  "scripts/ops/rollback-staging.ps1",
];
const text = Object.fromEntries(await Promise.all(files.map(async (file) => [file, await readFile(resolve(root, file), "utf8")])));
const compose = text["docker-compose.staging.yml"];
const env = text[".env.staging.example"];
const application = text["backend/src/main/resources/application.properties"];

assert.match(compose, /^name: coderzclub-staging/m);
assert.match(compose, /SPRING_DATA_MONGODB_DATABASE/);
assert.match(compose, /SPRING_REDIS_HOST: redis/);
assert.match(compose, /RABBITMQ_VHOST/);
assert.match(compose, /SUBMISSION_QUEUE_NAME/);
assert.match(env, /SUBMISSION_QUEUE_NAME=coderzclub\.staging\.submissions/);
assert.match(env, /CONTEST_RUN_QUEUE_NAME=coderzclub\.staging\.contest-runs/);
assert.match(compose, /APP_REQUIRE_SECURE_SECRETS: "true"/);
assert.match(compose, /coderzclub-staging-redis-data/);
assert.match(compose, /coderzclub-staging-rabbitmq-data/);
assert.doesNotMatch(compose, /container_name:/);
assert.doesNotMatch(compose, /RABBITMQ_DEFAULT_(USER|PASS):\s*(guest|local)/i);
assert.doesNotMatch(compose, /10\.20\.0\.(4|7)|api\.coderzclub\.site|judge0-local|patch-judge0-no-cgroup/);
assert.doesNotMatch(compose, /down\s+-v|dropDatabase|localdev123|local-demo-judge-secret/);
assert.match(application, /spring\.data\.redis\.host=\$\{SPRING_DATA_REDIS_HOST:\$\{SPRING_REDIS_HOST:localhost\}\}/);
assert.match(env, /SPRING_DATA_MONGODB_DATABASE=coderzclub_staging/);
assert.match(env, /RABBITMQ_VHOST=\/coderzclub-staging/);
assert.doesNotMatch(env, /10\.20\.0\.(4|7)|api\.coderzclub\.site|coderzclub-api-before/);
assert.doesNotMatch(env, /localdev123|your-judge0-api-key|local-demo-judge-secret/);
assert.match(text["ops/staging/JUDGE0_SANDBOX_CHECKLIST.md"], /--cg/);
assert.match(text["scripts/ops/start-staging.ps1"], /coderzclub-staging/);
assert.match(text["scripts/ops/start-staging.ps1"], /coderzclub_staging/);
assert.match(text["scripts/ops/start-staging.ps1"], /production|prod/);
assert.match(text["scripts/ops/rollback-staging.ps1"], /-ConfirmStagingRollback/);
console.log(`staging static checks passed (${files.length} files)`);
