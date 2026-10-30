export function legacyProblemTarget(id) {
  const value = id == null ? "" : String(id).trim();
  if (!value || value.includes("/") || value.includes("\\")) {
    return "/home";
  }
  return `/problem/${encodeURIComponent(value)}`;
}
