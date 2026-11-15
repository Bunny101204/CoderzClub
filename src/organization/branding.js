const HEX_COLOR = /^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/;
const HTTPS_ASSET = /^https:\/\/[\w.-]+(?:\/[\w./%?=&+#@~-]*)?$/i;

export const DEFAULT_BRANDING = Object.freeze({
  displayName: "CoderzClub",
  logoUrl: null,
  faviconUrl: null,
  primaryColor: "#2563eb",
  secondaryColor: "#0f172a",
  landingTagline: "Practice. Compete. Level up.",
  emailFromName: "CoderzClub",
  source: "platform",
});

export function isSafeHttpsAsset(url) {
  if (typeof url !== "string") return false;
  const trimmed = url.trim();
  const lower = trimmed.toLowerCase();
  if (!HTTPS_ASSET.test(trimmed) || trimmed.length > 500) return false;
  if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.includes("<script")) return false;
  if (lower.endsWith(".svg") || lower.includes(".svg?")) return false;
  return true;
}

export function isSafeHexColor(color) {
  return typeof color === "string" && HEX_COLOR.test(color.trim());
}

export function resolveOrganizationBranding(organization) {
  const branding = organization?.branding || {};
  const displayName = typeof branding.displayName === "string" && branding.displayName.trim()
    ? branding.displayName.trim()
    : (typeof organization?.name === "string" && organization.name.trim()
      ? organization.name.trim()
      : DEFAULT_BRANDING.displayName);
  const landingTagline = typeof branding.landingTagline === "string" && branding.landingTagline.trim()
    ? branding.landingTagline.trim().slice(0, 160)
    : DEFAULT_BRANDING.landingTagline;
  const emailFromName = typeof branding.emailFromName === "string" && branding.emailFromName.trim()
    ? branding.emailFromName.trim().slice(0, 80)
    : displayName;
  return {
    displayName,
    logoUrl: isSafeHttpsAsset(branding.logoUrl) ? branding.logoUrl.trim() : null,
    faviconUrl: isSafeHttpsAsset(branding.faviconUrl) ? branding.faviconUrl.trim() : null,
    primaryColor: isSafeHexColor(branding.primaryColor) ? branding.primaryColor.trim() : DEFAULT_BRANDING.primaryColor,
    secondaryColor: isSafeHexColor(branding.secondaryColor) ? branding.secondaryColor.trim() : DEFAULT_BRANDING.secondaryColor,
    landingTagline,
    emailFromName,
    source: organization?.id ? "organization" : "platform",
    organizationId: organization?.id || null,
  };
}

export function brandingCssVariables(branding = DEFAULT_BRANDING) {
  const resolved = branding?.primaryColor ? branding : DEFAULT_BRANDING;
  return {
    "--org-brand-primary": resolved.primaryColor || DEFAULT_BRANDING.primaryColor,
    "--org-brand-secondary": resolved.secondaryColor || DEFAULT_BRANDING.secondaryColor,
  };
}

/**
 * Custom hostnames require DNS domain verification and managed TLS before they can
 * be enabled. Do not accept arbitrary custom domains in the current release.
 */
export function customDomainReadinessNotes() {
  return [
    "Prove domain ownership with a DNS TXT challenge under _coderzclub-verify.<domain>.",
    "Provision and renew TLS certificates for the verified hostname (managed ACME).",
    "Map the hostname to the existing SPA origin; do not ship a separate frontend app per college.",
    "Reject unverified hostnames at the edge and keep X-Organization-Id membership checks authoritative.",
  ];
}
