export function studentSearchMessage(state) {
  if (state === "error") return "Student search failed. Nothing was added.";
  if (state === "loading") return "Searching registered students...";
  if (state === "empty") return "No matching registered users.";
  return "";
}

export function studentHitStatus(user) {
  return user?.alreadyMember ? "Already in this batch" : "Eligible to add";
}

export function formatJoined(value) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleDateString();
}

export function isoDayBound(dateValue, endOfDay) {
  if (!dateValue) return "";
  const suffix = endOfDay ? "T23:59:59.999Z" : "T00:00:00.000Z";
  const date = new Date(`${dateValue}${suffix}`);
  if (Number.isNaN(date.getTime())) return "";
  return date.toISOString();
}
