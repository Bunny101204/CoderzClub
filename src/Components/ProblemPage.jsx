import React, { useState } from "react";
import { useParams } from "react-router-dom";
import Judge0CodeEditor from "./Judge0CodeEditor";

const FUNCTION_TEMPLATES = {
  java: (name) => `public class Main {\n    public boolean ${name}(String str) {\n        // your code here\n    }\n}`,
  python: (name) => `def ${name}(s):\n    # your code here\n    pass`,
  cpp: (name) => `#include <string>\nbool ${name}(std::string s) {\n    // your code here\n}`,
  javascript: (name) => `function ${name}(s) {\n    // your code here\n}`,
  // Add more languages as needed
};

// Mapping from languageId to language name
const languageIdToName = {
  62: "java",
  71: "python",
  54: "cpp",
  63: "javascript",
  // Add more mappings as needed
};

// Helper to generate function signature for a language
const generateSignature = (lang, functionName, parameters, returnType) => {
  const paramList = parameters.map(p => {
    if (lang === "python") return p.name;
    if (lang === "javascript") return p.name;
    if (lang === "cpp") return `${p.type} ${p.name}`;
    if (lang === "java") return `${p.type} ${p.name}`;
    return p.name;
  }).join(", ");
  if (lang === "python") return `def ${functionName}(${paramList}):`;
  if (lang === "javascript") return `function ${functionName}(${paramList}) {`;
  if (lang === "cpp") return `${returnType} ${functionName}(${paramList}) {`;
  if (lang === "java") return `public ${returnType} ${functionName}(${paramList}) {`;
  return "";
};

// Helper to generate full template for a language
const generateTemplate = (lang, functionName, parameters, returnType) => {
  const signature = generateSignature(lang, functionName, parameters, returnType);
  if (lang === "python") {
    return `${signature}\n    # your code here\n    pass`;
  }
  if (lang === "javascript") {
    return `${signature}\n    // your code here\n}`;
  }
  if (lang === "cpp") {
    return `#include <iostream>\nusing namespace std;\n\n${signature}\n    // your code here\n}`;
  }
  if (lang === "java") {
    return `public class Main {\n    ${signature}\n        // your code here\n    }\n}`;
  }
  return "";
};

const ProblemPage = ({ problems }) => {
  const { id } = useParams();
  const problem = problems.find((p) => p.id === id);

  if (!problem) {
    return <div className="text-white p-6">Problem not found</div>;
  }

  // Default to Java (62)
  const [languageId, setLanguageId] = useState(62);
  // Generate template for current language
  const getTemplate = (langId) => {
    const lang = languageIdToName[langId];
    return generateTemplate(
      lang,
      problem.functionName || "myFunction",
      problem.parameters || [],
      problem.returnType || "void"
    );
  };
  const [editorCode, setEditorCode] = useState(getTemplate(languageId));

  // Example: constraints and public test cases (if present)
  const constraints = problem.constraints || [];
  const publicTestCases = problem.testCases || [];

  // Handler for language change
  const handleLanguageChange = (newLangId) => {
    setLanguageId(newLangId);
    setEditorCode(getTemplate(newLangId));
  };

  // Handler for reset button
  const handleResetCode = () => {
    setEditorCode(getTemplate(languageId));
  };

  // Helper to render function signature
  const renderSignature = () => {
    if (!problem.functionName || !problem.parameters || !problem.returnType) return null;
    const paramList = problem.parameters.map(p => `${p.type} ${p.name}`).join(", ");
    return (
      <div className="mb-4 p-3 bg-gray-900 rounded text-green-300 font-mono text-base">
        <span className="text-blue-300">{problem.returnType}</span> <span className="text-yellow-200">{problem.functionName}</span>({paramList})
      </div>
    );
  };

  // Pretty-print a value for display (handles strings, arrays, objects)
  const formatValue = (val) => {
    const replacer = (_, value) => value;
    if (typeof val === "string") return `"${val}"`;
    try {
      // Compact one-line JSON for arrays/objects
      return JSON.stringify(val, replacer);
    } catch {
      return String(val);
    }
  };

  // Render test case input considering common shapes: direct value, [value], [[...]] etc.
  const renderInput = (input) => {
    // If input is a single-element array that itself is an array/object, unwrap one level for readability
    if (Array.isArray(input) && input.length === 1 && (Array.isArray(input[0]) || typeof input[0] === 'object')) {
      return formatValue(input[0]);
    }
    return formatValue(input);
  };

  return (
    <div className="min-h-screen bg-gray-900 text-white p-4 flex flex-col md:flex-row gap-6">
      {/* Left: Problem details */}
      <div className="md:w-1/2 w-full bg-gray-800 rounded-lg p-6 shadow-lg mb-4 md:mb-0">
        <h2 className="text-2xl font-bold mb-4">🧩 {problem.title}</h2>
        {renderSignature()}
        <p className="mb-4 whitespace-pre-line">{problem.statement}</p>
        {constraints.length > 0 && (
          <div className="mb-4">
            <h3 className="text-lg font-semibold mb-2 text-blue-300">Constraints</h3>
            <ul className="list-disc list-inside text-gray-300">
              {constraints.map((c, i) => (
                <li key={i}>{c}</li>
              ))}
            </ul>
          </div>
        )}
        {publicTestCases.length > 0 && (
          <div className="mb-4">
            <h3 className="text-lg font-semibold mb-2 text-green-300">Public Test Cases</h3>
            <ul className="space-y-2">
              {publicTestCases.map((tc, i) => (
                <li key={i} className="bg-gray-900 rounded p-2 text-sm">
                  <div className="mb-1"><span className="font-bold">Input:</span>
                    <pre className="mt-1 p-2 bg-gray-950 rounded whitespace-pre overflow-auto">{renderInput(tc.input)}</pre>
                  </div>
                  <div><span className="font-bold">Output:</span>
                    <pre className="mt-1 p-2 bg-gray-950 rounded whitespace-pre overflow-auto">{formatValue(tc.output)}</pre>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
      {/* Right: Editor */}
      <div className="md:w-1/2 w-full flex flex-col">
        <div className="flex-1">
          <div className="flex justify-end mb-2">
            <button
              onClick={handleResetCode}
              className="bg-yellow-500 hover:bg-yellow-600 text-white px-3 py-1 rounded text-sm"
              type="button"
            >
              Reset Code
            </button>
          </div>
          <Judge0CodeEditor
            initialCode={editorCode}
            setEditorCode={setEditorCode}
            languageId={languageId}
            setLanguageId={handleLanguageChange}
            testCases={problem.testCases}
            hiddenTestCases={problem.hiddenTestCases}
            showButtonsBelow
            functionName={problem.functionName}
            parameters={problem.parameters}
            returnType={problem.returnType}
          />
        </div>
      </div>
    </div>
  );
};

export default ProblemPage;
