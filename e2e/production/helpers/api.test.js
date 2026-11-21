import test from "node:test";
import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { spawnSync } from "node:child_process";
import { createRequire } from "node:module";
import {
  existsSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import process from "node:process";
import { fileURLToPath, pathToFileURL } from "node:url";
import { ProductionApiError, apiCall } from "./api.js";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "../../..");
const require = createRequire(import.meta.url);

function canaryValues() {
  return {
    password: "CANARY_PASSWORD_DO_NOT_PRINT_7f41",
    token: "eyJjYW5hcnkiOiJ0ZXN0In0.eyJzZWNyZXQiOiJ0b2tlbiJ9.signature-canary-7f41",
  };
}

function scanFiles(directory, needles) {
  const matches = [];
  if (!existsSync(directory)) return matches;
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      matches.push(...scanFiles(path, needles));
      continue;
    }
    const contents = readFileSync(path);
    for (const needle of needles) {
      if (contents.includes(Buffer.from(needle))) matches.push({ path, needle });
    }
  }
  return matches;
}

test("transport failures replace raw Playwright errors without retaining a cause", async () => {
  const canary = canaryValues();
  const metrics = [];
  const request = {
    fetch: async () => {
      throw new Error(`socket failed password=${canary.password} Authorization: Bearer ${canary.token}`);
    },
  };

  await assert.rejects(
    apiCall(request, "POST", "/api/login", {
      token: canary.token,
      data: { username: "synthetic", password: canary.password },
      metrics,
    }),
    (error) => {
      assert.equal(error instanceof ProductionApiError, true);
      assert.equal(error.kind, "TRANSPORT");
      assert.equal(error.status, null);
      assert.equal(error.cause, undefined);
      assert.match(error.message, /POST \/api\/login failed before an HTTP response/);
      assert.equal(error.stack.includes(canary.password), false);
      assert.equal(error.stack.includes(canary.token), false);
      return true;
    }
  );
  assert.equal(metrics[0].status, "TRANSPORT_ERROR");
});

test("HTTP failures retain status and omit response bodies", async () => {
  const canary = canaryValues();
  const request = {
    fetch: async () => ({
      status: () => 503,
      headers: () => ({ "content-type": "application/json" }),
      text: async () => JSON.stringify({ password: canary.password, token: canary.token }),
    }),
  };

  await assert.rejects(
    apiCall(request, "GET", "/api/users/profile", {
      token: canary.token,
      expectedStatuses: [200],
    }),
    (error) => {
      assert.equal(error.kind, "HTTP");
      assert.equal(error.status, 503);
      assert.match(error.message, /HTTP 503/);
      assert.equal(error.stack.includes(canary.password), false);
      assert.equal(error.stack.includes(canary.token), false);
      return true;
    }
  );
});

test("failed transport canaries do not reach Playwright output or generated reports", () => {
  const canary = canaryValues();
  const directory = mkdtempSync(join(tmpdir(), "coderzclub-secret-regression-"));
  const htmlDir = join(directory, "html");
  const summaryDir = join(directory, "summary");
  const specPath = join(directory, "transport-failure.spec.js");
  const configPath = join(directory, "playwright.config.js");
  const apiUrl = pathToFileURL(join(root, "e2e", "production", "helpers", "api.js")).href;
  const playwrightImport = pathToFileURL(require.resolve("@playwright/test")).href;
  const reporterPath = join(root, "e2e", "production", "reporters", "summary.js");
  const cliPath = require.resolve("@playwright/test/cli");

  try {
    writeFileSync(specPath, `
      import { test } from ${JSON.stringify(playwrightImport)};
      import { apiCall } from ${JSON.stringify(apiUrl)};
      test("sanitized transport failure", async () => {
        const password = process.env.CANARY_PASSWORD;
        const token = process.env.CANARY_TOKEN;
        const request = {
          fetch: async () => {
            throw new Error("raw password=" + password + " Authorization: Bearer " + token);
          }
        };
        await apiCall(request, "POST", "/api/login", {
          token,
          data: { username: "synthetic", password }
        });
      });
    `);
    writeFileSync(configPath, `
      export default {
        testDir: ${JSON.stringify(directory)},
        testMatch: "transport-failure.spec.js",
        workers: 1,
        retries: 0,
        use: { trace: "off", screenshot: "off", video: "off" },
        reporter: [
          ["line"],
          ["html", { outputFolder: ${JSON.stringify(htmlDir)}, open: "never" }],
          [${JSON.stringify(reporterPath)}, { outputDir: ${JSON.stringify(summaryDir)} }]
        ]
      };
    `);

    const result = spawnSync(process.execPath, [cliPath, "test", "--config", configPath], {
      cwd: root,
      env: {
        ...process.env,
        CANARY_PASSWORD: canary.password,
        CANARY_TOKEN: canary.token,
        PRODUCTION_E2E_MODE: "read-regression",
        PRODUCTION_E2E_RUN_ID: "read-regression",
      },
      encoding: "utf8",
      shell: false,
      maxBuffer: 10 * 1024 * 1024,
    });

    assert.equal(result.status, 1, `Expected fixture failure, got stdout=${result.stdout} stderr=${result.stderr}`);
    for (const output of [result.stdout, result.stderr]) {
      assert.equal(output.includes(canary.password), false);
      assert.equal(output.includes(canary.token), false);
    }
    assert.deepEqual(scanFiles(directory, [canary.password, canary.token]), []);
    assert.equal(existsSync(join(summaryDir, "summary.json")), true);
    assert.equal(existsSync(join(htmlDir, "index.html")), true);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
