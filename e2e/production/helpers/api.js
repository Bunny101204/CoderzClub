import { PRODUCTION_API_ORIGIN } from "./gates.js";

export class ProductionApiError extends Error {
  constructor(message, { kind, method, path, status = null } = {}) {
    super(message);
    this.name = "ProductionApiError";
    this.kind = kind || "REQUEST";
    this.method = method || "UNKNOWN";
    this.path = path || "/";
    this.status = status;
  }
}

function safePath(path) {
  const url = new URL(path, PRODUCTION_API_ORIGIN);
  if (url.origin !== PRODUCTION_API_ORIGIN) {
    throw new Error("Refusing a production E2E request outside the API allowlist.");
  }
  return `${url.pathname}${url.search}`;
}

export async function apiCall(
  request,
  method,
  path,
  { token, organizationId, data, expectedStatuses = [200], metrics } = {}
) {
  const requestPath = safePath(path);
  const headers = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (organizationId) headers["X-Organization-Id"] = organizationId;
  const started = Date.now();
  let response;
  try {
    response = await request.fetch(`${PRODUCTION_API_ORIGIN}${requestPath}`, {
      method,
      headers,
      data,
    });
  } catch {
    const durationMs = Date.now() - started;
    const diagnosticPath = new URL(requestPath, PRODUCTION_API_ORIGIN).pathname;
    metrics?.push({ method, path: diagnosticPath, status: "TRANSPORT_ERROR", durationMs });
    throw new ProductionApiError(
      `${method} ${diagnosticPath} failed before an HTTP response was received.`,
      { kind: "TRANSPORT", method, path: diagnosticPath }
    );
  }
  const durationMs = Date.now() - started;
  const diagnosticPath = new URL(requestPath, PRODUCTION_API_ORIGIN).pathname;
  metrics?.push({ method, path: diagnosticPath, status: response.status(), durationMs });
  if (!expectedStatuses.includes(response.status())) {
    throw new ProductionApiError(
      `${method} ${diagnosticPath} returned HTTP ${response.status()}.`,
      { kind: "HTTP", method, path: diagnosticPath, status: response.status() }
    );
  }
  const contentType = response.headers()["content-type"] || "";
  const text = await response.text();
  let body = text;
  if (contentType.includes("json") && text) {
    try {
      body = JSON.parse(text);
    } catch {
      throw new ProductionApiError(
        `${method} ${diagnosticPath} returned invalid JSON.`,
        { kind: "RESPONSE", method, path: diagnosticPath, status: response.status() }
      );
    }
  }
  return { status: response.status(), body, contentType, durationMs };
}

export async function loginAccount(request, account, metrics) {
  const login = await apiCall(request, "POST", "/api/login", {
    data: { username: account.username, password: account.password },
    expectedStatuses: [200],
    metrics,
  });
  if (!login.body?.token) throw new Error("Production E2E login did not return an access token.");
  const profile = await apiCall(request, "GET", "/api/users/profile", {
    token: login.body.token,
    expectedStatuses: [200],
    metrics,
  });
  if (!profile.body?.id) throw new Error("Production E2E profile did not return a user id.");
  return { token: login.body.token, role: login.body.role, profile: profile.body };
}
