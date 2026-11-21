import { Buffer } from "node:buffer";

function redactJwt(value) {
  return value
    .replace(/Bearer\s+[A-Za-z0-9._~+/-]+=*/gi, "Bearer [REDACTED]")
    .replace(/\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b/g, "[REDACTED_JWT]")
    .replace(/(["']?(?:password|token|authorization)["']?\s*[:=]\s*)["']?[^"',\s}]+/gi, "$1[REDACTED]");
}

export function sanitizeText(value, secrets = []) {
  let text = redactJwt(String(value ?? ""));
  for (const secret of secrets) {
    if (secret) text = text.split(secret).join("[REDACTED]");
  }
  return text.slice(0, 2_000);
}

function safeUrl(raw) {
  try {
    const url = new URL(raw);
    return `${url.origin}${url.pathname}`;
  } catch {
    return "[invalid-url]";
  }
}

export class EvidenceCollector {
  constructor(secrets = []) {
    this.secrets = secrets;
    this.browserErrors = [];
    this.httpErrors = [];
    this.timings = [];
  }

  attachPage(page, label) {
    page.on("console", (message) => {
      if (message.type() === "error" || message.type() === "warning") {
        this.browserErrors.push({
          label,
          type: `console.${message.type()}`,
          message: sanitizeText(message.text(), this.secrets),
        });
      }
    });
    page.on("pageerror", (error) => {
      this.browserErrors.push({ label, type: "pageerror", message: sanitizeText(error?.message, this.secrets) });
    });
    page.on("requestfailed", (request) => {
      this.httpErrors.push({
        label,
        method: request.method(),
        url: safeUrl(request.url()),
        status: "REQUEST_FAILED",
        detail: sanitizeText(request.failure()?.errorText, this.secrets),
      });
    });
    page.on("response", (response) => {
      if (response.status() >= 400) {
        this.httpErrors.push({
          label,
          method: response.request().method(),
          url: safeUrl(response.url()),
          status: response.status(),
        });
      }
    });
  }

  timing(name, durationMs, details = {}) {
    this.timings.push({ name, durationMs, ...details });
  }

  async attach(testInfo) {
    await testInfo.attach("browser-errors.json", {
      body: Buffer.from(JSON.stringify(this.browserErrors, null, 2)),
      contentType: "application/json",
    });
    await testInfo.attach("http-errors.json", {
      body: Buffer.from(JSON.stringify(this.httpErrors, null, 2)),
      contentType: "application/json",
    });
    await testInfo.attach("timing-metrics.json", {
      body: Buffer.from(JSON.stringify(this.timings, null, 2)),
      contentType: "application/json",
    });
  }
}

export async function captureScreenshot(page, testInfo, name) {
  const body = await page.screenshot({ fullPage: true });
  await testInfo.attach(name, { body, contentType: "image/png" });
}
