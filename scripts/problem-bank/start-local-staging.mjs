#!/usr/bin/env node
/**
 * Start isolated local problem-bank staging (Compose project cz-pb-staging).
 * Refuses production selectors and refuses to touch non-cz-pb-staging containers.
 */
import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, writeFileSync, readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const composeFile = resolve(root, "docker-compose.pb-staging.yml");
const project = "cz-pb-staging";
const reportDir = resolve(root, ".local/problem-bank/reports");

function run(cmd, args, opts = {}) {
  const result = spawnSync(cmd, args, {
    cwd: root,
    encoding: "utf8",
    shell: false,
    ...opts,
  });
  if (result.status !== 0 && !opts.allowFail) {
    const detail = (result.stderr || result.stdout || "").trim();
    throw new Error(`${cmd} ${args.join(" ")} failed${detail ? `: ${detail.slice(0, 800)}` : ""}`);
  }
  return result;
}

function assertIsolation() {
  if (!existsSync(composeFile)) throw new Error("Missing docker-compose.pb-staging.yml");
  const compose = readFileSync(composeFile, "utf8");
  if (/mongodb\.net|mongodb\+srv|api\.coderzclub\.site|coderzclub\.site/i.test(compose)) {
    throw new Error("Refuse: compose file references production hosts");
  }
  if (!compose.includes("coderzclub_pb_staging")) {
    throw new Error("Refuse: staging DB name coderzclub_pb_staging missing");
  }
  if (!compose.includes("127.0.0.1:18090")) {
    throw new Error("Refuse: expected loopback API bind 127.0.0.1:18090");
  }

  const ps = run("docker", ["ps", "-a", "--format", "{{.Names}}"]);
  const names = (ps.stdout || "").split(/\r?\n/).filter(Boolean);
  const foreignRunning = run("docker", ["ps", "--format", "{{.Names}}"]);
  const running = (foreignRunning.stdout || "").split(/\r?\n/).filter(Boolean);
  const unexpectedRunning = running.filter((n) => !n.startsWith(`${project}-`) && n !== project);
  // Allowed: only our project may be started by this script. Other running containers are a soft warning
  // unless they share our unique resources.
  const collisions = names.filter((n) => n.includes("pb-staging") && !n.startsWith(`${project}-`));
  if (collisions.length) {
    throw new Error(`Refuse: unexpected pb-staging name collision: ${collisions.join(", ")}`);
  }

  const volumes = run("docker", ["volume", "ls", "--format", "{{.Name}}"]);
  const volumeNames = (volumes.stdout || "").split(/\r?\n/).filter(Boolean);
  for (const required of [
    "cz_pb_staging_mongo",
    "cz_pb_staging_redis",
    "cz_pb_staging_rabbit",
    "cz_pb_staging_judge_pg",
  ]) {
    // ok if missing before first up
    if (volumeNames.includes(required)) continue;
  }
  const badVol = volumeNames.find((v) => /atlas|prod/i.test(v) && /pb_staging|pb-staging/i.test(v));
  if (badVol) throw new Error(`Refuse: suspicious volume ${badVol}`);

  return { namesCount: names.length, runningForeign: unexpectedRunning.length };
}

function ensureImage() {
  const image = process.env.PB_STAGING_BACKEND_IMAGE || "coderzclub-backend:pb-staging";
  const inspect = run("docker", ["image", "inspect", image], { allowFail: true });
  if (inspect.status === 0) {
    console.log(`Using existing image ${image}`);
    return image;
  }
  console.log(`Building ${image} from ./backend ...`);
  run("docker", ["build", "-t", image, "./backend"], { stdio: "inherit" });
  return image;
}

function main() {
  const apply = process.argv.includes("--apply");
  const isolation = assertIsolation();
  console.log(`Isolation OK. Existing containers catalogued: ${isolation.namesCount}. Foreign running: ${isolation.runningForeign}.`);

  run("docker", ["compose", "-p", project, "-f", composeFile, "config"], { stdio: "inherit" });

  if (!apply) {
    console.log("Validation only. Pass --apply to start cz-pb-staging (will not start/stop other projects).");
    return;
  }

  const image = ensureImage();
  process.env.PB_STAGING_BACKEND_IMAGE = image;

  // Project flag isolates this up to cz-pb-staging only; other stopped containers are untouched.
  run(
    "docker",
    ["compose", "-p", project, "-f", composeFile, "up", "-d"],
    {
      stdio: "inherit",
      env: { ...process.env, PB_STAGING_BACKEND_IMAGE: image },
    }
  );

  // If services were missing, --no-recreate still creates new ones; ensure they exist.
  const ours = run("docker", ["compose", "-p", project, "-f", composeFile, "ps", "-a", "--format", "{{.Name}}"]);
  console.log("Project containers:\n" + (ours.stdout || ""));

  mkdirSync(reportDir, { recursive: true });
  writeFileSync(
    resolve(reportDir, "pb-staging-start.json"),
    JSON.stringify(
      {
        project,
        apiBase: "http://127.0.0.1:18090",
        mongoHostPort: "127.0.0.1:27029",
        judge0: "http://127.0.0.1:2360",
        database: "coderzclub_pb_staging",
        image,
        startedAt: new Date().toISOString(),
      },
      null,
      2
    )
  );
  console.log("Started project cz-pb-staging. API http://127.0.0.1:18090");
}

main();
