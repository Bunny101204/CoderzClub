import { Buffer } from "node:buffer";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import process from "node:process";
import { sanitizeText } from "../helpers/observability.js";

function attachmentJson(attachments, name) {
  const attachment = attachments.find((item) => item.name === name);
  if (!attachment) return [];
  try {
    const raw = attachment.body || (attachment.path && existsSync(attachment.path) ? readFileSync(attachment.path) : null);
    return raw ? JSON.parse(Buffer.from(raw).toString("utf8")) : [];
  } catch {
    return [];
  }
}

export default class ProductionSummaryReporter {
  constructor(options = {}) {
    this.outputDir = options.outputDir || "artifacts/playwright-production/read-only";
    this.tests = [];
  }

  onTestEnd(test, result) {
    this.tests.push({
      title: test.titlePath().join(" > "),
      status: result.status,
      durationMs: result.duration,
      retry: result.retry,
      error: result.error ? sanitizeText(result.error.message) : null,
      attachments: result.attachments.map((attachment) => attachment.name),
      browserErrors: attachmentJson(result.attachments, "browser-errors.json"),
      httpErrors: attachmentJson(result.attachments, "http-errors.json"),
      timings: attachmentJson(result.attachments, "timing-metrics.json"),
    });
  }

  onEnd(runResult) {
    mkdirSync(this.outputDir, { recursive: true });
    const summary = {
      generatedAt: new Date().toISOString(),
      target: "https://coderzclub.site",
      mode: process.env.PRODUCTION_E2E_MODE || "unknown",
      runId: process.env.PRODUCTION_E2E_RUN_ID || "unknown",
      status: runResult.status,
      tests: this.tests,
      totals: {
        tests: this.tests.length,
        passed: this.tests.filter((test) => test.status === "passed").length,
        failed: this.tests.filter((test) => test.status === "failed" || test.status === "timedOut").length,
        browserErrors: this.tests.reduce((sum, test) => sum + test.browserErrors.length, 0),
        httpErrors: this.tests.reduce((sum, test) => sum + test.httpErrors.length, 0),
      },
    };
    writeFileSync(join(this.outputDir, "summary.json"), JSON.stringify(summary, null, 2));
    const lines = [
      "# CoderzClub production Playwright evidence",
      "",
      `- Run: ${summary.runId}`,
      `- Mode: ${summary.mode}`,
      `- Status: ${summary.status}`,
      `- Tests: ${summary.totals.passed} passed, ${summary.totals.failed} failed`,
      `- Captured browser errors: ${summary.totals.browserErrors}`,
      `- Captured HTTP errors: ${summary.totals.httpErrors}`,
      "",
      "## Test results",
      ...summary.tests.map((test) => `- ${test.status.toUpperCase()}: ${test.title} (${test.durationMs} ms)`),
      "",
      "Traces, screenshots, timing details, and the created-resource ledger remain in this ignored local run directory.",
    ];
    writeFileSync(join(this.outputDir, "summary.md"), lines.join("\n"));
  }
}
