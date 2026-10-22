const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "0.0.0.0", "::1"]);
const DEV_PORTS = new Set(["8080", "5173"]);

/**
 * Development keeps an empty base so the Vite dev server proxy can forward `/api`.
 * Production requires an absolute HTTPS API origin and rejects local or tunnel hosts.
 */
export function resolveApiBaseUrl({ dev, raw } = {}) {
  if (dev === true) {
    return "";
  }
  const trimmed = String(raw ?? "").trim().replace(/\/+$/, "");
  if (!trimmed) {
    throw new Error("VITE_API_BASE_URL is required for production builds");
  }
  let url;
  try {
    url = new URL(trimmed);
  } catch {
    throw new Error("VITE_API_BASE_URL must be an absolute HTTPS URL");
  }
  if (url.protocol !== "https:") {
    throw new Error("VITE_API_BASE_URL must use HTTPS");
  }
  const host = url.hostname.replace(/^\[|\]$/g, "").toLowerCase();
  if (LOCAL_HOSTS.has(host)) {
    throw new Error("VITE_API_BASE_URL must not target a local development host");
  }
  if (host.endsWith(".github.dev") || host.includes("codespaces")) {
    throw new Error("VITE_API_BASE_URL must not target a Codespaces development tunnel");
  }
  if (DEV_PORTS.has(url.port)) {
    throw new Error("VITE_API_BASE_URL must not target a development port");
  }
  const path = url.pathname === "/" ? "" : url.pathname.replace(/\/+$/, "");
  return `${url.origin}${path}`;
}

/** Prefix same-origin `/api` paths. Leave absolute URLs and non-API paths unchanged. */
export function prefixApiPath(path, base) {
  if (typeof path !== "string" || !path.startsWith("/api")) {
    return path;
  }
  return `${base || ""}${path}`;
}

/** Cloudflare SPA fallback returns index.html with HTTP 200. That is not an API payload. */
export function htmlApiPayloadError(body, contentType = "") {
  const type = String(contentType || "").toLowerCase();
  if (type.includes("text/html")) {
    return new Error("API returned HTML instead of JSON");
  }
  if (typeof body === "string") {
    const trimmed = body.trim().slice(0, 64).toLowerCase();
    if (trimmed.startsWith("<!doctype") || trimmed.startsWith("<html")) {
      return new Error("API returned HTML instead of JSON");
    }
  }
  return null;
}
