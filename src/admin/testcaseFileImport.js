export const CASE_DELIMITER = "===CASE===";
export const MAX_IMPORT_FILE_BYTES = 256 * 1024;
export const MAX_IMPORT_CASES = 40;
export const MAX_CASE_CHARS = 32_000;
export const PREVIEW_CASE_LIMIT = 5;

export function splitExactCaseDelimiter(text) {
  const normalized = String(text ?? "").replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  const lines = normalized.split("\n");
  const cases = [];
  let buffer = [];
  for (const line of lines) {
    if (line === CASE_DELIMITER) {
      cases.push(buffer.join("\n"));
      buffer = [];
    } else {
      buffer.push(line);
    }
  }
  cases.push(buffer.join("\n"));
  while (cases.length > 1 && cases[cases.length - 1] === "") {
    cases.pop();
  }
  return cases;
}

export function parsePairedTestcaseFiles(inputText, outputText, { fileBytes = 0 } = {}) {
  if (fileBytes > MAX_IMPORT_FILE_BYTES) {
    return { ok: false, error: `Each file must be at most ${MAX_IMPORT_FILE_BYTES} bytes.` };
  }
  const inputs = splitExactCaseDelimiter(inputText);
  const outputs = splitExactCaseDelimiter(outputText);
  if (inputs.length !== outputs.length) {
    return {
      ok: false,
      error: `Input file has ${inputs.length} case(s) but output file has ${outputs.length}. Counts must match.`
    };
  }
  if (inputs.length === 0) {
    return { ok: false, error: "No test cases found." };
  }
  if (inputs.length > MAX_IMPORT_CASES) {
    return { ok: false, error: `At most ${MAX_IMPORT_CASES} cases can be imported at once.` };
  }
  const cases = [];
  for (let i = 0; i < inputs.length; i++) {
    if (inputs[i].length > MAX_CASE_CHARS || outputs[i].length > MAX_CASE_CHARS) {
      return { ok: false, error: `Case #${i + 1} exceeds the ${MAX_CASE_CHARS} character limit.` };
    }
    cases.push({ input: inputs[i], output: outputs[i] });
  }
  return {
    ok: true,
    cases,
    preview: cases.slice(0, PREVIEW_CASE_LIMIT)
  };
}

export function applyImportedCases(existing, incoming, mode) {
  const current = Array.isArray(existing) ? existing : [];
  const next = incoming.map((tc) => ({
    input: tc.input ?? "",
    output: tc.output ?? "",
    explanation: tc.explanation ?? ""
  }));
  if (mode === "replace") {
    return next.length ? next : [{ input: "", output: "", explanation: "" }];
  }
  const usable = current.filter((tc) => (tc.input || "").length || (tc.output || "").length);
  return [...usable, ...next];
}
