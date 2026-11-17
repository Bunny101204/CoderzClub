/**
 * Guard for local cz-pb-staging load tests only.
 * Refuses production hosts and non-loopback targets.
 */
export function assertLocalPbStagingTarget(baseUrl) {
  const raw = String(baseUrl || "").trim().replace(/\/$/, "");
  if (!raw) {
    throw new Error("BASE_URL is required");
  }
  const ok =
    raw === "http://127.0.0.1:18090" ||
    raw === "http://localhost:18090";
  if (!ok) {
    throw new Error(
      `Refusing BASE_URL '${raw}'. Phase 7 local load allows only http://127.0.0.1:18090 (cz-pb-staging).`
    );
  }
  if (/coderzclub\.site|mongodb\.net|atlas/i.test(raw)) {
    throw new Error("Refuse production-like target");
  }
}
