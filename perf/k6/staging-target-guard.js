const FORBIDDEN_TARGET = /(^|[.\-])(prod|production|coderzclub\.site)([.\-]|$)/i;

export function assertApprovedStagingTarget(rawTarget) {
  if (__ENV.STAGING_LOADTEST_APPROVED !== "1") {
    throw new Error("Set STAGING_LOADTEST_APPROVED=1 only after the staging preflight is approved");
  }
  let target;
  try {
    target = new URL(rawTarget);
  } catch {
    throw new Error("BASE_URL must be an absolute staging URL");
  }
  if (!(target.protocol === "http:" || target.protocol === "https:") || target.username || target.password) {
    throw new Error("BASE_URL must be an http(s) URL without embedded credentials");
  }
  if (target.hostname === "127.0.0.1" || target.hostname === "localhost" || FORBIDDEN_TARGET.test(target.hostname)) {
    throw new Error("Refusing loopback or production-like target for remote staging k6");
  }
  return rawTarget.replace(/\/+$/, "");
}
