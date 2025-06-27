import React, { useState, useEffect } from "react";
import axios from "axios";
import "../index.css";
import UtilBar from "./UtilBar";
import { EditorContextAPI } from "./EditorContextAPI";

const Judge0CodeEditor = () => {
  const [sourceCode, setSourceCode] = useState("");
  const [output, setOutput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [userInput, setUserInput] = useState("");
  const [languageId, setLanguageId] = useState(62); // Default to Java

  // UtilityFunctions.jsx
  let downloadCode = (sourceCode, languageId) => {
    const languages = {
      50: "C",
      54: "C++",
      62: "Java",
      71: "Python",
      63: "JavaScript",
      74: "TypeScript",
      60: "Go",
      68: "PHP",
      72: "Ruby",
    };

    const language = languages[languageId];
    const extension =
      {
        Java: "java",
        C: "c",
        "C++": "cpp",
        Python: "py",
        JavaScript: "js",
        TypeScript: "ts",
        Go: "go",
        PHP: "php",
        Ruby: "rb",
        txt: "txt",
      }[language] || "txt";

    const blob = new Blob([sourceCode], { type: "text/plain" });
    const link = document.createElement("a");
    link.download = `code.${extension}`;
    link.href = URL.createObjectURL(blob);
    link.click();
  };

  // Normalize class name
  const normalizedCode = sourceCode.replace(
    /public\s+class\s+\w+/,
    "public class Main"
  );

  const handleSubmit = async () => {
    setIsLoading(true);

    try {
      const response = await axios.post(
        "https://judge0-ce.p.rapidapi.com/submissions?base64_encoded=false&wait=true",
        {
          language_id: languageId,
          source_code: normalizedCode,
          stdin: userInput,
        },
        {
          headers: {
            "content-type": "application/json",
            "X-RapidAPI-Key": import.meta.env.VITE_JUDGE0_API_KEY,
            "X-RapidAPI-Host": "judge0-ce.p.rapidapi.com",
          },
        }
      );

      const res = response.data;
      const result =
        res.stdout?.trim() ||
        res.compile_output?.trim() ||
        res.stderr?.trim() ||
        "No Output";
      setOutput(result);
    } catch (error) {
      if (error.response) {
        const data = error.response.data;
        setOutput(
          `Error ${error.response.status}: ` +
            (data.message ||
              data.stderr ||
              data.compile_output ||
              "Unknown error")
        );
      } else {
        setOutput("Error: " + error.message);
      }
    } finally {
      setIsLoading(false);
    }
  };
  useEffect(() => {
    console.log(languageId);
  }, [languageId]);

  return (
    <div className="max-w-4xl mx-auto mt-8 p-6 bg-gray-900 text-white rounded-lg shadow-lg">
      <EditorContextAPI.Provider
        value={{ setLanguageId, sourceCode, languageId, downloadCode }}
      >
        <UtilBar />
      </EditorContextAPI.Provider>

      <textarea
        rows="12"
        id="codeArea"
        className="w-full p-4 mb-4 rounded-lg bg-gray-800 border border-gray-700 font-mono text-sm resize-y focus:outline-none focus:ring-2 focus:ring-green-400"
        placeholder="Type your code here..."
        value={sourceCode}
        onChange={(e) => setSourceCode(e.target.value)}
        
        onKeyDown={(e) => {
          const ta = e.target;
          const start = ta.selectionStart;
          const end = ta.selectionEnd;
          const val = ta.value;
          const IND = "    "; // 4 spaces

          const braceLangs = [50, 54, 62, 63, 74, 60, 68, 72];
          const isBraceLang = braceLangs.includes(languageId);
          const isPython = languageId === 71;

          // 1) TAB → insert indent
          if (e.key === "Tab") {
            e.preventDefault();
            const code = val.slice(0, start) + IND + val.slice(end);
            setSourceCode(code);
            setTimeout(() => {
              ta.selectionStart = ta.selectionEnd = start + IND.length;
            }, 0);
            return;
          }

          // 2) Auto‑close & wrap selection
          const pairs = { "(": ")", "[": "]", "{": "}", '"': '"', "'": "'" };
          if (pairs[e.key]) {
            e.preventDefault();

            const open = e.key;
            const close = pairs[open];
            let code, newPos;

            if (start !== end) {
              // WRAP the selected text
              const selected = val.slice(start, end);
              code =
                val.slice(0, start) + open + selected + close + val.slice(end);
              newPos = end + 2; // place after the closing
            } else {
              // INSERT empty pair
              code = val.slice(0, start) + open + close + val.slice(end);
              newPos = start + 1; // between them
            }

            setSourceCode(code);
            setTimeout(() => {
              ta.selectionStart = ta.selectionEnd = newPos;
            }, 0);
            return;
          }

          // 3) ENTER → smart indent
          if (e.key === "Enter") {
            e.preventDefault();

            // 3a) determine current line indent
            const lineStart = val.lastIndexOf("\n", start - 1) + 1;
            const lineText = val.slice(lineStart, start);
            const currIndent = (lineText.match(/^\s*/) || [""])[0];

            // 3b) should we add extra indent?
            let extra = "";
            if (isBraceLang && val[start - 1] === "{") extra = IND;
            if (isPython && lineText.trimEnd().endsWith(":")) extra = IND;

            let newCode, cursorOffset;
            if (extra && isBraceLang) {
              // e.g. after `{` in C/Java…
              newCode =
                val.slice(0, start) +
                `\n${currIndent}${extra}\n${currIndent}}` +
                val.slice(end);
              cursorOffset = currIndent.length + extra.length + 1;
            } else {
              // Python colon or plain newline
              newCode =
                val.slice(0, start) +
                `\n${currIndent}${extra}` +
                val.slice(end);
              cursorOffset = currIndent.length + extra.length + 1;
            }

            setSourceCode(newCode);
            setTimeout(() => {
              ta.selectionStart = ta.selectionEnd = start + cursorOffset;
            }, 0);
          }
        }}
      />

      <input
        type="text"
        className="w-full p-3 mb-4 rounded-lg bg-gray-800 border border-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-green-400"
        placeholder="Enter input (stdin)"
        value={userInput}
        onChange={(e) => setUserInput(e.target.value)}
      />

      <button
        onClick={handleSubmit}
        className={`w-full py-3 text-lg font-semibold rounded-lg ${
          isLoading
            ? "bg-green-800 cursor-not-allowed"
            : "bg-green-500 hover:bg-green-600"
        }`}
        disabled={isLoading}
      >
        {isLoading ? "⏳ Running..." : "▶️ Run Code"}
      </button>

      <div className="mt-6">
        <h3 className="text-xl font-semibold mb-2 text-gray-300">🧾 Output:</h3>
        <pre className="bg-gray-800 border border-gray-700 text-green-300 p-4 rounded-lg whitespace-pre-wrap min-h-[120px]">
          {output}
        </pre>
      </div>
    </div>
  );
};

export default Judge0CodeEditor;
