export function batchStatusSymbol(status) {
  if (status === "SOLVED") return "✓";
  if (status === "ATTEMPTED") return "●";
  return "–";
}

export function batchStatusLabel(status) {
  if (status === "SOLVED") return "Solved";
  if (status === "ATTEMPTED") return "Attempted";
  return "Unsolved";
}

export function displayAssignedProblemId(problem) {
  if (problem == null || problem.numericId == null || problem.numericId === "") {
    return "—";
  }
  return String(problem.numericId);
}
