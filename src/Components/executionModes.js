export const EXECUTION_MODES = [
  {
    id: "STANDARD_PER_CASE",
    selectable: true,
    label: "Standard (one Judge0 run per testcase)",
    help: "Each testcase is executed independently. Use this for existing stdin/stdout problems and whenever batching is not proven compatible.",
  },
  {
    id: "FUNCTION_HARNESS_BATCH",
    selectable: true,
    label: "Function harness (line-v1)",
    help: "Student submits a function body only (not a full program). Requires testcaseVersion line-v1, single-line inputs, and Java/Python/C++. Incompatible submissions are rejected rather than silently falling back.",
  },
  {
    id: "BATCH_STDIN_PROGRAM",
    selectable: false,
    label: "Batched stdin/stdout program (not currently supported)",
    help: "Reserved for a future isolated implementation. Safe generic stdin batching is not implemented. This mode cannot be selected for production problems.",
  },
];

export function selectableExecutionModes() {
  return EXECUTION_MODES.filter((mode) => mode.selectable);
}

export function isBatchedExecutionMode(mode) {
  return mode === "FUNCTION_HARNESS_BATCH";
}

export function helpForExecutionMode(mode) {
  const found = EXECUTION_MODES.find((item) => item.id === mode);
  return found ? found.help : "";
}

export function languageSupportsMode(language, mode) {
  if (!language) return false;
  if (mode === "BATCH_STDIN_PROGRAM") return false;
  if (mode === "FUNCTION_HARNESS_BATCH") return !!language.functionHarnessBatch;
  return language.standardPerCase !== false;
}
