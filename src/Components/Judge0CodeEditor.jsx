import React, { useState, useEffect } from "react";
import axios from "axios";
import "../index.css";
import UtilBar from "./UtilBar";
import { EditorContextAPI } from "./EditorContextAPI";
import { getTemplate } from "./LanguageTemplates";

const Judge0CodeEditor = ({ 
  initialCode = "", 
  testCases = [], 
  hiddenTestCases = [], 
  languageId: propLanguageId, 
  setLanguageId: propSetLanguageId, 
  setEditorCode: propSetEditorCode, 
  functionName: propFunctionName, 
  parameters: propParameters = [],
  problemId,
  onSubmissionSuccess
}) => {
  // Use controlled or internal state for languageId
  const [internalLanguageId, setInternalLanguageId] = useState(62);
  const languageId = propLanguageId !== undefined ? propLanguageId : internalLanguageId;
  const setLanguageId = propSetLanguageId || setInternalLanguageId;

  // Use controlled or internal state for code
  const [internalSourceCode, setInternalSourceCode] = useState(initialCode);
  const sourceCode = propSetEditorCode ? initialCode : internalSourceCode;
  const setSourceCode = propSetEditorCode || setInternalSourceCode;

  const [output, setOutput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [results, setResults] = useState([]); // [{input, expected, actual, passed}]
  const [showMainWarning, setShowMainWarning] = useState(false);
  const [userInput, setUserInput] = useState("");
  const [submitResult, setSubmitResult] = useState(null); // {status, failedCase, passedCount, totalCount}
  const [showAccepted, setShowAccepted] = useState(false);
  const [submissionStatus, setSubmissionStatus] = useState(null); // Track submission status
  const [useCustomInput, setUseCustomInput] = useState(false);
  
  // New features
  const [startTime, setStartTime] = useState(null);
  const [elapsedTime, setElapsedTime] = useState(0);
  const [isTimerRunning, setIsTimerRunning] = useState(false);

  // Initialize timer when component mounts
  useEffect(() => {
    if (problemId && !startTime) {
      setStartTime(Date.now());
      setIsTimerRunning(true);
    }
  }, [problemId, startTime]);

  // Timer effect
  useEffect(() => {
    let interval = null;
    if (isTimerRunning) {
      interval = setInterval(() => {
        setElapsedTime(Date.now() - startTime);
      }, 1000);
    } else if (!isTimerRunning && elapsedTime !== 0) {
      clearInterval(interval);
    }
    return () => clearInterval(interval);
  }, [isTimerRunning, elapsedTime, startTime]);

  // Format time as MM:SS
  const formatTime = (ms) => {
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  };

  // Store code in localStorage when it changes
  useEffect(() => {
    if (problemId && sourceCode) {
      localStorage.setItem(`code_${problemId}_${languageId}`, sourceCode);
    }
  }, [sourceCode, problemId, languageId]);

  // Load stored code when component mounts or problemId changes
  useEffect(() => {
    if (problemId) {
      const storedCode = localStorage.getItem(`code_${problemId}_${languageId}`);
      if (storedCode && !propSetEditorCode) {
        setInternalSourceCode(storedCode);
      } else if (!storedCode && !propSetEditorCode) {
        // Only load template if no stored code exists
        if (!initialCode || initialCode.trim() === "") {
          const template = getTemplate(languageId);
          setInternalSourceCode(template);
        } else {
          setInternalSourceCode(initialCode);
        }
      }
    }
  }, [problemId, languageId]);

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

  // Helper to wrap user code for Java
  function generateJavaCode(userCode, functionName, testInput, parameters) {
    // Check if userCode contains a class definition
    const classMatch = userCode.match(/class\s+\w+/);
    const hasClass = !!classMatch;
    const hasMain = /public\s+static\s+void\s+main\s*\(/.test(userCode);
    let code = userCode;

    // Build argument preparation based on parameters
    const firstParamType = (parameters && parameters[0] && parameters[0].type) ? String(parameters[0].type) : "String";
    let argPrep = `        java.util.Scanner sc = new java.util.Scanner(System.in);
        StringBuilder __sb = new StringBuilder();
        while (sc.hasNextLine()) {
            if (__sb.length() > 0) __sb.append(' ');
            __sb.append(sc.nextLine());
        }
        String input = __sb.toString();
`;
    let argExpr = "input";
    if (/^String\[\]/.test(firstParamType)) {
      argPrep += `        String[] arr = input.trim().isEmpty() ? new String[0] : input.split("\\\\s+");
`;
      argExpr = "arr";
    }
    // If only a method, wrap in Main class
    if (!hasClass) {
      return `public class Main {\n${userCode}\n\n    public static void main(String[] args) {\n${argPrep}        System.out.println(new Main().${functionName}(${argExpr}));\n    }\n}`;
    }
    // If class exists but no main, inject main method
    if (hasClass && !hasMain) {
      // Find where to inject main (before last })
      const mainMethod = `\n    public static void main(String[] args) {\n${argPrep}        System.out.println(new Main().${functionName}(${argExpr}));\n    }\n`;
      const lastBrace = code.lastIndexOf("}");
      if (lastBrace !== -1) {
        code = code.slice(0, lastBrace) + mainMethod + code.slice(lastBrace);
      } else {
        code += mainMethod + "}";
      }
      // Rename class to Main if not already
      code = code.replace(/class\s+\w+/, "class Main");
      return code;
    }
    // If class and main both exist, just rename class to Main
    code = code.replace(/class\s+\w+/, "class Main");
    return code;
  }

  const functionName = propFunctionName || "myFunction";
  const parameters = propParameters;

  // Convert problem test-case input into stdin string based on first parameter type
  function buildStdinForParams(rawInput) {
    const firstParamType = (parameters && parameters[0] && parameters[0].type) ? String(parameters[0].type) : "String";
    // Handle String[] inputs: join tokens by whitespace
    if (/^String\[\]/.test(firstParamType)) {
      let arr = [];
      if (Array.isArray(rawInput)) {
        if (rawInput.length > 0 && Array.isArray(rawInput[0])) {
          arr = rawInput[0];
        } else {
          arr = rawInput;
        }
      } else if (typeof rawInput === 'string') {
        return rawInput;
      } else {
        return String(rawInput ?? "");
      }
      return arr.map(v => String(v)).join(' ');
    }
    // Default: pass raw string
    if (typeof rawInput === 'string') return rawInput;
    try { return JSON.stringify(rawInput); } catch { return String(rawInput ?? ""); }
  }

  // Simple delay
  const sleep = (ms) => new Promise((res) => setTimeout(res, ms));

  // Post to Judge0 with retry/backoff for rate limits
  async function postToJudge0(payload, maxRetries = 5) {
    let attempt = 0;
    let lastErr;
    while (attempt <= maxRetries) {
      try {
        return await axios.post(
          "https://judge0-ce.p.rapidapi.com/submissions?base64_encoded=false&wait=true",
          payload,
          {
            headers: {
              "content-type": "application/json",
              "X-RapidAPI-Key": import.meta.env.VITE_JUDGE0_API_KEY,
              "X-RapidAPI-Host": "judge0-ce.p.rapidapi.com",
            },
          }
        );
      } catch (error) {
        lastErr = error;
        const status = error?.response?.status;
        if (status === 429 || status === 503) {
          const backoffMs = Math.min(10000, 800 * Math.pow(2, attempt));
          await sleep(backoffMs);
          attempt++;
          continue;
        }
        throw error;
      }
    }
    throw lastErr;
  }

  const handleRunAll = async () => {
    // Check for main method in user code (Java only)
    if (languageId === 62 && /public\s+static\s+void\s+main\s*\(/.test(sourceCode)) {
      setShowMainWarning(true);
      return;
    }
    setIsLoading(true);
    setResults([]);
    setOutput("");
    try {
      const allResults = [];
      for (const tc of testCases) {
        let codeToRun = normalizedCode;
        if (languageId === 62) {
          codeToRun = generateJavaCode(sourceCode, functionName, tc.input, parameters);
        }
        const response = await postToJudge0({
          language_id: languageId,
          source_code: codeToRun,
          stdin: (buildStdinForParams(tc.input) ?? "") + "\n",
        });
        const res = response.data;
        const actual =
          res.stdout?.trim() ||
          res.compile_output?.trim() ||
          res.stderr?.trim() ||
          "No Output";
        const expected = String(tc.output).trim();
        const passed = actual === expected;
        allResults.push({ input: tc.input, expected, actual, passed });
        // small delay between calls to reduce 429
        await sleep(600);
      }
      setResults(allResults);
      setOutput("");
    } catch (error) {
      setOutput("Error running test cases: " + (error.message || "Unknown error"));
    } finally {
      setIsLoading(false);
    }
  };

  // Use test case mode only when we have a specific problem (problemId exists)
  const isTestCaseMode = !!problemId;

  // Old single-run handler for normal editor
  const handleSingleRun = async () => {
    setIsLoading(true);
    setOutput("");
    try {
      let codeToRun = normalizedCode;
      if (languageId === 62) {
        codeToRun = generateJavaCode(sourceCode, functionName, userInput, parameters);
      }
      const response = await postToJudge0({
        language_id: languageId,
        source_code: codeToRun,
        stdin: (buildStdinForParams(userInput) ?? "") + "\n",
      });
      const res = response.data;
      const result =
        res.stdout?.trim() ||
        res.compile_output?.trim() ||
        res.stderr?.trim() ||
        "No Output";
      setOutput(result);
    } catch (error) {
      setOutput("Error: " + (error.message || "Unknown error"));
    } finally {
      setIsLoading(false);
    }
  };

  // Helper to run a single test case (for submit)
  const runTestCase = async (codeToRun, input, expected) => {
    const response = await postToJudge0({
      language_id: languageId,
      source_code: codeToRun,
      stdin: (buildStdinForParams(input) ?? "") + "\n",
    });
    const res = response.data;
    const actual =
      res.stdout?.trim() ||
      res.compile_output?.trim() ||
      res.stderr?.trim() ||
      "No Output";
    const passed = actual === String(expected).trim();
    return { passed, actual };
  };

  // Submit handler
  const handleSubmitSolution = async (publicCases, hiddenCases) => {
    setIsLoading(true);
    setSubmitResult(null);
    setShowAccepted(false);
    let codeToRun = normalizedCode;
    if (languageId === 62) {
      codeToRun = generateJavaCode(sourceCode, functionName, "", parameters);
    }
    let passedCount = 0;
    let totalCount = (publicCases?.length || 0) + (hiddenCases?.length || 0);
    // 1. Check public test cases
    for (let i = 0; i < publicCases.length; i++) {
      const tc = publicCases[i];
      const { passed, actual } = await runTestCase(codeToRun, tc.input, tc.output);
      if (!passed) {
        setIsLoading(false);
        setSubmitResult({
          status: "failed",
          failedCase: { ...tc, actual, type: "public", index: i },
          passedCount,
          totalCount,
        });
        // Save failed submission
        await saveSubmission("WRONG_ANSWER", `Failed on public test case ${i + 1}: expected ${tc.output}, got ${actual}`);
        return;
      }
      passedCount++;
      await sleep(600);
    }
    // 2. Check hidden test cases
    for (let i = 0; i < (hiddenCases?.length || 0); i++) {
      const tc = hiddenCases[i];
      const { passed, actual } = await runTestCase(codeToRun, tc.input, tc.output);
      if (!passed) {
        setIsLoading(false);
        setSubmitResult({
          status: "failed",
          failedCase: { ...tc, actual, type: "hidden", index: i },
          passedCount,
          totalCount,
        });
        // Save failed submission
        await saveSubmission("WRONG_ANSWER", `Failed on hidden test case ${i + 1}: expected ${tc.output}, got ${actual}`);
        return;
      }
      passedCount++;
      await sleep(600);
    }
    // All passed
    setIsLoading(false);
    setSubmitResult({ status: "accepted", passedCount, totalCount });
    setShowAccepted(true);
    setTimeout(() => setShowAccepted(false), 2000);
    
    // Stop timer when problem is solved
    setIsTimerRunning(false);
    
    // Save successful submission
    await saveSubmission("ACCEPTED", "All test cases passed");
    
    // Call success callback
    if (onSubmissionSuccess) {
      onSubmissionSuccess();
    }
  };

  // Reset function
  const handleReset = () => {
    const template = getTemplate(languageId);
    setInternalSourceCode(template);
    setOutput("");
    setResults([]);
    setSubmitResult(null);
    setShowAccepted(false);
    setUserInput("");
    setSubmissionStatus(null);
    // Reset timer
    setStartTime(Date.now());
    setElapsedTime(0);
    setIsTimerRunning(true);
  };

  // Function to save submission to backend
  const saveSubmission = async (result, output) => {
    try {
      const token = localStorage.getItem('token');
      if (!token) {
        console.log("No token found, skipping submission save");
        return;
      }

      const languageNames = {
        50: "C", 54: "C++", 62: "Java", 71: "Python", 63: "JavaScript",
        74: "TypeScript", 60: "Go", 68: "PHP", 72: "Ruby"
      };

      const submissionData = {
        problemId: window.location.pathname.split('/').pop(), // Get problem ID from URL
        code: sourceCode,
        language: languageNames[languageId] || "Unknown",
        result: result,
        output: output
      };

      const response = await axios.post('/api/submissions', submissionData, {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });

      console.log('Submission saved:', response.data);
      setSubmissionStatus('saved');
      
    } catch (error) {
      console.error('Error saving submission:', error);
      setSubmissionStatus('error');
    }
  };

  useEffect(() => {
    setResults([]);
    setOutput("");
  }, [initialCode, Array.isArray(testCases) ? testCases.length : 0]);

  // Only show language selector and download button in test case mode
  const utilBarRow = (
    <div className="flex justify-between items-center w-full mb-2">
      <div className="flex items-center space-x-4">
        {/* Language Selector */}
        <select
          value={languageId}
          onChange={e => setLanguageId(Number(e.target.value))}
          className="bg-gray-700 text-white px-3 py-1 rounded text-sm focus:outline-none"
        >
          <option value={62}>Java</option>
          <option value={71}>Python</option>
          <option value={54}>C++</option>
          <option value={63}>JavaScript</option>
          <option value={50}>C</option>
          <option value={51}>C#</option>
          <option value={74}>TypeScript</option>
          <option value={60}>Go</option>
          <option value={68}>PHP</option>
          <option value={72}>Ruby</option>
          <option value={73}>Rust</option>
          <option value={78}>Kotlin</option>
          <option value={81}>Scala</option>
          <option value={83}>Swift</option>
        </select>
        
        {/* Timer */}
        <div className="flex items-center space-x-2">
          <span className="text-sm text-gray-400">Timer:</span>
          <span className={`text-sm font-mono px-2 py-1 rounded ${isTimerRunning ? 'bg-green-600 text-white' : 'bg-gray-600 text-gray-300'}`}>
            {formatTime(elapsedTime)}
          </span>
        </div>
      </div>
      
      <div className="flex items-center space-x-2">
        {/* Save Button */}
        <button
          onClick={() => downloadCode(sourceCode, languageId)}
          className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 shadow-md hover:shadow-lg flex items-center space-x-1"
          title="Save code as file"
        >
          <span>💾</span>
          <span>Save</span>
        </button>
        
        {/* Reset Button */}
        <button
          onClick={handleReset}
          className="bg-orange-600 hover:bg-orange-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 shadow-md hover:shadow-lg flex items-center space-x-1"
          title="Reset code to template"
        >
          <span>🔄</span>
          <span>Reset</span>
        </button>
        
        {/* Download Button */}
        <button
          onClick={() => downloadCode(sourceCode, languageId)}
          className="bg-green-600 hover:bg-green-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 shadow-md hover:shadow-lg flex items-center space-x-1"
          title="Download code"
        >
          <span>⬇️</span>
          <span>Download</span>
        </button>
      </div>
    </div>
  );

  return (
    <div className="max-w-4xl mx-auto mt-8 p-6 bg-gray-900 text-white rounded-lg shadow-lg">
      {/* Main method warning modal (only in test case mode) */}
      {isTestCaseMode && showMainWarning && (
        <div className="fixed inset-0 flex items-center justify-center bg-black bg-opacity-60 z-50">
          <div className="bg-white text-gray-900 rounded-lg shadow-lg p-8 max-w-md w-full text-center">
            <h2 className="text-xl font-bold mb-4">⚠️ Invalid Code Submission</h2>
            <p className="mb-4">You <b>cannot</b> include a <code>main</code> method in your code.<br/>Please only modify the given method and do not add a <code>main</code> function or class.</p>
            <button
              className="mt-4 px-6 py-2 bg-green-600 text-white rounded hover:bg-green-700 font-semibold"
              onClick={() => setShowMainWarning(false)}
            >
              OK
            </button>
          </div>
        </div>
      )}
      <EditorContextAPI.Provider
        value={{ setLanguageId, sourceCode, languageId, downloadCode, setSourceCode }}
      >
        {/* Show language selector and download in test case mode, UtilBar otherwise */}
        {isTestCaseMode ? utilBarRow : <UtilBar />}
      </EditorContextAPI.Provider>

      <textarea
        rows={isTestCaseMode ? 18 : 12}
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
              // INSERT empty pair - always side by side initially
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

            // Special case: cursor is between { and }
            if (
              isBraceLang &&
              val[start - 1] === "{" &&
              val[end] === "}"
            ) {
              // Insert indented newline and move closing brace to its own line
              const before = val.slice(0, start);
              const after = val.slice(end + 1); // skip the '}'
              const newCode =
                before +
                "\n" +
                currIndent + IND +
                "\n" +
                currIndent + "}" +
                after;
              // Find the position of the new indented line
              const searchStr = "\n" + currIndent + IND;
              const cursorPos = before.length + searchStr.length;
              setSourceCode(newCode);
              setTimeout(() => {
                ta.selectionStart = ta.selectionEnd = cursorPos;
              }, 0);
              return;
            }

            let newCode, cursorOffset;
            if (extra && isBraceLang) {
              // Check if there's already a closing brace after the cursor
              const afterCursor = val.slice(end);
              const nextChar = afterCursor.trim()[0];
              
              if (nextChar === "}") {
                // There's already a closing brace, just add indented line
                newCode =
                  val.slice(0, start) +
                  `\n${currIndent}${extra}` +
                  val.slice(end);
                cursorOffset = currIndent.length + extra.length + 1;
              } else {
                // No closing brace, add both indented line and closing brace
                newCode =
                  val.slice(0, start) +
                  `\n${currIndent}${extra}\n${currIndent}}` +
                  val.slice(end);
                cursorOffset = currIndent.length + extra.length + 1;
              }
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

      {/* Normal editor mode: single input/output */}
      {!isTestCaseMode && (
        <>
          <input
            type="text"
            className="w-full p-3 mb-4 rounded-lg bg-gray-800 border border-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-green-400"
            placeholder="Enter input (stdin)"
            value={userInput}
            onChange={(e) => setUserInput(e.target.value)}
          />
          <button
            onClick={handleSingleRun}
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
              {output ? output : "Output will appear here."}
            </pre>
          </div>
        </>
      )}

      {/* Test case mode: run all test cases */}
      {isTestCaseMode && (
        <>
          <div>
            {/* Editor stays above */}
          </div>
          
          {/* Custom input checkbox and input field */}
          <div className="mt-4">
            <label className="flex items-center space-x-2 mb-3">
              <input
                type="checkbox"
                checked={useCustomInput}
                onChange={(e) => setUseCustomInput(e.target.checked)}
                className="w-4 h-4 text-blue-600 bg-gray-700 border-gray-600 rounded focus:ring-blue-500 focus:ring-2"
              />
              <span className="text-sm text-gray-300">Use custom input for testing</span>
            </label>
            
            {useCustomInput && (
              <input
                type="text"
                className="w-full p-3 mb-4 rounded-lg bg-gray-800 border border-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-green-400"
                placeholder="Enter custom input (stdin)"
                value={userInput}
                onChange={(e) => setUserInput(e.target.value)}
              />
            )}
          </div>
          
          <div className="flex gap-2 mt-4">
            <button
              onClick={useCustomInput ? handleSingleRun : (testCases && testCases.length > 0 ? handleRunAll : handleSingleRun)}
              className={`flex-1 py-2 px-4 text-base font-semibold rounded bg-green-500 hover:bg-green-600 disabled:bg-green-800 disabled:cursor-not-allowed`}
              disabled={isLoading}
            >
              {isLoading ? "⏳ Running..." : "▶️ Run"}
            </button>
            <button
              onClick={() => handleSubmitSolution(testCases || [], hiddenTestCases || [])}
              className={`flex-1 py-2 px-4 text-base font-semibold rounded bg-blue-500 hover:bg-blue-600 disabled:bg-blue-800 disabled:cursor-not-allowed`}
              disabled={isLoading}
            >
              {isLoading ? "⏳ Submitting..." : "🚀 Submit"}
            </button>
          </div>
          {output && (
            <div className="mt-6">
              <h3 className="text-xl font-semibold mb-2 text-gray-300">🧾 Output:</h3>
              <pre className="bg-gray-800 border border-gray-700 text-green-300 p-4 rounded-lg whitespace-pre-wrap min-h-[120px]">
                {output}
              </pre>
            </div>
          )}
          {results.length > 0 && (
            <div className="mt-6">
              <h3 className="text-xl font-semibold mb-2 text-gray-300">🧪 Public Test Case Results:</h3>
              <ul className="space-y-3">
                {results.map((r, idx) => (
                  <li
                    key={idx}
                    className={`p-4 rounded-lg border text-sm whitespace-pre-wrap ${
                      r.passed
                        ? "bg-green-900 border-green-600 text-green-200"
                        : "bg-red-900 border-red-600 text-red-200"
                    }`}
                  >
                    <div className="font-semibold">
                      Test Case {idx + 1}: {r.passed ? "Passed" : "Failed"}
                    </div>
                    <div><span className="font-bold">Input:</span> <pre className="inline whitespace-pre-wrap">{r.input}</pre></div>
                    <div><span className="font-bold">Expected Output:</span> <pre className="inline whitespace-pre-wrap">{r.expected}</pre></div>
                    <div><span className="font-bold">Your Output:</span> <pre className="inline whitespace-pre-wrap">{r.actual}</pre></div>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {/* Submit result panel */}
          {submitResult && (
            <div className="mt-6">
              {submitResult.status === "accepted" ? (
                <div className="flex flex-col items-center justify-center">
                  {showAccepted && (
                    <div className="animate-bounce text-4xl mb-2">🎉</div>
                  )}
                  <div className="text-green-400 text-xl font-bold">Solution Accepted!</div>
                  <div className="text-green-200 mt-2">All {submitResult.totalCount} test cases passed.</div>
                </div>
              ) : (
                <div className="bg-red-900 border border-red-600 text-red-200 p-4 rounded-lg">
                  <div className="font-bold mb-2">Test Case Failed ({submitResult.failedCase.type === "public" ? "Public" : "Hidden"} #{submitResult.failedCase.index + 1})</div>
                  <div><span className="font-bold">Input:</span> <pre className="inline whitespace-pre-wrap">{submitResult.failedCase.input}</pre></div>
                  <div><span className="font-bold">Expected Output:</span> <pre className="inline whitespace-pre-wrap">{submitResult.failedCase.output}</pre></div>
                  <div><span className="font-bold">Your Output:</span> <pre className="inline whitespace-pre-wrap">{submitResult.failedCase.actual}</pre></div>
                  <div className="mt-2">Passed {submitResult.passedCount} out of {submitResult.totalCount} test cases.</div>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default Judge0CodeEditor;
