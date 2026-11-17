const PRODUCTION_HOST_RE =
  /(^|\.)coderzclub\.site$|(^|\.)mongodb\.net$|mongodb\+srv:\/\//i;

export function classifyApiBase(apiBase) {
  if (!apiBase || typeof apiBase !== "string") {
    return { ok: false, reason: "api-base is required for apply" };
  }
  let url;
  try {
    url = new URL(apiBase);
  } catch {
    return { ok: false, reason: "api-base is not a valid URL" };
  }
  if (!["http:", "https:"].includes(url.protocol)) {
    return { ok: false, reason: "api-base must be http(s)" };
  }
  const host = url.hostname.toLowerCase();
  const production =
    host === "api.coderzclub.site" ||
    host === "coderzclub.site" ||
    host === "www.coderzclub.site" ||
    PRODUCTION_HOST_RE.test(apiBase);
  return { ok: true, url: url.toString().replace(/\/$/, ""), production, host };
}

export function assertApplyAllowed({ apiBase, allowProduction = false, apply = false }) {
  if (!apply) return { allowed: true, mode: "dry-run" };
  const classified = classifyApiBase(apiBase);
  if (!classified.ok) {
    return { allowed: false, reasons: [classified.reason] };
  }
  if (classified.production && !allowProduction) {
    return {
      allowed: false,
      reasons: [
        `refusing production api-base ${classified.host}; pass --allow-production only after backup and owner approval`,
      ],
    };
  }
  return {
    allowed: true,
    mode: "apply",
    apiBase: classified.url,
    production: classified.production,
  };
}

export function difficultyCaseMinimums(difficulty) {
  const d = String(difficulty || "").toUpperCase();
  if (d === "HARD") return { public: 3, hidden: 20 };
  if (d === "MEDIUM") return { public: 2, hidden: 12 };
  return { public: 2, hidden: 6 };
}
