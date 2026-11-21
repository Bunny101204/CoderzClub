import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawn } from "node:child_process";

const root = process.cwd();

function runGuard(envPath) {
  return new Promise((resolve) => {
    const child = spawn("powershell", ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
      join(root, "scripts/ops/start-staging.ps1"), "-EnvFile", envPath], { cwd: root });
    let output = "";
    child.stdout.on("data", (data) => { output += data; });
    child.stderr.on("data", (data) => { output += data; });
    child.on("close", (code) => resolve({ code, output: String(output) }));
  });
}

const base = {
  STAGING_ENVIRONMENT: "staging",
  COMPOSE_PROJECT_NAME: "coderzclub-staging",
  MONGODB_URI: "mongodb://stage-user:stage-pass@staging-mongo.internal:27017/?replicaSet=stage-rs",
  SPRING_DATA_MONGODB_DATABASE: "coderzclub_staging",
  JWT_SECRET: "a".repeat(80),
  REDIS_PASSWORD: "stage-redis-secret",
  RABBITMQ_USERNAME: "stage-rabbit",
  RABBITMQ_PASSWORD: "stage-rabbit-secret",
  RABBITMQ_VHOST: "/coderzclub-staging",
  JUDGE0_API_URL: "http://judge0.staging.internal:2358/submissions",
  APP_BACKEND_URL: "https://staging-api.example.test",
  APP_FRONTEND_URL: "https://staging.example.test",
};

const cases = [
  ["production Mongo URI", { MONGODB_URI: "mongodb://prod-db.internal/coderzclub" }],
  ["production Redis endpoint", { SPRING_REDIS_HOST: "redis.production.internal" }],
  ["production Rabbit endpoint", { RABBITMQ_HOST: "rabbit.production.internal" }],
  ["production Judge0 endpoint", { JUDGE0_API_URL: "https://judge0.production.internal/submissions" }],
  ["production frontend origin", { APP_FRONTEND_URL: "https://coderzclub.site" }],
  ["missing JWT secret", { JWT_SECRET: "" }],
  ["missing Mongo URI", { MONGODB_URI: "" }],
  ["missing Redis secret", { REDIS_PASSWORD: "" }],
  ["missing Rabbit secret", { RABBITMQ_PASSWORD: "" }],
  ["missing Judge0 URL", { JUDGE0_API_URL: "" }],
  ["wrong Mongo database", { SPRING_DATA_MONGODB_DATABASE: "coderzclub" }],
  ["production-like project name", { COMPOSE_PROJECT_NAME: "coderzclub-production" }],
  ["insecure local Judge0 workaround", { JUDGE0_API_URL: "http://127.0.0.1:2358/submissions" }],
];

const temp = await mkdtemp(join(tmpdir(), "coderzclub-staging-guards-"));
try {
  for (const [name, overrides] of cases) {
    const values = { ...base, ...overrides };
    const file = join(temp, ".env.staging");
    await writeFile(file, Object.entries(values).map(([key, value]) => `${key}=${value}`).join("\n"));
    const result = await runGuard(file);
    assert.notEqual(result.code, 0, `${name} was accepted`);
    assert.doesNotMatch(result.output, /stage-(?:redis|rabbit)-secret|a{20,}/,
      `${name} leaked a test secret`);
  }
  console.log(`staging guard tests passed (${cases.length} refusal cases)`);
} finally {
  await rm(temp, { recursive: true, force: true });
}
