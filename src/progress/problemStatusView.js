export function statusPresentation(status) {
  if (status === "SOLVED") {
    return { kind: "check", label: "Solved", visibleText: "" };
  }
  if (status === "ATTEMPTED") {
    return { kind: "dot", label: "Attempted", visibleText: "" };
  }
  return { kind: "blank", label: "Not attempted", visibleText: "" };
}

export function hasVisibleUnsolvedPlaceholder(status) {
  const { kind, visibleText } = statusPresentation(status);
  if (kind !== "blank") return false;
  return Boolean(visibleText) || visibleText === "-" || visibleText === "–" || visibleText === "N/A";
}
