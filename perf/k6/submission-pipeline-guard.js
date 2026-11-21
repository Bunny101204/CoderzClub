export const DEFAULT_BASE_URL = "http://127.0.0.1:8082";

const LOOPBACK_HOSTS = new Set(["127.0.0.1", "localhost", "::1"]);

export function assertLoopbackTarget(rawTarget = DEFAULT_BASE_URL) {
  let target;
  try {
    target = new URL(rawTarget);
  } catch {
    throw new Error("BASE_URL must be an absolute loopback http URL");
  }

  if (
    target.protocol !== "http:" ||
    !LOOPBACK_HOSTS.has(target.hostname.toLowerCase()) ||
    target.username ||
    target.password
  ) {
    throw new Error(
      "Refusing non-loopback API target; this local test only permits http://127.0.0.1 (or localhost/::1)"
    );
  }

  return rawTarget.replace(/\/+$/, "");
}
