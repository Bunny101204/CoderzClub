import React, { useState, useEffect } from "react";
import axios from "axios";
import "../index.css";
import UtilBar from "./UtilBar";
import { EditorContextAPI } from "./EditorContextAPI";
import { getTemplate } from "./LanguageTemplates";
// Syntax highlighting - will be added as overlay if needed
// import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
// import { vscDarkPlus, vs } from 'react-syntax-highlighter/dist/esm/styles/prism';

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
  executionMode = "STDIN_STDOUT",
  onSubmissionSuccess,
}) => {
  // Debug: Log props on mount
  React.useEffect(() => {
    console.log("=== Judge0CodeEditor mounted ===");
    console.log("testCases:", testCases);
    console.log("testCases.length:", testCases?.length);
    console.log("hiddenTestCases:", hiddenTestCases);
    console.log("problemId:", problemId);
    console.log("executionMode:", executionMode);
  }, []);
  // Use controlled or internal state for languageId
  const [internalLanguageId, setInternalLanguageId] = useState(62);
  const languageId =
    propLanguageId !== undefined ? propLanguageId : internalLanguageId;
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
  const [executionTime, setExecutionTime] = useState(null); // Execution time in ms
  const [executionMemory, setExecutionMemory] = useState(null); // Execution memory in bytes
  const [errorDetails, setErrorDetails] = useState(null); // Detailed error info
  const [copySuccess, setCopySuccess] = useState(false); // Copy to clipboard success
  const [isDarkTheme, setIsDarkTheme] = useState(true); // Theme state

  // New features
  const [startTime, setStartTime] = useState(null);
  const [elapsedTime, setElapsedTime] = useState(0);
  const [isTimerRunning, setIsTimerRunning] = useState(false);

  //for line numbers in editor
  const [lineCount, setLineCount] = useState(1);
  const textareaRef = React.useRef(null);
  const lineRef = React.useRef(null);

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

  //for line change in editor
  useEffect(() => {
    const lines = sourceCode.split("\n").length;
    setLineCount(lines);
  }, [sourceCode]);

  // Format time as MM:SS
  const formatTime = (ms) => {
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes.toString().padStart(2, "0")}:${seconds
      .toString()
      .padStart(2, "0")}`;
  };

  //for numbers in code editor
  const syncScroll = () => {
    if (textareaRef.current && lineRef.current) {
      lineRef.current.scrollTop = textareaRef.current.scrollTop;
    }
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
      const storedCode = localStorage.getItem(
        `code_${problemId}_${languageId}`
      );
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
    const firstParamType =
      parameters && parameters[0] && parameters[0].type
        ? String(parameters[0].type)
        : "String";
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
    const firstParamType =
      parameters && parameters[0] && parameters[0].type
        ? String(parameters[0].type)
        : "String";
    // Handle String[] inputs: join tokens by whitespace
    if (/^String\[\]/.test(firstParamType)) {
      let arr = [];
      if (Array.isArray(rawInput)) {
        if (rawInput.length > 0 && Array.isArray(rawInput[0])) {
          arr = rawInput[0];
        } else {
          arr = rawInput;
        }
      } else if (typeof rawInput === "string") {
        return rawInput;
      } else {
        return String(rawInput ?? "");
      }
      return arr.map((v) => String(v)).join(" ");
    }
    // Default: pass raw string
    if (typeof rawInput === "string") return rawInput;
    try {
      return JSON.stringify(rawInput);
    } catch {
      return String(rawInput ?? "");
    }
  }

  // Simple delay
  const sleep = (ms) => new Promise((res) => setTimeout(res, ms));
  
  // Copy to clipboard function
  const copyToClipboard = async (text) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopySuccess(true);
      setTimeout(() => setCopySuccess(false), 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
      // Fallback for older browsers
      const textArea = document.createElement('textarea');
      textArea.value = text;
      textArea.style.position = 'fixed';
      textArea.style.opacity = '0';
      document.body.appendChild(textArea);
      textArea.select();
      try {
        document.execCommand('copy');
        setCopySuccess(true);
        setTimeout(() => setCopySuccess(false), 2000);
      } catch (e) {
        console.error('Fallback copy failed:', e);
      }
      document.body.removeChild(textArea);
    }
  };
  
  // Format memory
  const formatMemory = (bytes) => {
    if (!bytes) return 'N/A';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(2)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };
  
  // Format time
  const formatExecutionTime = (ms) => {
    if (!ms) return 'N/A';
    if (ms < 1000) return `${ms} ms`;
    return `${(ms / 1000).toFixed(2)} s`;
  };
  
  // Get error type and message from Judge0 response
  const parseError = (res) => {
    if (!res) return null;
    
    const statusId = res.status?.id;
    const statusDescription = res.status?.description;
    
    // Compilation error
    if (statusId === 6 || res.compile_output) {
      return {
        type: 'Compilation Error',
        message: res.compile_output || res.stderr || 'Compilation failed',
        details: res.message || statusDescription
      };
    }
    
    // Runtime error
    if (statusId >= 7 && statusId <= 12) {
      return {
        type: 'Runtime Error',
        message: res.stderr || res.message || 'Runtime error occurred',
        details: statusDescription || `Error code: ${statusId}`
      };
    }
    
    // Time limit exceeded
    if (statusId === 5) {
      return {
        type: 'Time Limit Exceeded',
        message: 'Your code took too long to execute',
        details: 'Please optimize your algorithm'
      };
    }
    
    // Memory limit exceeded (if available)
    if (res.memory && res.memory > 100000000) { // 100MB
      return {
        type: 'Memory Limit Exceeded',
        message: 'Your code used too much memory',
        details: `Used: ${formatMemory(res.memory)}`
      };
    }
    
    return null;
  };

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
    console.log("=== handleRunAll called ===");
    console.log("testCases:", testCases);
    console.log("testCases length:", testCases?.length);
    console.log("languageId:", languageId);
    console.log("sourceCode length:", sourceCode?.length);
    
    // Check if we have test cases
    if (!testCases || testCases.length === 0) {
      console.warn("No test cases available!");
      setOutput("No test cases available for this problem.");
      return;
    }
    
    // REMOVED: Main method validation - users can submit full programs OR function-only code
    // The platform supports both STDIN_STDOUT mode (full programs) and function-based mode
    
    setIsLoading(true);
    setResults([]);
    setOutput("");
    setSubmitResult(null); // Clear submit results when running
    setExecutionTime(null);
    setExecutionMemory(null);
    setErrorDetails(null);
    
    try {
      const allResults = [];
      console.log(`Running ${testCases.length} test cases...`);
      
      for (let i = 0; i < testCases.length; i++) {
        const tc = testCases[i];
        console.log(`Test case ${i + 1}:`, { input: tc.input, expected: tc.output });
        
        // For STDIN_STDOUT mode, use source code directly (no main method required)
        // Users write complete programs that read from stdin and write to stdout
        let codeToRun = sourceCode;
        
        // Only wrap Java code if we have function-based problem (old mode, not STDIN_STDOUT)
        if (languageId === 62 && functionName && parameters && parameters.length > 0 && executionMode !== "STDIN_STDOUT") {
          codeToRun = generateJavaCode(
            sourceCode,
            functionName,
            tc.input,
            parameters
          );
        }
        
        const stdinInput = typeof tc.input === 'string' ? tc.input : (buildStdinForParams(tc.input) ?? "");
        console.log(`Sending to Judge0 - stdin:`, stdinInput.substring(0, 100));
        
        const response = await postToJudge0({
          language_id: languageId,
          source_code: codeToRun,
          stdin: stdinInput + "\n",
        });
        
        const res = response.data;
        console.log(`Judge0 response ${i + 1}:`, {
          status: res.status?.id,
          stdout: res.stdout?.substring(0, 50),
          stderr: res.stderr?.substring(0, 50),
          time: res.time,
          memory: res.memory,
        });
        
        // Extract execution metrics
        const runtime = res.time ? Math.round(parseFloat(res.time) * 1000) : null;
        const memory = res.memory ? res.memory * 1024 : null;
        
        // Parse errors - check status first
        const error = parseError(res);
        
        // Determine actual output - prioritize stdout, then stderr, then compile_output
        let actual = "";
        if (res.stdout && res.stdout.trim()) {
          actual = res.stdout.trim();
        } else if (res.stderr && res.stderr.trim()) {
          actual = res.stderr.trim();
        } else if (res.compile_output && res.compile_output.trim()) {
          actual = res.compile_output.trim();
        } else {
          actual = "No Output";
        }
        
        const expected = String(tc.output || "").trim();
        // Only pass if no error AND output matches
        const passed = !error && actual === expected;
        
        console.log(`Test ${i + 1} result:`, { actual, expected, passed, runtime, memory, error });
        
        allResults.push({ 
          input: tc.input || "", 
          expected: expected || "", 
          actual: actual || "No Output", 
          passed,
          runtime,
          memory,
          error,
          statusId: res.status?.id,
          statusDescription: res.status?.description
        });
        
        // Update execution metrics (use max values)
        if (runtime && (!executionTime || runtime > executionTime)) {
          setExecutionTime(runtime);
        }
        if (memory && (!executionMemory || memory > executionMemory)) {
          setExecutionMemory(memory);
        }
        
        // Set error details if any
        if (error) {
          setErrorDetails(error);
        }
        
        // small delay between calls to reduce 429
        await sleep(600);
      }
      
      console.log("All results:", allResults);
      setResults(allResults);
      setOutput(""); // Clear output since we're showing results instead
      
      // Force a re-render check
      console.log("Results state set, length:", allResults.length);
      
    } catch (error) {
      console.error("Error in handleRunAll:", error);
      setOutput(
        "Error running test cases: " + (error.message || "Unknown error")
      );
      setResults([]); // Clear results on error
    } finally {
      setIsLoading(false);
      console.log("handleRunAll completed, isLoading set to false");
    }
  };

  // Use test case mode only when we have a specific problem (problemId exists)
  const isTestCaseMode = !!problemId;

  // Old single-run handler for normal editor
  const handleSingleRun = async () => {
    setIsLoading(true);
    setOutput("");
    setErrorDetails(null);
    setExecutionTime(null);
    setExecutionMemory(null);
    try {
      // For STDIN_STDOUT mode, use source code directly (no main method required)
      let codeToRun = sourceCode;
      
      // Only wrap Java code if we have function-based problem (old mode)
      if (languageId === 62 && functionName && parameters && parameters.length > 0 && executionMode !== "STDIN_STDOUT") {
        codeToRun = generateJavaCode(
          sourceCode,
          functionName,
          userInput,
          parameters
        );
      }
      
      const stdinInput = useCustomInput ? userInput : (buildStdinForParams(userInput) ?? "");
      
      const response = await postToJudge0({
        language_id: languageId,
        source_code: codeToRun,
        stdin: stdinInput + "\n",
      });
      
      const res = response.data;
      
      // Extract execution metrics
      const runtime = res.time ? Math.round(parseFloat(res.time) * 1000) : null;
      const memory = res.memory ? res.memory * 1024 : null;
      setExecutionTime(runtime);
      setExecutionMemory(memory);
      
      // Parse errors
      const error = parseError(res);
      setErrorDetails(error);
      
      // Determine output
      let result = "";
      if (res.stdout) {
        result = res.stdout.trim();
      } else if (res.compile_output) {
        result = res.compile_output.trim();
      } else if (res.stderr) {
        result = res.stderr.trim();
      } else {
        result = "No Output";
      }
      
      // Add status message
      if (error) {
        result = `[${error.type}]\n\n${result}`;
      } else if (res.status?.id === 3) {
        result = `[Success]\n\n${result}`;
      }
      
      setOutput(result);
    } catch (error) {
      setOutput("Error: " + (error.message || "Unknown error"));
      setErrorDetails({
        type: 'Execution Error',
        message: error.message || 'Unknown error occurred',
        details: 'Please check your code and try again'
      });
    } finally {
      setIsLoading(false);
    }
  };

  // Helper to run a single test case (for submit)
  const runTestCase = async (codeToRun, input, expected) => {
    // Input is already a string for STDIN_STDOUT mode
    const stdinInput = typeof input === 'string' ? input : (buildStdinForParams(input) ?? "");
    
    const response = await postToJudge0({
      language_id: languageId,
      source_code: codeToRun,
      stdin: stdinInput + "\n",
    });
    const res = response.data;
    
    // Parse error first
    const error = parseError(res);
    
    // Determine actual output - prioritize stdout
    let actual = "";
    if (res.stdout && res.stdout.trim()) {
      actual = res.stdout.trim();
    } else if (res.stderr && res.stderr.trim()) {
      actual = res.stderr.trim();
    } else if (res.compile_output && res.compile_output.trim()) {
      actual = res.compile_output.trim();
    } else {
      actual = "No Output";
    }
    
    const expectedStr = String(expected || "").trim();
    // Only pass if no error AND output matches
    const passed = !error && actual === expectedStr;
    
    return { 
      passed, 
      actual, 
      runtime: res.time ? Math.round(parseFloat(res.time) * 1000) : null, // Convert to ms
      memory: res.memory ? res.memory * 1024 : null, // Convert to bytes
      statusId: res.status?.id,
      statusDescription: res.status?.description || '',
      stderr: res.stderr || res.compile_output || '',
      errorMessage: res.message || '',
      error: error, // Include parsed error object
      fullResponse: res
    };
  };

  // Submit handler
  const handleSubmitSolution = async (publicCases, hiddenCases) => {
    // Check submission limits before submitting
    try {
      const token = localStorage.getItem("token");
      if (token) {
        const limitsResponse = await axios.get(`/api/submissions/limits?problemId=${problemId}`, {
          headers: { Authorization: `Bearer ${token}` }
        });
        const limits = limitsResponse.data;
        
        if (!limits.canSubmitNow) {
          const cooldown = limits.cooldownSeconds || 0;
          setOutput(`⏳ Please wait ${cooldown} seconds before submitting again.`);
          setErrorDetails({
            type: 'Rate Limit',
            message: `You must wait ${cooldown} seconds between submissions.`,
            details: 'This helps prevent abuse and ensures fair usage.'
          });
          return;
        }
        
        if (limits.hasExceededDailyLimit) {
          setOutput(`❌ Daily submission limit exceeded. You have ${limits.remainingDaily} submissions remaining today.`);
          setErrorDetails({
            type: 'Daily Limit Exceeded',
            message: `You have exceeded your daily submission limit of ${limits.dailyLimit}.`,
            details: `Remaining submissions: ${limits.remainingDaily}`
          });
          return;
        }
        
        if (limits.hasExceededProblemLimit) {
          setOutput(`❌ Problem submission limit exceeded. You have ${limits.remainingProblem} submissions remaining for this problem today.`);
          setErrorDetails({
            type: 'Problem Limit Exceeded',
            message: `You have exceeded your submission limit for this problem (${limits.problemLimit} per day).`,
            details: `Remaining submissions: ${limits.remainingProblem}`
          });
          return;
        }
      }
    } catch (error) {
      console.warn("Could not check submission limits:", error);
      // Continue with submission if limit check fails (don't block user)
    }
    
    setIsLoading(true);
    setSubmitResult(null);
    setShowAccepted(false);
    setResults([]); // Clear previous run results when submitting
    setOutput(""); // Clear output when submitting
    setExecutionTime(null);
    setExecutionMemory(null);
    setErrorDetails(null);
    
    // For STDIN_STDOUT mode, use source code directly (no main method required)
    let codeToRun = sourceCode;
    
    // Only wrap Java code if we have function-based problem (old mode)
    if (languageId === 62 && functionName && parameters && parameters.length > 0 && executionMode !== "STDIN_STDOUT") {
      codeToRun = generateJavaCode(sourceCode, functionName, "", parameters);
    }
    let passedCount = 0;
    let totalCount = (publicCases?.length || 0) + (hiddenCases?.length || 0);
    let allTestResults = [];
    let maxRuntime = 0;
    let maxMemory = 0;
    let lastStatusId = null;
    let lastStderr = '';
    let lastErrorMessage = '';
    let lastFullResponse = null;
    
    // 1. Check public test cases
    for (let i = 0; i < publicCases.length; i++) {
      const tc = publicCases[i];
      const stdinInput = typeof tc.input === 'string' ? tc.input : (buildStdinForParams(tc.input) ?? "");
      const result = await runTestCase(
        codeToRun,
        stdinInput,
        tc.output
      );
      allTestResults.push(result);
      
      if (result.runtime) maxRuntime = Math.max(maxRuntime, result.runtime);
      if (result.memory) maxMemory = Math.max(maxMemory, result.memory);
      lastStatusId = result.statusId;
      if (result.stderr) lastStderr = result.stderr;
      if (result.errorMessage) lastErrorMessage = result.errorMessage;
      lastFullResponse = result.fullResponse;
      
      if (!result.passed) {
        setIsLoading(false);
        // Determine failure reason
        let failureReason = "WRONG_ANSWER";
        let failureMessage = `Failed on public test case ${i + 1}: expected ${tc.output}, got ${result.actual}`;
        
        if (result.error) {
          if (result.error.type === "Compilation Error") {
            failureReason = "COMPILATION_ERROR";
            failureMessage = `Compilation error on public test case ${i + 1}: ${result.error.message}`;
          } else if (result.error.type === "Runtime Error") {
            failureReason = "RUNTIME_ERROR";
            failureMessage = `Runtime error on public test case ${i + 1}: ${result.error.message}`;
          } else if (result.error.type === "Time Limit Exceeded") {
            failureReason = "TIME_LIMIT_EXCEEDED";
            failureMessage = `Time limit exceeded on public test case ${i + 1}`;
          }
        }
        
        const failedResult = {
          status: "failed",
          failedCase: { ...tc, actual: result.actual, type: "public", index: i, error: result.error },
          passedCount,
          totalCount,
          failureReason,
        };
        setSubmitResult(failedResult);
        setErrorDetails(result.error); // Show error details
        console.log("Submit failed:", failedResult);
        // Save failed submission with details
        await saveSubmission(
          failureReason,
          failureMessage,
          result.runtime,
          result.memory,
          result.statusId,
          result.stderr,
          result.errorMessage,
          passedCount,
          totalCount,
          result.fullResponse
        );
        return;
      }
      passedCount++;
      await sleep(600);
    }
    // 2. Check hidden test cases
    for (let i = 0; i < (hiddenCases?.length || 0); i++) {
      const tc = hiddenCases[i];
      const stdinInput = typeof tc.input === 'string' ? tc.input : (buildStdinForParams(tc.input) ?? "");
      const result = await runTestCase(
        codeToRun,
        stdinInput,
        tc.output
      );
      allTestResults.push(result);
      
      if (result.runtime) maxRuntime = Math.max(maxRuntime, result.runtime);
      if (result.memory) maxMemory = Math.max(maxMemory, result.memory);
      lastStatusId = result.statusId;
      if (result.stderr) lastStderr = result.stderr;
      if (result.errorMessage) lastErrorMessage = result.errorMessage;
      lastFullResponse = result.fullResponse;
      
      if (!result.passed) {
        setIsLoading(false);
        // Determine failure reason
        let failureReason = "WRONG_ANSWER";
        let failureMessage = `Failed on hidden test case ${i + 1}: expected ${tc.output}, got ${result.actual}`;
        
        if (result.error) {
          if (result.error.type === "Compilation Error") {
            failureReason = "COMPILATION_ERROR";
            failureMessage = `Compilation error on hidden test case ${i + 1}: ${result.error.message}`;
          } else if (result.error.type === "Runtime Error") {
            failureReason = "RUNTIME_ERROR";
            failureMessage = `Runtime error on hidden test case ${i + 1}: ${result.error.message}`;
          } else if (result.error.type === "Time Limit Exceeded") {
            failureReason = "TIME_LIMIT_EXCEEDED";
            failureMessage = `Time limit exceeded on hidden test case ${i + 1}`;
          }
        }
        
        setSubmitResult({
          status: "failed",
          failedCase: { ...tc, actual: result.actual, type: "hidden", index: i, error: result.error },
          passedCount,
          totalCount,
          failureReason,
        });
        setErrorDetails(result.error); // Show error details
        // Save failed submission with details
        await saveSubmission(
          failureReason,
          failureMessage,
          result.runtime,
          result.memory,
          result.statusId,
          result.stderr,
          result.errorMessage,
          passedCount,
          totalCount,
          result.fullResponse
        );
        return;
      }
      passedCount++;
      await sleep(600);
    }
    // All passed
    setIsLoading(false);
    
    // Update execution metrics for display
    if (maxRuntime > 0) setExecutionTime(maxRuntime);
    if (maxMemory > 0) setExecutionMemory(maxMemory);
    
    const acceptedResult = { status: "accepted", passedCount, totalCount };
    setSubmitResult(acceptedResult);
    console.log("Submit accepted:", acceptedResult);
    setShowAccepted(true);
    setTimeout(() => setShowAccepted(false), 2000);

    // Stop timer when problem is solved
    setIsTimerRunning(false);

    // Save successful submission with details
    await saveSubmission(
      "ACCEPTED", 
      "All test cases passed",
      maxRuntime,
      maxMemory,
      lastStatusId || 3, // 3 = Accepted in Judge0
      lastStderr,
      lastErrorMessage,
      passedCount,
      totalCount,
      lastFullResponse
    );

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

  // Function to save submission to backend with enhanced details
  const saveSubmission = async (
    result, 
    output, 
    runtime = null, 
    memory = null, 
    statusId = null,
    stderr = '',
    errorMessage = '',
    passedTestCases = 0,
    totalTestCases = 0,
    executionDetails = null
  ) => {
    try {
      const token = localStorage.getItem("token");
      if (!token) {
        console.log("No token found, skipping submission save");
        return;
      }

      const languageNames = {
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

      // Get problem ID from URL or props
      const problemIdFromUrl = window.location.pathname.split("/").pop();
      const problemId = problemId || problemIdFromUrl;

      const submissionData = {
        problemId: problemId,
        code: sourceCode,
        language: languageNames[languageId] || "Unknown",
        result: result,
        output: output,
        runtime: runtime,
        memory: memory,
        statusId: statusId,
        stderr: stderr,
        errorMessage: errorMessage,
        passedTestCases: passedTestCases,
        totalTestCases: totalTestCases,
        executionDetails: executionDetails
      };

      const response = await axios.post("/api/submissions", submissionData, {
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
      });

      console.log("Submission saved with details:", response.data);
      setSubmissionStatus("saved");
    } catch (error) {
      console.error("Error saving submission:", error);
      
      // Handle rate limiting and submission limit errors
      if (error.response && error.response.status === 429) {
        const errorData = error.response.data;
        if (errorData && errorData.error) {
          if (errorData.error === "RATE_LIMIT_EXCEEDED") {
            const cooldown = errorData.cooldownSeconds || 0;
            setErrorDetails({
              type: 'Rate Limit Exceeded',
              message: `Please wait ${cooldown} seconds before submitting again.`,
              details: 'This helps prevent abuse and ensures fair usage.'
            });
          } else if (errorData.error === "DAILY_LIMIT_EXCEEDED") {
            setErrorDetails({
              type: 'Daily Limit Exceeded',
              message: `You have exceeded your daily submission limit of ${errorData.limit || 100}.`,
              details: 'Please try again tomorrow.'
            });
          } else if (errorData.error === "PROBLEM_LIMIT_EXCEEDED") {
            setErrorDetails({
              type: 'Problem Limit Exceeded',
              message: `You have exceeded your submission limit for this problem (${errorData.limit || 50} per day).`,
              details: 'Please try again tomorrow or work on other problems.'
            });
          }
        }
      }
      
      setSubmissionStatus("error");
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
          onChange={(e) => setLanguageId(Number(e.target.value))}
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
          <span
            className={`text-sm font-mono px-2 py-1 rounded ${
              isTimerRunning
                ? "bg-green-600 text-white"
                : "bg-gray-600 text-gray-300"
            }`}
          >
            {formatTime(elapsedTime)}
          </span>
        </div>
      </div>

      <div className="flex items-center space-x-2">
        {/* Save Button */}
        <button
          onClick={() => downloadCode(sourceCode, languageId)}
          className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 shadow-md hover:shadow-lg"
          title="Save code as file"
        >
          Save
        </button>

        {/* Reset Button */}
        <button
          onClick={handleReset}
          className="bg-orange-600 hover:bg-orange-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 shadow-md hover:shadow-lg"
          title="Reset code to template"
        >
          Reset
        </button>
        
        {/* Theme Toggle */}
        <button
          onClick={() => setIsDarkTheme(!isDarkTheme)}
          className="bg-gray-700 hover:bg-gray-600 text-white px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 shadow-md hover:shadow-lg"
          title="Toggle theme"
        >
          {isDarkTheme ? 'Light' : 'Dark'}
        </button>
      </div>
    </div>
  );
  
  // Get language name for syntax highlighting
  const getLanguageName = (langId) => {
    const langMap = {
      50: 'c',
      54: 'cpp',
      62: 'java',
      71: 'python',
      63: 'javascript',
      74: 'typescript',
      60: 'go',
      68: 'php',
      72: 'ruby',
      73: 'rust',
      78: 'kotlin',
      81: 'scala',
      83: 'swift',
      51: 'csharp'
    };
    return langMap[langId] || 'text';
  };

  return (
    <div className="max-w-4xl mx-auto mt-8 p-6 bg-gray-900 text-white rounded-lg shadow-lg">
      {/* REMOVED: Main method warning modal - no longer needed as users can submit full programs */}
      <EditorContextAPI.Provider
        value={{
          setLanguageId,
          sourceCode,
          languageId,
          downloadCode,
          setSourceCode,
        }}
      >
        {/* Show language selector and download in test case mode, UtilBar otherwise */}
        {isTestCaseMode ? utilBarRow : <UtilBar />}
      </EditorContextAPI.Provider>
      <div className="relative flex mb-4 border border-gray-700 rounded-lg overflow-hidden">
        {/* Line Numbers */}
        <div
          ref={lineRef}
          className="bg-gray-800 text-gray-400 text-sm font-mono px-3 py-4 select-none overflow-hidden"
          style={{ minWidth: "48px" }}
        >
          {Array.from({ length: lineCount }, (_, i) => (
            <div key={i} className="leading-6 text-right">
              {i + 1}
            </div>
          ))}
        </div>
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
                  val.slice(0, start) +
                  open +
                  selected +
                  close +
                  val.slice(end);
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
              if (isBraceLang && val[start - 1] === "{" && val[end] === "}") {
                // Insert indented newline and move closing brace to its own line
                const before = val.slice(0, start);
                const after = val.slice(end + 1); // skip the '}'
                const newCode =
                  before +
                  "\n" +
                  currIndent +
                  IND +
                  "\n" +
                  currIndent +
                  "}" +
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
      </div>

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
          {output && (
            <div className="mt-6 p-4 rounded-lg border border-gray-600">
              <h3 className="text-xl font-semibold mb-2 text-gray-300">
                🧾 Output:
              </h3>
              <pre className="bg-gray-800 border border-gray-700 text-green-300 p-4 rounded-lg whitespace-pre-wrap min-h-[120px]">
                {output}
              </pre>
            </div>
          )}
          {!output && (
            <div className="mt-6 p-4 rounded-lg border border-gray-600">
              <h3 className="text-xl font-semibold mb-2 text-gray-300">
                🧾 Output:
              </h3>
              <pre className="bg-gray-800 border border-gray-700 text-gray-500 p-4 rounded-lg whitespace-pre-wrap min-h-[120px]">
                Output will appear here.
              </pre>
            </div>
          )}
        </>
      )}

      {/* Test case mode: run all test cases */}
      {isTestCaseMode && (
        <>
          <div>{/* Editor stays above */}</div>

          {/* Custom input checkbox and input field */}
          <div className="mt-4">
            <label className="flex items-center space-x-2 mb-3">
              <input
                type="checkbox"
                checked={useCustomInput}
                onChange={(e) => setUseCustomInput(e.target.checked)}
                className="w-4 h-4 text-blue-600 bg-gray-700 border-gray-600 rounded focus:ring-blue-500 focus:ring-2"
              />
              <span className="text-sm text-gray-300">
                Use custom input for testing
              </span>
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

          
          {/* Show message if no test cases */}
          {!useCustomInput && (!testCases || testCases.length === 0) && !isLoading && (
            <div className="mt-4 p-3 bg-gray-800 border border-gray-600 rounded-lg text-gray-300 text-sm">
              No test cases available for this problem. Use "Use custom input" checkbox to test your code.
            </div>
          )}
          
          <div className="flex gap-2 mt-4">
            <button
              onClick={() => {
                console.log("=== Run button clicked ===");
                console.log("useCustomInput:", useCustomInput);
                console.log("testCases:", testCases);
                console.log("testCases.length:", testCases?.length);
                console.log("current results.length:", results.length);
                
                if (useCustomInput) {
                  console.log("Using handleSingleRun (custom input)");
                  handleSingleRun();
                } else if (testCases && testCases.length > 0) {
                  console.log("Using handleRunAll");
                  handleRunAll();
                } else {
                  console.warn("No test cases available, using handleSingleRun");
                  setOutput("No test cases available. Use custom input or check problem configuration.");
                  handleSingleRun();
                }
              }}
              className={`flex-1 py-2 px-4 text-base font-semibold rounded bg-green-500 hover:bg-green-600 disabled:bg-green-800 disabled:cursor-not-allowed`}
              disabled={isLoading}
            >
              {isLoading ? "⏳ Running..." : `▶️ Run${testCases && testCases.length > 0 ? ` (${testCases.length} tests)` : ''}`}
            </button>
            <button
              onClick={() =>
                handleSubmitSolution(testCases || [], hiddenTestCases || [])
              }
              className={`flex-1 py-2 px-4 text-base font-semibold rounded bg-blue-500 hover:bg-blue-600 disabled:bg-blue-800 disabled:cursor-not-allowed`}
              disabled={isLoading}
            >
              {isLoading ? "Submitting..." : "Submit"}
            </button>
          </div>
          {/* Output - Show when there's output OR when running with custom input */}
          {(output || (useCustomInput && !isLoading && !results.length && !submitResult)) && (
            <div className="mt-6 p-4 rounded-lg border border-gray-600 bg-gray-800">
              <div className="flex justify-between items-center mb-2">
                <h3 className="text-xl font-semibold text-gray-300">
                  🧾 Output:
                </h3>
                {output && (
                  <button
                    onClick={() => copyToClipboard(output)}
                    className="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded text-sm flex items-center space-x-1"
                    title="Copy to clipboard"
                  >
                    {copySuccess ? '✓ Copied!' : '📋 Copy'}
                  </button>
                )}
              </div>
              <pre className="bg-gray-900 border border-gray-700 text-green-300 p-4 rounded-lg whitespace-pre-wrap min-h-[120px] max-h-96 overflow-auto">
                {output || "Output will appear here after running your code."}
              </pre>
              {(executionTime || executionMemory) && (
                <div className="mt-2 flex gap-4 text-sm text-gray-400">
                  {executionTime && <span>⏱️ Time: {formatExecutionTime(executionTime)}</span>}
                  {executionMemory && <span>Memory: {formatMemory(executionMemory)}</span>}
                </div>
              )}
            </div>
          )}
          
          {/* Error Display */}
          {errorDetails && (
            <div className="mt-6 p-4 rounded-lg border-2 border-red-500 bg-red-900/20">
              <div className="flex justify-between items-start mb-2">
                <h3 className="text-xl font-semibold text-red-400">
                  ⚠️ {errorDetails.type}
                </h3>
                <div className="flex gap-2">
                  {(executionTime || executionMemory) && (
                    <div className="text-xs text-red-300 flex gap-2">
                      {executionTime && <span>⏱️ {formatExecutionTime(executionTime)}</span>}
                      {executionMemory && <span>{formatMemory(executionMemory)}</span>}
                    </div>
                  )}
                  <button
                    onClick={() => copyToClipboard(errorDetails.message)}
                    className="px-3 py-1 bg-red-800 hover:bg-red-700 rounded text-sm"
                    title="Copy error to clipboard"
                  >
                    {copySuccess ? '✓' : '📋'}
                  </button>
                </div>
              </div>
              <div className="bg-gray-900 border border-red-700 p-3 rounded mt-2">
                <pre className="text-red-300 whitespace-pre-wrap text-sm">
                  {errorDetails.message}
                </pre>
                {errorDetails.details && (
                  <p className="text-red-400 text-xs mt-2">{errorDetails.details}</p>
                )}
              </div>
            </div>
          )}
          {/* Run results - Show test case results */}
          {results.length > 0 && !submitResult && (
            <div className="mt-6 p-4 rounded-lg border-2 border-gray-600 bg-gray-800">
              <div className="flex justify-between items-center mb-4">
                <h3 className="text-xl font-semibold text-gray-300">
                  Public Test Case Results ({results.length} test cases):
                </h3>
                {/* Overall execution metrics */}
                {(executionTime || executionMemory) && (
                  <div className="text-sm text-gray-300 flex gap-4">
                    {executionTime && <span>Avg Time: {formatExecutionTime(executionTime)}</span>}
                    {executionMemory && <span>Max Memory: {formatMemory(executionMemory)}</span>}
                  </div>
                )}
              </div>
              <ul className="space-y-3">
                {results.map((r, idx) => (
                  <li
                    key={idx}
                    className={`p-4 rounded-lg border-2 text-sm whitespace-pre-wrap ${
                      r.passed
                        ? "bg-green-900/50 border-green-500 text-green-200"
                        : "bg-red-900/50 border-red-500 text-red-200"
                    }`}
                  >
                    <div className="flex justify-between items-center mb-2">
                      <div className="font-semibold text-lg">
                        Test Case {idx + 1}: {r.passed ? "✅ Passed" : "❌ Failed"}
                      </div>
                      {(r.runtime || r.memory) && (
                        <div className="text-xs text-gray-300 flex gap-2">
                          {r.runtime && <span>⏱️ {formatExecutionTime(r.runtime)}</span>}
                          {r.memory && <span>{formatMemory(r.memory)}</span>}
                        </div>
                      )}
                    </div>
                    {r.error && (
                      <div className="mb-2 p-2 bg-red-900/30 border border-red-700 rounded">
                        <div className="text-red-400 font-semibold text-sm mb-1">{r.error.type}</div>
                        <pre className="text-red-300 text-xs whitespace-pre-wrap">{r.error.message}</pre>
                      </div>
                    )}
                    <div className="mb-2">
                      <span className="font-bold text-gray-300">Input:</span>
                      <div className="mt-1 flex items-center gap-2">
                        <pre className="flex-1 bg-gray-900 p-2 rounded whitespace-pre-wrap text-xs">
                          {String(r.input || 'N/A')}
                        </pre>
                        <button
                          onClick={() => copyToClipboard(String(r.input || ''))}
                          className="px-2 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs"
                          title="Copy input"
                        >
                          📋
                        </button>
                      </div>
                    </div>
                    <div className="mb-2">
                      <span className="font-bold text-gray-300">Expected Output:</span>
                      <div className="mt-1 flex items-center gap-2">
                        <pre className="flex-1 bg-gray-900 p-2 rounded whitespace-pre-wrap text-xs text-green-300">
                          {String(r.expected || 'N/A')}
                        </pre>
                        <button
                          onClick={() => copyToClipboard(String(r.expected || ''))}
                          className="px-2 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs"
                          title="Copy expected output"
                        >
                          📋
                        </button>
                      </div>
                    </div>
                    <div>
                      <span className="font-bold text-gray-300">Your Output:</span>
                      <div className="mt-1 flex items-center gap-2">
                        <pre className="flex-1 bg-gray-900 p-2 rounded whitespace-pre-wrap text-xs">
                          {String(r.actual || 'No Output')}
                        </pre>
                        <button
                          onClick={() => copyToClipboard(String(r.actual || ''))}
                          className="px-2 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs"
                          title="Copy your output"
                        >
                          📋
                        </button>
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {/* Submit result panel - Show this prominently */}
          {submitResult && (
            <div className="mt-6 p-4 rounded-lg border-2 border-blue-500 bg-gray-800">
              {submitResult.status === "accepted" ? (
                <div className="flex flex-col items-center justify-center p-6">
                  {showAccepted && (
                    <div className="animate-bounce text-4xl mb-2">🎉</div>
                  )}
                  <div className="text-green-400 text-2xl font-bold mb-2">
                    ✅ Solution Accepted!
                  </div>
                  <div className="text-green-200 text-lg mb-2">
                    All {submitResult.totalCount} test cases passed.
                  </div>
                  {(executionTime || executionMemory) && (
                    <div className="text-green-300 text-sm flex gap-4 justify-center">
                      {executionTime && <span>⏱️ Time: {formatExecutionTime(executionTime)}</span>}
                      {executionMemory && <span>Memory: {formatMemory(executionMemory)}</span>}
                    </div>
                  )}
                </div>
              ) : (
                <div className="bg-red-900/50 border-2 border-red-600 text-red-200 p-6 rounded-lg">
                  <div className="font-bold text-xl mb-4 text-red-400">
                    ❌ Test Case Failed (
                    {submitResult.failedCase?.type === "public"
                      ? "Public"
                      : "Hidden"}{" "}
                    #{submitResult.failedCase?.index !== undefined ? submitResult.failedCase.index + 1 : 'N/A'})
                    {submitResult.failureReason && (
                      <span className="ml-2 text-sm font-normal text-red-300">
                        ({submitResult.failureReason.replace(/_/g, ' ')})
                      </span>
                    )}
                  </div>
                  {submitResult.failedCase?.error && (
                    <div className="mb-4 p-3 bg-red-950 border border-red-700 rounded">
                      <div className="font-semibold text-red-300 mb-1">{submitResult.failedCase.error.type}</div>
                      <pre className="text-red-200 text-xs whitespace-pre-wrap">
                        {submitResult.failedCase.error.message}
                      </pre>
                    </div>
                  )}
                  {submitResult.failedCase && (
                    <>
                      <div className="mb-3">
                        <span className="font-bold text-red-300">Input:</span>
                        <pre className="mt-1 bg-gray-900 p-2 rounded whitespace-pre-wrap text-sm">
                          {submitResult.failedCase.input || 'N/A'}
                        </pre>
                      </div>
                      <div className="mb-3">
                        <span className="font-bold text-red-300">Expected Output:</span>
                        <pre className="mt-1 bg-gray-900 p-2 rounded whitespace-pre-wrap text-sm text-green-300">
                          {submitResult.failedCase.output || 'N/A'}
                        </pre>
                      </div>
                      <div className="mb-3">
                        <span className="font-bold text-red-300">Your Output:</span>
                        <pre className="mt-1 bg-gray-900 p-2 rounded whitespace-pre-wrap text-sm text-red-300">
                          {submitResult.failedCase.actual || 'N/A'}
                        </pre>
                      </div>
                    </>
                  )}
                  <div className="mt-4 pt-4 border-t border-red-700">
                    Passed {submitResult.passedCount || 0} out of{" "}
                    {submitResult.totalCount || 0} test cases.
                  </div>
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
