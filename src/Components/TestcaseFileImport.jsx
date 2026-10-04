import { useRef, useState } from "react";
import {
  applyImportedCases,
  MAX_IMPORT_FILE_BYTES,
  parsePairedTestcaseFiles,
  PREVIEW_CASE_LIMIT
} from "../admin/testcaseFileImport.js";

const TestcaseFileImport = ({ kind, cases, onApply }) => {
  const inputRef = useRef(null);
  const outputRef = useRef(null);
  const [mode, setMode] = useState("append");
  const [error, setError] = useState("");
  const [preview, setPreview] = useState(null);
  const [pending, setPending] = useState(null);

  const readFile = (file) =>
    new Promise((resolve, reject) => {
      if (!file) {
        reject(new Error("Choose both an input file and an output file."));
        return;
      }
      if (file.size > MAX_IMPORT_FILE_BYTES) {
        reject(new Error(`Each file must be at most ${MAX_IMPORT_FILE_BYTES} bytes.`));
        return;
      }
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result || ""));
      reader.onerror = () => reject(new Error("Could not read the selected file."));
      reader.readAsText(file);
    });

  const handlePreview = async () => {
    setError("");
    setPreview(null);
    try {
      const inputFile = inputRef.current?.files?.[0];
      const outputFile = outputRef.current?.files?.[0];
      const [inputText, outputText] = await Promise.all([readFile(inputFile), readFile(outputFile)]);
      const parsed = parsePairedTestcaseFiles(inputText, outputText, {
        fileBytes: Math.max(inputFile.size, outputFile.size)
      });
      if (!parsed.ok) {
        setError(parsed.error);
        return;
      }
      setPending(parsed.cases);
      setPreview(parsed);
    } catch (err) {
      setError(err.message || "Import failed.");
    }
  };

  const handleApply = () => {
    if (!pending?.length) {
      setError("Preview a valid pair before importing.");
      return;
    }
    onApply(applyImportedCases(cases, pending, mode));
    setPending(null);
    setPreview(null);
    setError("");
    if (inputRef.current) inputRef.current.value = "";
    if (outputRef.current) outputRef.current.value = "";
  };

  return (
    <div className="mt-4 app-inset rounded-lg p-4">
      <h3 className="font-semibold mb-2">Import {kind} cases from files</h3>
      <p className="text-sm app-muted mb-3">
        Choose a paired input file and output file. Separate cases with a line that is exactly
        <code className="mx-1 app-code px-1 rounded">===CASE===</code>
        Files stay in the browser and become ordinary editable rows.
      </p>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-3">
        <label className="text-sm">
          Input file
          <input ref={inputRef} type="file" accept=".txt,text/plain" className="mt-1 block w-full app-input rounded p-2" />
        </label>
        <label className="text-sm">
          Output file
          <input ref={outputRef} type="file" accept=".txt,text/plain" className="mt-1 block w-full app-input rounded p-2" />
        </label>
      </div>
      <div className="flex flex-wrap items-center gap-3 mb-3">
        <label className="text-sm flex items-center gap-2">
          Mode
          <select value={mode} onChange={(e) => setMode(e.target.value)} className="app-input rounded px-2 py-1">
            <option value="append">Append</option>
            <option value="replace">Replace</option>
          </select>
        </label>
        <button type="button" onClick={handlePreview} className="px-3 py-2 app-btn-secondary rounded text-sm">
          Preview
        </button>
        <button type="button" onClick={handleApply} className="px-3 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded text-sm">
          Import
        </button>
      </div>
      {error && <div className="text-sm text-red-700 dark:text-red-400 mb-2">{error}</div>}
      {preview && (
        <div className="text-sm">
          <div className="mb-2">
            {preview.cases.length} case(s) ready. Showing first {Math.min(PREVIEW_CASE_LIMIT, preview.preview.length)}.
          </div>
          <ul className="space-y-2">
            {preview.preview.map((tc, index) => (
              <li key={index} className="app-code rounded p-2 font-mono text-xs whitespace-pre-wrap">
                <div className="font-semibold mb-1">Case {index + 1}</div>
                <div>IN: {tc.input}</div>
                <div>OUT: {tc.output}</div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
};

export default TestcaseFileImport;
