export function filtersAreActive({ search = "", difficulty = "", topic = "" } = {}) {
  return Boolean(String(search).trim() || difficulty || topic);
}

export function catalogStatus({
  loading = false,
  error = null,
  offline = false,
  count = 0,
  filtersActive = false,
} = {}) {
  if (loading) {
    return { kind: "loading", text: "Loading problems…" };
  }
  if (error) {
    if (offline) {
      return {
        kind: "offline",
        text: "You appear to be offline. Check the connection, then try again.",
      };
    }
    return {
      kind: "error",
      text: "Problems could not be loaded.",
    };
  }
  if (count === 0 && filtersActive) {
    return {
      kind: "no-results",
      text: "No problems match these filters.",
    };
  }
  if (count === 0) {
    return {
      kind: "empty",
      text: "No problems have been published yet.",
    };
  }
  return { kind: "ready", text: "" };
}
