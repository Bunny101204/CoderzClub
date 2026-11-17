import { createHash } from "node:crypto";

function stable(value) {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === "object") {
    return Object.keys(value)
      .sort()
      .reduce((acc, key) => {
        acc[key] = stable(value[key]);
        return acc;
      }, {});
  }
  return value;
}

export function contentHash(problem) {
  const payload = {
    title: problem.title,
    statement: problem.statement,
    difficulty: problem.difficulty,
    category: problem.category,
    tags: problem.tags || [],
    inputFormat: problem.inputFormat,
    outputFormat: problem.outputFormat,
    constraints: problem.constraints,
    hints: problem.hints || [],
    exampleInput: problem.exampleInput,
    exampleOutput: problem.exampleOutput,
    exampleExplanation: problem.exampleExplanation,
    points: problem.points,
    estimatedTime: problem.estimatedTime,
    availability: problem.availability || "PUBLIC",
    executionMode: problem.executionMode || "STANDARD_PER_CASE",
    testcaseVersion: problem.testcaseVersion || "v1",
    publicTestCases: (problem.publicTestCases || []).map((tc) => ({
      input: tc.input,
      output: tc.output,
    })),
    hiddenTestCases: (problem.hiddenTestCases || []).map((tc) => ({
      input: tc.input,
      output: tc.output,
    })),
  };
  return createHash("sha256").update(JSON.stringify(stable(payload))).digest("hex");
}
