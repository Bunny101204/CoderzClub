import { spawn } from "node:child_process";
import { createRequire } from "node:module";

const mode = process.argv[2];
if (!["read", "write"].includes(mode)) {
  console.error("Usage: node scripts/production-e2e/run.mjs <read|write>");
  process.exit(2);
}

if (mode === "write") {
  const enabled = process.env.PRODUCTION_E2E_ENABLED === "true";
  const confirmed = process.env.PRODUCTION_E2E_CONFIRM === "coderzclub.site";
  if (!enabled || !confirmed) {
    console.error(
      "Production writes are blocked. Set PRODUCTION_E2E_ENABLED=true and " +
      "PRODUCTION_E2E_CONFIRM=coderzclub.site only after owner approval."
    );
    process.exit(2);
  }
}

const runId = process.env.PRODUCTION_E2E_RUN_ID ||
  `${mode}-${new Date().toISOString().replace(/[:.]/g, "-")}`;
const env = {
  ...process.env,
  PRODUCTION_E2E_MODE: mode,
  PRODUCTION_E2E_RUN_ID: runId,
};

const require = createRequire(import.meta.url);
const playwrightCli = require.resolve("@playwright/test/cli");
const passthroughArgs = process.argv.slice(3);
const child = spawn(
  process.execPath,
  [playwrightCli, "test", "--config", "playwright.production.config.js", ...passthroughArgs],
  { cwd: process.cwd(), env, stdio: "inherit", shell: false }
);

child.on("error", () => {
  console.error("Could not start Playwright.");
  process.exitCode = 1;
});
child.on("exit", (code, signal) => {
  process.exitCode = signal ? 1 : (code ?? 1);
});
