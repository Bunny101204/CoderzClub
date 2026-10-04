import { statusPresentation } from "../progress/problemStatusView.js";

export function batchStatusSymbol(status) {
  const presentation = statusPresentation(status);
  if (presentation.kind === "blank") return "";
  return presentation.kind;
}

export function batchStatusLabel(status) {
  return statusPresentation(status).label === "Not attempted"
    ? "Unsolved"
    : statusPresentation(status).label;
}

export function displayAssignedProblemId(problem) {
  if (problem == null || problem.numericId == null || problem.numericId === "") {
    return "—";
  }
  return String(problem.numericId);
}
