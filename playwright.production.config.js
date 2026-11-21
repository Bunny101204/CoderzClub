import { defineConfig } from "@playwright/test";
import process from "node:process";

// Production specs self-register only when this explicit config is loaded, so
// the existing local Playwright config remains unchanged and cannot run them.
process.env.PRODUCTION_E2E_CONFIG = "true";

const runId = process.env.PRODUCTION_E2E_RUN_ID || "read-only";
const artifactRoot = `artifacts/playwright-production/${runId}`;

export default defineConfig({
  testDir: "./e2e/production",
  testMatch: "**/*.spec.js",
  timeout: 6 * 60_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  outputDir: `${artifactRoot}/test-results`,
  reporter: [
    ["line"],
    ["html", { outputFolder: `${artifactRoot}/html`, open: "never" }],
    ["./e2e/production/reporters/summary.js", { outputDir: artifactRoot }],
  ],
  use: {
    baseURL: "https://coderzclub.site",
    headless: true,
    screenshot: "only-on-failure",
    // The write spec starts traces after login so password entry is not captured.
    trace: "off",
    video: "off",
    actionTimeout: 20_000,
    navigationTimeout: 30_000,
  },
});
