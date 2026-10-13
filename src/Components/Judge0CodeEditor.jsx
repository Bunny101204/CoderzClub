import React, { useState, useEffect, useRef } from "react";
import "../index.css";
import UtilBar from "./UtilBar";
import { EditorContextAPI } from "./EditorContextAPI";
import { getTemplate } from "./LanguageTemplates";
import {
  getJobStatusMessage,
  getPollDelay,
  isTerminalJobState,
  MAX_POLLS,
  normalizeJobResult,
  isHiddenResult,
} from "./submissionPolling";
import { editorSurfaceClasses, resultCardClasses } from "../theme/theme";
import { useTheme } from "../context/ThemeContext";
import { CopyIcon } from "../icons/AppIcons.jsx";
import { codingDurationSecondsFromElapsedMs } from "../execution/codingDuration.js";
import { formatSupportReference, studentVerdictCopy } from "../execution/studentVerdict.js";
import { api, apiUrl } from "../apiClient";
import { CONTEST_RUN_MAX_POLLS, getContestRunPollDelay, isTerminalContestRun } from "../contests/contestRunPolling";
import { useAuth } from "../context/AuthContext";
import { readInitialDraftCode } from "../drafts/codeDraft";
import { useEditorDraft } from "../drafts/useEditorDraft";

const Judge0CodeEditor = ({
  initialCode = "",
  testCases = [],
  languageId: propLanguageId,
  setLanguageId: propSetLanguageId,
  setEditorCode: propSetEditorCode,
  functionName: propFunctionName,
  parameters: propParameters = [],
  problemId,
  executionMode = "STDIN_STDOUT",
  executionLocked = false,
  contestId = null,
  contestStatus = null,
  onSubmissionSuccess,
}) => {
  const { theme } = useTheme();
  const { user } = useAuth();
  const editorChrome = editorSurfaceClasses(theme);
  // Use controlled or internal state for languageId
  const [internalLanguageId, setInternalLanguageId] = useState(62);
  const languageId =
    propLanguageId !== undefined ? propLanguageId : internalLanguageId;
  const setLanguageId = propSetLanguageId || setInternalLanguageId;

  // Use controlled or internal state for code. Local draft seeding is only for the uncontrolled editor.
  const [internalSourceCode, setInternalSourceCode] = useState(() => {
    if (propSetEditorCode) return initialCode;
    const local = readInitialDraftCode({
      userId: user?.id,
      contestId,
      problemId,
      languageId: propLanguageId !== undefined ? propLanguageId : 62,
    });
    return local != null ? local : initialCode;
  });
  const sourceCode = propSetEditorCode ? initialCode : internalSourceCode;
  const setSourceCode = propSetEditorCode || setInternalSourceCode;

  const [output, setOutput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [results, setResults] = useState([]); // [{input, expected, actual, passed}]
  const [, setSubmissionStatus] = useState(null);
  const [userInput, setUserInput] = useState("");
  const [submitResult, setSubmitResult] = useState(null); // {status, failedCase, passedCount, totalCount}
  const [showAccepted, setShowAccepted] = useState(false);
  const [useCustomInput, setUseCustomInput] = useState(false);
  const [executionTime, setExecutionTime] = useState(null); // Execution time in ms
  const [executionMemory, setExecutionMemory] = useState(null); // Execution memory in bytes
  const [errorDetails, setErrorDetails] = useState(null); // Detailed error info
  const [copySuccess, setCopySuccess] = useState(false); // Copy to clipboard success
  const pollingControllerRef = useRef(null);
  const activeJobIdRef = useRef(null);
  const submissionGenerationRef = useRef(0);
  const contestRunGenerationRef = useRef(0);
  const contestRunControllerRef = useRef(null);
  const activeRunIdRef = useRef(null);
  const contestMode = Boolean(contestId);
  const contestLive = contestMode && contestStatus === "LIVE";
  const runLocked = contestMode ? !contestLive : executionLocked;
  const customRunLocked = contestMode ? true : executionLocked;
  const eventSourceRef = useRef(null);
  const sourceCodeRef = useRef(sourceCode);
  sourceCodeRef.current = sourceCode;
  const draftEnabled = !propSetEditorCode && Boolean(problemId);
  const draft = useEditorDraft({
    enabled: draftEnabled,
    userId: user?.id || null,
    contestId,
    problemId,
    languageId,
    starterCode: getTemplate(languageId),
    currentCodeRef: sourceCodeRef,
    onApply: (code) => {
      if (!propSetEditorCode) setInternalSourceCode(code ?? "");
    },
  });

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

  // New features
  const [startTime, setStartTime] = useState(null);
  const [elapsedTime, setElapsedTime] = useState(0);
  const [isTimerRunning, setIsTimerRunning] = useState(false);

  useEffect(() => () => {
    pollingControllerRef.current?.abort();
    eventSourceRef.current?.close();
    contestRunControllerRef.current?.abort();
  }, []);

  useEffect(() => () => {
    contestRunGenerationRef.current += 1;
    contestRunControllerRef.current?.abort();
    contestRunControllerRef.current = null;
    activeRunIdRef.current = null;
  }, [problemId, contestId]);

  //for line numbers in editor
  const [lineCount, setLineCount] = useState(1);
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

  const noteDraftEdit = draft.noteEdit;
  useEffect(() => {
    if (!draftEnabled) return;
    noteDraftEdit(sourceCode ?? "");
  }, [sourceCode, draftEnabled, noteDraftEdit]);

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

    if (statusId === 13 || res.errorType === "INTERNAL_ERROR" || res.errorCode === "JUDGE0_PROVIDER_ERROR") {
      return {
        type: "INTERNAL_ERROR",
        message: "Execution service is temporarily unavailable. Please retry.",
        details: "Judging infrastructure failed. This is not a wrong answer.",
        code: "JUDGE0_PROVIDER_ERROR"
      };
    }

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
        return await api.judge0.execute(payload);
      } catch (error) {
        lastErr = error;
        const status = error?.response?.status;
        if (status === 429 || status === 500 || status === 502 || status === 503 || status === 504) {
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

  const cancelContestRunPolling = () => {
    contestRunGenerationRef.current += 1;
    contestRunControllerRef.current?.abort();
    contestRunControllerRef.current = null;
    activeRunIdRef.current = null;
  };

  const applyContestRunPayload = (payload) => {
    const remoteResults = payload.results || [];
    const allResults = [];
    for (let i = 0; i < remoteResults.length; i++) {
      const r = remoteResults[i];
      const errorType = r.errorType;
      const error = errorType && errorType !== "WRONG_ANSWER"
        ? { type: errorType, message: r.errorMessage || errorType }
        : null;
      allResults.push({
        input: r.input || "",
        expected: r.expectedOutput || "",
        actual: r.actualOutput || "No Output",
        passed: !!r.passed,
        runtime: r.runtime,
        memory: r.memory,
        error,
      });
      if (r.runtime && (!executionTime || r.runtime > executionTime)) {
        setExecutionTime(r.runtime);
      }
      if (r.memory && (!executionMemory || r.memory > executionMemory)) {
        setExecutionMemory(r.memory);
      }
      if (error) {
        setErrorDetails(error);
      }
    }
    setResults(allResults);
    setOutput("");
  };

  const pollContestRun = async (runId, generation, signal) => {
    for (let poll = 0; poll < CONTEST_RUN_MAX_POLLS; poll++) {
      if (signal.aborted || generation !== contestRunGenerationRef.current || activeRunIdRef.current !== runId) {
        return;
      }
      await sleep(getContestRunPollDelay(poll));
      if (signal.aborted || generation !== contestRunGenerationRef.current || activeRunIdRef.current !== runId) {
        return;
      }
      const response = await api.contests.getRun(runId);
      const payload = response.data || {};
      if (payload.status === "QUEUED" || payload.status === "RUNNING") {
        const completed = payload.completedTests;
        const total = payload.totalTests;
        setOutput(
          Number.isFinite(completed) && Number.isFinite(total) && total > 0
            ? `Running public tests ${completed}/${total}`
            : payload.status === "QUEUED" ? "Contest run queued" : "Running public tests..."
        );
        continue;
      }
      if (isTerminalContestRun(payload.status)) {
        if (payload.status === "FAILED") {
          setOutput(payload.errorMessage || "Contest run failed. You can retry.");
          setResults([]);
          return;
        }
        applyContestRunPayload(payload);
        return;
      }
    }
    setOutput("Contest run is taking longer than expected. You can retry.");
  };

  const handleContestRun = async () => {
    if (runLocked) {
      return;
    }
    if (!testCases || testCases.length === 0) {
      setOutput("No public tests available for this contest problem.");
      return;
    }
    cancelContestRunPolling();
    const generation = contestRunGenerationRef.current;
    const controller = new AbortController();
    contestRunControllerRef.current = controller;
    setIsLoading(true);
    setResults([]);
    setOutput("");
    setSubmitResult(null);
    setExecutionTime(null);
    setExecutionMemory(null);
    setErrorDetails(null);
    try {
      const response = await api.contests.run(contestId, problemId, {
        code: sourceCode,
        language: languageNames[languageId] || "Unknown",
        languageId,
        codingDurationSeconds: codingDurationSecondsFromElapsedMs(elapsedTime),
      });
      if (generation !== contestRunGenerationRef.current) {
        return;
      }
      const runId = response.data?.runId;
      if (!runId) {
        setOutput("Contest run was not accepted. Please retry.");
        return;
      }
      activeRunIdRef.current = runId;
      setOutput("Contest run queued");
      await pollContestRun(runId, generation, controller.signal);
    } catch (error) {
      if (generation !== contestRunGenerationRef.current) {
        return;
      }
      const status = error?.response?.status;
      if (status === 429) {
        setOutput(error?.response?.data?.error || "Run is busy. Please retry.");
      } else if (status === 503) {
        setOutput(error?.response?.data?.error || "Run service is unavailable. Please retry.");
      } else if (status === 409) {
        setOutput("Contest Run is available only while the contest is LIVE.");
      } else {
        setOutput("Error running test cases: " + (error.response?.data?.error || error.message || "Unknown error"));
      }
      setResults([]);
    } finally {
      if (generation === contestRunGenerationRef.current) {
        setIsLoading(false);
      }
    }
  };

  const handleRunAll = async () => {
    if (runLocked) {
      return;
    }
    if (contestMode) {
      await handleContestRun();
      return;
    }
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
      const response = await api.problems.runPublic(problemId, {
        source_code: sourceCode,
        language_id: languageId,
      });
      const payload = response.data || {};
      const remoteResults = payload.results || [];
      for (let i = 0; i < remoteResults.length; i++) {
        const r = remoteResults[i];
        const errorType = r.errorType;
        const error = errorType && errorType !== "WRONG_ANSWER"
          ? { type: errorType, message: r.errorMessage || errorType }
          : null;
        allResults.push({
          input: r.input || "",
          expected: r.expectedOutput || "",
          actual: r.actualOutput || "No Output",
          passed: !!r.passed,
          runtime: r.runtime,
          memory: r.memory,
          error,
        });
        if (r.runtime && (!executionTime || r.runtime > executionTime)) {
          setExecutionTime(r.runtime);
        }
        if (r.memory && (!executionMemory || r.memory > executionMemory)) {
          setExecutionMemory(r.memory);
        }
        if (error) {
          setErrorDetails(error);
        }
      }
      
      setResults(allResults);
      setOutput(""); // Clear output since we're showing results instead
      
    } catch (error) {
      console.error("Error in handleRunAll:", error);
      if (error?.response?.status === 429) {
        setOutput(
          "Judge0 rate limit reached. Please wait a few seconds and try again."
        );
      } else if (error?.response?.status === 503) {
        setOutput(
          "Judge0 is temporarily unavailable. Please try again in a few seconds."
        );
      } else {
        setOutput(
          "Error running test cases: " + (error.message || "Unknown error")
        );
      }
      setResults([]); // Clear results on error
    } finally {
      setIsLoading(false);
    }
  };

  // Use test case mode only when we have a specific problem (problemId exists)
  const isTestCaseMode = !!problemId;

  // Old single-run handler for normal editor
  const handleSingleRun = async () => {
    if (customRunLocked) {
      return;
    }
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
  const contestSubmitEnabled = Boolean(contestId) && contestStatus === "LIVE";
  const submitLocked = contestId ? !contestSubmitEnabled : executionLocked;

  const handleSubmitSolution = async () => {
    if (submitLocked) {
      return;
    }
    cancelContestRunPolling();
    const submissionGeneration = ++submissionGenerationRef.current;
    pollingControllerRef.current?.abort();
    eventSourceRef.current?.close();
    eventSourceRef.current = null;
    pollingControllerRef.current = null;
    activeJobIdRef.current = null;
    // Check submission limits before submitting
    try {
      const limitsResponse = await api.submissions.getLimits(problemId);
      const limits = limitsResponse.data;

      if (!limits.canSubmitNow) {
        const cooldown = limits.cooldownSeconds || 0;
        setOutput(`Please wait ${cooldown} seconds before submitting again.`);
        setErrorDetails({
          type: 'Rate Limit',
          message: `You must wait ${cooldown} seconds between submissions.`,
          details: 'This helps prevent abuse and ensures fair usage.'
        });
        return;
      }

      if (limits.hasExceededDailyLimit) {
        setOutput(`Daily submission limit exceeded. You have ${limits.remainingDaily} submissions remaining today.`);
        setErrorDetails({
          type: 'Daily Limit Exceeded',
          message: `You have exceeded your daily submission limit of ${limits.dailyLimit}.`,
          details: `Remaining submissions: ${limits.remainingDaily}`
        });
        return;
      }

      if (limits.hasExceededProblemLimit) {
        setOutput(`Problem submission limit exceeded. You have ${limits.remainingProblem} submissions remaining for this problem today.`);
        setErrorDetails({
          type: 'Problem Limit Exceeded',
          message: `You have exceeded your submission limit for this problem (${limits.problemLimit} per day).`,
          details: `Remaining submissions: ${limits.remainingProblem}`
        });
        return;
      }
    } catch (error) {
      console.warn("Could not check submission limits:", error);
      // Continue with submission if limit check fails (don't block user)
    }

    if (submissionGeneration !== submissionGenerationRef.current) return;

    setIsLoading(true);
    setSubmitResult(null);
    setShowAccepted(false);
    setResults([]); // Clear previous run results when submitting
    setOutput(""); // Clear output when submitting
    setExecutionTime(null);
    setExecutionMemory(null);
    setErrorDetails(null);

    try {
      // Create async submission job - only send identifiers and code

      // Create async submission job

      const executionPayload = {
        code: sourceCode,
        language: languageNames[languageId] || "Unknown",
        languageId: languageId,
        codingDurationSeconds: codingDurationSecondsFromElapsedMs(elapsedTime)
      };

      const jobResponse = contestId
        ? await api.contests.submit(contestId, problemId, executionPayload)
        : await api.submissionJobs.create({ problemId, ...executionPayload });
      if (submissionGeneration !== submissionGenerationRef.current) return;
      const { jobId } = jobResponse.data;
      activeJobIdRef.current = jobId;

      await subscribeToJobEvents(jobId);

    } catch (error) {
      console.error("Submission failed:", error);
      setOutput("Error during submission: " + (error.response?.data?.error || error.message || "Unknown error"));
      setIsLoading(false);
    }
  };

  const startPollingFallback = (jobId) => {
    if (activeJobIdRef.current !== jobId) return;
    eventSourceRef.current?.close();
    eventSourceRef.current = null;
    pollingControllerRef.current?.abort();
    pollingControllerRef.current = new AbortController();
    pollJobStatus(jobId, pollingControllerRef.current.signal);
  };

  const subscribeToJobEvents = async (jobId) => {
    let ticket;
    try {
      const ticketResponse = await api.submissionJobs.createEventTicket(jobId);
      ticket = ticketResponse.data.ticket;
    } catch (error) {
      console.warn("Unable to create SSE ticket; switching to polling", error);
      startPollingFallback(jobId);
      return;
    }

    if (activeJobIdRef.current !== jobId) return;
    const source = new EventSource(apiUrl(`/api/submission-jobs/${jobId}/events?ticket=${encodeURIComponent(ticket)}`));
    let fallbackStarted = false;
    const fallbackTimer = setTimeout(() => {
      if (activeJobIdRef.current !== jobId || fallbackStarted) return;
      fallbackStarted = true;
      startPollingFallback(jobId);
    }, 5000);
    eventSourceRef.current = source;

    const handleEvent = (message) => {
      if (activeJobIdRef.current !== jobId) return;
      try {
        const event = JSON.parse(message.data);
        const statusMessage = getJobStatusMessage(event);
        if (statusMessage) setOutput(statusMessage);
        if (isTerminalJobState(event.status)) {
          source.close();
          clearTimeout(fallbackTimer);
          eventSourceRef.current = null;
          // The event is progress-only; retrieve the authoritative sanitized final snapshot.
          if (!fallbackStarted) {
            fallbackStarted = true;
            startPollingFallback(jobId);
          }
        }
      } catch (error) {
        console.warn("Invalid submission event; switching to polling", error);
        if (!fallbackStarted) {
          fallbackStarted = true;
          clearTimeout(fallbackTimer);
          startPollingFallback(jobId);
        }
      }
    };

    source.addEventListener("submission-job", handleEvent);

    source.onerror = () => {
      if (activeJobIdRef.current !== jobId || fallbackStarted) return;
      fallbackStarted = true;
      clearTimeout(fallbackTimer);
      startPollingFallback(jobId);
    };
  };

  // Poll for job completion status
  const pollJobStatus = async (jobId, signal) => {
    for (let poll = 0; poll < MAX_POLLS; poll++) {
      try {
        await sleep(getPollDelay(poll));
        if (signal.aborted || activeJobIdRef.current !== jobId) return;
        const response = await api.submissionJobs.getStatus(jobId, { signal });
        const job = response.data;

        const statusMessage = getJobStatusMessage(job);
        if (statusMessage) setOutput(statusMessage);

        if (isTerminalJobState(job.status)) {
          setOutput("");
          setIsLoading(false);

          if (job.status === 'TIMEOUT') {
            setSubmissionStatus("TIMEOUT");
            setOutput("Submission timed out on the execution server.");
            setErrorDetails({ type: "Timeout", message: "The execution exceeded the server time limit." });
            setSubmitResult({ status: "timeout" });
            return;
          }
          if (job.status === 'CANCELLED') {
            setSubmissionStatus("CANCELLED");
            setOutput("Submission cancelled.");
            setSubmitResult({ status: "cancelled" });
            return;
          }
          if (job.status === 'FAILED') {
            setSubmissionStatus("FAILED");
            setOutput("Execution service is temporarily unavailable. Please retry.");
            setErrorDetails({
              type: "INTERNAL_ERROR",
              message: "Execution service is temporarily unavailable. Please retry.",
              details: "Judging infrastructure failed. This is not a wrong answer.",
              code: "JUDGE0_PROVIDER_ERROR"
            });
            setSubmitResult({
              status: "failed",
              failedCase: null,
              failureReason: "INTERNAL_ERROR",
              errorCode: job.errorCode || "JUDGE0_PROVIDER_ERROR",
              diagnosticMessage: job.diagnosticMessage,
              reference: job.reference || job.jobId
            });
            return;
          }

          if (job.status === 'COMPLETED' && job.result === 'ACCEPTED') {
            const normalizedResults = (job.testResults || []).map(normalizeJobResult);
            setResults(normalizedResults);
            setSubmitResult({ status: "accepted", passedCount: job.testResults?.length || 0, totalCount: job.testResults?.length || 0 });
            setShowAccepted(true);
            setTimeout(() => setShowAccepted(false), 3000);
            setIsTimerRunning(false);

            if (onSubmissionSuccess) {
              onSubmissionSuccess();
            }
            window.dispatchEvent(new Event("leaderboardRefresh"));
          } else {
            const normalizedResults = (job.testResults || []).map(normalizeJobResult);
            setResults(normalizedResults);
            const failedIndex = normalizedResults.findIndex(r => !r.passed);
            const failedTest = failedIndex >= 0 ? normalizedResults[failedIndex] : null;
            if (failedTest) {
              const error = failedTest.error;
              if (error && failedTest.type !== 'hidden') {
                error.details = `Expected: ${failedTest.expected}, Got: ${failedTest.actual}`;
              }

            setSubmitResult({
              status: "failed",
              failedCase: {
                input: failedTest.input,
                output: failedTest.expected,
                expected: failedTest.expected,
                actual: failedTest.actual,
                type: failedTest.type,
                index: failedIndex,
                error: error
              },
              passedCount: normalizedResults.filter(r => r.passed).length,
              totalCount: normalizedResults.length,
              failureReason: job.result,
              errorCode: job.errorCode,
              diagnosticMessage: job.diagnosticMessage,
              reference: job.reference || job.jobId || job.submissionId
            });

              if (error) {
                setErrorDetails(error);
              }
            } else {
              setSubmitResult({
                status: "failed",
                failedCase: null,
                passedCount: normalizedResults.filter(r => r.passed).length,
                totalCount: normalizedResults.length,
                failureReason: job.result
              });
            }
          }

          // Update execution metrics
          if (job.runtime) setExecutionTime(job.runtime);
          if (job.memory) setExecutionMemory(job.memory);

          return;
        }

      } catch (error) {
        if (signal.aborted || error.code === 'ERR_CANCELED' || error.name === 'CanceledError') return;
        console.error("Error polling job status:", error);
        setIsLoading(false);
        setOutput("Error checking submission status: " + (error.response?.data?.error || error.message));
        return;
      }
    }

    if (activeJobIdRef.current !== jobId) return;
    setIsLoading(false);
    setSubmissionStatus("STILL_PROCESSING");
    setSubmitResult({ status: "still-processing" });
    setOutput("Submission is still processing. Refresh to reconnect to this job.");
    setErrorDetails({
      type: 'Still Processing',
      message: 'The job is still queued or running on the server.',
      details: 'Use Refresh status to reconnect without creating another submission.'
    });
  };

  const reconnectToJob = () => {
    const jobId = activeJobIdRef.current;
    if (!jobId) return;
    pollingControllerRef.current?.abort();
    pollingControllerRef.current = new AbortController();
    setSubmissionStatus(null);
    setIsLoading(true);
    pollJobStatus(jobId, pollingControllerRef.current.signal);
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
          className="app-input px-3 py-1 rounded text-sm focus:outline-none"
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
          <span className="text-sm app-muted">Timer:</span>
          <span
            className={`text-sm font-mono px-2 py-1 rounded border ${
              isTimerRunning
                ? "bg-green-100 text-green-900 border-green-600 dark:bg-green-700 dark:text-white dark:border-green-500"
                : "bg-gray-100 text-gray-800 border-gray-300 dark:bg-gray-700 dark:text-gray-200 dark:border-gray-500"
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
      </div>
    </div>
  );

  return (
    <div className={`max-w-4xl mx-auto mt-8 p-6 rounded-lg shadow-lg ${theme === "light" ? "bg-white text-gray-900" : "bg-gray-900 text-white"}`}>
      {/* REMOVED: Main method warning modal - no longer needed as users can submit full programs */}
      {draftEnabled && draft.status ? (
        <p className="text-xs app-muted mb-2" data-testid="draft-status">{draft.status}</p>
      ) : null}
      {draftEnabled && draft.conflict ? (
        <div className="mb-2 text-sm" data-testid="draft-conflict">
          <p>This draft changed on another device.</p>
          <div className="mt-1 flex gap-2">
            <button type="button" className="underline text-xs" onClick={draft.useCloud}>Use cloud</button>
            <button type="button" className="underline text-xs" onClick={draft.keepLocal}>Keep this draft</button>
          </div>
        </div>
      ) : null}
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
      <div className={`relative flex mb-4 border rounded-lg overflow-hidden ${theme === "light" ? "border-gray-300" : "border-gray-700"}`}>
        {/* Line Numbers */}
        <div
          ref={lineRef}
          className={`${theme === "light" ? "bg-gray-100 text-gray-500" : "bg-gray-800 text-gray-400"} text-sm font-mono px-3 py-4 select-none overflow-hidden`}
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
          className={`w-full p-4 mb-4 rounded-lg font-mono text-sm resize-y focus:outline-none focus:ring-2 focus:ring-green-400 ${editorChrome}`}
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
            className="w-full p-3 mb-4 rounded-lg app-input text-sm focus:outline-none focus:ring-2 focus:ring-green-400"
            placeholder="Enter input (stdin)"
            value={userInput}
            onChange={(e) => setUserInput(e.target.value)}
          />
          <button
            onClick={handleSingleRun}
            className={`w-full py-3 text-lg font-semibold rounded-lg ${
              isLoading || customRunLocked
                ? "bg-green-800 cursor-not-allowed"
                : "bg-green-500 hover:bg-green-600"
            }`}
            disabled={isLoading || customRunLocked}
          >
            {isLoading ? "Running..." : "Run Code"}
          </button>
          {output && (
            <div className="mt-6 p-4 rounded-lg border border-gray-300 dark:border-gray-600 app-surface">
              <h3 className="text-xl font-semibold mb-2">
                Output:
              </h3>
              <pre className="app-code p-4 rounded-lg whitespace-pre-wrap min-h-[120px]">
                {output}
              </pre>
            </div>
          )}
          {!output && (
            <div className="mt-6 p-4 rounded-lg border border-gray-300 dark:border-gray-600 app-surface">
              <h3 className="text-xl font-semibold mb-2">
                Output:
              </h3>
              <pre className="app-code app-muted p-4 rounded-lg whitespace-pre-wrap min-h-[120px]">
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

          {!contestMode && (
          <div className="mt-4">
            <label className="flex items-center space-x-2 mb-3">
              <input
                type="checkbox"
                checked={useCustomInput}
                onChange={(e) => setUseCustomInput(e.target.checked)}
                className="w-4 h-4 text-blue-600 border-gray-400 rounded focus:ring-blue-500 focus:ring-2"
              />
              <span className="text-sm">
                Use custom input for testing
              </span>
            </label>

            {useCustomInput && (
              <input
                type="text"
                className="w-full p-3 mb-4 rounded-lg app-input text-sm focus:outline-none focus:ring-2 focus:ring-green-400"
                placeholder="Enter custom input (stdin)"
                value={userInput}
                onChange={(e) => setUserInput(e.target.value)}
              />
            )}
          </div>
          )}

          
          {/* Show message if no test cases */}
          {!useCustomInput && (!testCases || testCases.length === 0) && !isLoading && (
            <div className="mt-4 p-3 app-inset rounded-lg text-sm">
              No test cases available for this problem. Use "Use custom input" checkbox to test your code.
            </div>
          )}
          
          {contestMode && !contestLive && (
            <div className="mt-4 p-3 app-inset rounded-lg text-sm">
              Contest Run and Submit are available only while the contest is LIVE.
            </div>
          )}
          {contestMode && contestLive && (
            <div className="mt-4 p-3 app-inset rounded-lg text-sm">
              Contest Run uses public snapshot tests only. Custom input is disabled during contests.
            </div>
          )}
          {executionLocked && !contestMode && (
            <div className="mt-4 p-3 app-inset rounded-lg text-sm">
              Contest Run and Submit are not available yet. You can still edit code locally.
            </div>
          )}
          <div className="flex gap-2 mt-4">
            <button
              onClick={() => {
                if (runLocked) {
                  return;
                }
                if (!contestMode && useCustomInput) {
                  handleSingleRun();
                } else if (testCases && testCases.length > 0) {
                  handleRunAll();
                } else if (!contestMode) {
                  console.warn("No test cases available, using handleSingleRun");
                  setOutput("No test cases available. Use custom input or check problem configuration.");
                  handleSingleRun();
                } else {
                  setOutput("No public tests available for this contest problem.");
                }
              }}
              className={`flex-1 py-2 px-4 text-base font-semibold rounded bg-green-500 hover:bg-green-600 disabled:bg-green-800 disabled:cursor-not-allowed`}
              disabled={isLoading || runLocked}
              title={runLocked ? "Contest run is available only while LIVE" : "Run tests"}
            >
              {isLoading ? "Running..." : `Run${testCases && testCases.length > 0 ? ` (${testCases.length} tests)` : ''}`}
            </button>
            <button
              onClick={() => handleSubmitSolution(testCases || [])}
              className={`flex-1 py-2 px-4 text-base font-semibold rounded bg-blue-500 hover:bg-blue-600 disabled:bg-blue-800 disabled:cursor-not-allowed`}
              disabled={isLoading || submitLocked}
              title={submitLocked
                ? (contestId ? "Contest submit is available only while LIVE" : "Contest submit is not available yet")
                : "Submit"}
            >
              {isLoading ? "Submitting..." : "Submit"}
            </button>
          </div>
          {/* Output - Show when there's output OR when running with custom input */}
          {(output || (useCustomInput && !isLoading && !results.length && !submitResult)) && (
            <div className="mt-6 p-4 rounded-lg border border-gray-200 dark:border-gray-600 app-surface">
              <div className="flex justify-between items-center mb-2">
                <h3 className="text-xl font-semibold">
                  Output:
                </h3>
                {output && (
                  <button
                    onClick={() => copyToClipboard(output)}
                    className="px-3 py-1 app-btn-secondary rounded text-sm flex items-center space-x-1"
                    title="Copy to clipboard"
                  >
                    {copySuccess ? "Copied!" : <span className="inline-flex items-center gap-1"><CopyIcon /> Copy</span>}
                  </button>
                )}
              </div>
              <pre className="app-code text-green-700 dark:text-green-300 p-4 rounded-lg whitespace-pre-wrap min-h-[120px] max-h-96 overflow-auto">
                {output || "Output will appear here after running your code."}
              </pre>
              {(executionTime || executionMemory) && (
                <div className="mt-2 flex gap-4 text-sm app-muted">
                  {executionTime && <span>Time: {formatExecutionTime(executionTime)}</span>}
                  {executionMemory && <span>Memory: {formatMemory(executionMemory)}</span>}
                </div>
              )}
            </div>
          )}
          
          {/* Error Display */}
          {errorDetails && (
            <div className="mt-6 p-4 rounded-lg border-2 border-red-600 bg-red-50 text-red-900 dark:bg-red-950/40 dark:text-red-100">
              <div className="flex justify-between items-start mb-2">
                <h3 className="text-xl font-semibold">
                  {errorDetails.type}
                </h3>
                <div className="flex gap-2">
                  {(executionTime || executionMemory) && (
                    <div className="text-xs text-red-300 flex gap-2">
                      {executionTime && <span>{formatExecutionTime(executionTime)}</span>}
                      {executionMemory && <span>{formatMemory(executionMemory)}</span>}
                    </div>
                  )}
                  <button
                    onClick={() => copyToClipboard(errorDetails.message)}
                    className="px-3 py-1 app-btn-secondary rounded text-sm"
                    title="Copy error to clipboard"
                  >
                    {copySuccess ? "Copied" : <CopyIcon />}
                  </button>
                </div>
              </div>
              <div className="app-code border border-red-300 dark:border-red-700 p-3 rounded mt-2">
                <pre className="whitespace-pre-wrap text-sm">
                  {errorDetails.message}
                </pre>
                {errorDetails.details && (
                  <p className="text-xs mt-2">{errorDetails.details}</p>
                )}
              </div>
            </div>
          )}
          {/* Run results - Show test case results */}
          {results.length > 0 && !submitResult && (
            <div className="mt-6 p-4 rounded-lg border-2 border-gray-300 dark:border-gray-600 app-surface">
              <div className="flex justify-between items-center mb-4">
                <h3 className="text-xl font-semibold">
                  Public Test Case Results ({results.length} test cases):
                </h3>
                {/* Overall execution metrics */}
                {(executionTime || executionMemory) && (
                  <div className="text-sm app-muted flex gap-4">
                    {executionTime && <span>Avg Time: {formatExecutionTime(executionTime)}</span>}
                    {executionMemory && <span>Max Memory: {formatMemory(executionMemory)}</span>}
                  </div>
                )}
              </div>
              <ul className="space-y-3">
                {results.map((r, idx) => (
                  <li
                    key={idx}
                    className={`p-4 rounded-lg border-2 text-sm whitespace-pre-wrap ${resultCardClasses(r.passed)}`}
                  >
                    <div className="flex justify-between items-center mb-2">
                      <div className="font-semibold text-lg">
                        Test Case {idx + 1}: {r.passed ? "Passed" : "Failed"}
                      </div>
                      {(r.runtime || r.memory) && (
                        <div className="text-xs flex gap-2 opacity-90">
                          {r.runtime && <span>{formatExecutionTime(r.runtime)}</span>}
                          {r.memory && <span>{formatMemory(r.memory)}</span>}
                        </div>
                      )}
                    </div>
                    {r.error && (
                      <div className="mb-2 p-2 app-inset rounded">
                        <div className="font-semibold text-sm mb-1">{r.error.type}</div>
                        <pre className="text-xs whitespace-pre-wrap">{r.error.message}</pre>
                      </div>
                    )}
                    {isHiddenResult(r) ? (
                      <div className="mb-2 text-sm">Hidden testcase details are not shown.</div>
                    ) : <div className="mb-2">
                      <span className="font-bold">Input:</span>
                      <div className="mt-1 flex items-center gap-2">
                        <pre className="flex-1 app-code p-2 rounded whitespace-pre-wrap text-xs">
                          {String(r.input || 'N/A')}
                        </pre>
                        <button
                          onClick={() => copyToClipboard(String(r.input || ''))}
                          className="px-2 py-1 app-btn-secondary rounded text-xs"
                          title="Copy input"
                        >
                          <CopyIcon className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </div>}
                    {!isHiddenResult(r) && <div className="mb-2">
                      <span className="font-bold">Expected Output:</span>
                      <div className="mt-1 flex items-center gap-2">
                        <pre className="flex-1 app-code p-2 rounded whitespace-pre-wrap text-xs">
                          {String(r.expected || 'N/A')}
                        </pre>
                        <button
                          onClick={() => copyToClipboard(String(r.expected || ''))}
                          className="px-2 py-1 app-btn-secondary rounded text-xs"
                          title="Copy expected output"
                        >
                          <CopyIcon className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </div>}
                    {!isHiddenResult(r) && <div>
                      <span className="font-bold">Your Output:</span>
                      <div className="mt-1 flex items-center gap-2">
                        <pre className="flex-1 app-code p-2 rounded whitespace-pre-wrap text-xs">
                          {String(r.actual || 'No Output')}
                        </pre>
                        <button
                          onClick={() => copyToClipboard(String(r.actual || ''))}
                          className="px-2 py-1 app-btn-secondary rounded text-xs"
                          title="Copy your output"
                        >
                          <CopyIcon className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </div>}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {/* Submit result panel - Show this prominently */}
          {submitResult && (
            <div className="mt-6 p-4 rounded-lg border-2 border-blue-500 app-surface">
              {submitResult.status === "timeout" && (
                <div className="p-6 text-center text-yellow-300">
                  <div className="text-xl font-bold mb-2">Submission timed out</div>
                  <div>The execution server did not finish within its time limit.</div>
                </div>
              )}
              {submitResult.status === "cancelled" && (
                <div className="p-6 text-center text-gray-300">
                  <div className="text-xl font-bold mb-2">Submission cancelled</div>
                  <div>No result was recorded for this submission.</div>
                </div>
              )}
              {submitResult.status === "still-processing" && (
                <div className="p-6 text-center text-yellow-300">
                  <div className="text-xl font-bold mb-2">Still processing</div>
                  <div className="mb-4">The job is still running. Reconnect to check its status.</div>
                  <button onClick={reconnectToJob} className="px-3 py-2 bg-yellow-700 hover:bg-yellow-600 rounded" type="button">
                    Refresh status
                  </button>
                </div>
              )}
              {(submitResult.status === "accepted" || submitResult.status === "failed") && (
              <>
              {submitResult.status === "accepted" ? (
                <div className="flex flex-col items-center justify-center p-6">
                  {showAccepted && (
                    <div className="text-4xl mb-2 font-bold text-green-600 dark:text-green-400">Accepted</div>
                  )}
                  <div className="text-green-700 dark:text-green-400 text-2xl font-bold mb-2">
                    Solution Accepted!
                  </div>
                  <div className="text-green-800 dark:text-green-200 text-lg mb-2">
                    All {submitResult.totalCount} test cases passed.
                  </div>
                  {(executionTime || executionMemory) && (
                    <div className="text-green-800 dark:text-green-300 text-sm flex gap-4 justify-center">
                      {executionTime && <span>Time: {formatExecutionTime(executionTime)}</span>}
                      {executionMemory && <span>Memory: {formatMemory(executionMemory)}</span>}
                    </div>
                  )}
                </div>
              ) : (
                <SubmitFailurePanel submitResult={submitResult} />
              )}
              </>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
};

function SubmitFailurePanel({ submitResult }) {
  const hidden = submitResult.failedCase?.type === "hidden";
  const verdict = submitResult.failureReason || submitResult.failedCase?.error?.type || "WRONG_ANSWER";
  const copy = studentVerdictCopy({
    verdict,
    hidden,
    passedCount: submitResult.passedCount,
    totalCount: submitResult.totalCount,
    errorMessage: submitResult.failedCase?.error?.message || submitResult.diagnosticMessage,
    errorCode: submitResult.errorCode,
    diagnosticMessage: submitResult.diagnosticMessage
  });
  const reference = formatSupportReference(submitResult.reference);
  const showPublicIo = copy.showHiddenIo && submitResult.failedCase;
  return (
    <div className={`p-6 rounded-lg border-2 ${resultCardClasses(false)}`}>
      <div className="font-bold text-xl mb-2">
        {copy.title}
        {verdict && (
          <span className="ml-2 text-sm font-normal">({String(verdict).replace(/_/g, " ")})</span>
        )}
      </div>
      <div className="mb-3">{copy.summary}</div>
      {copy.detail && <div className="mb-3">{copy.detail}</div>}
      {copy.code === "COMPILATION_ERROR" && submitResult.failedCase?.error?.message && (
        <pre className="app-code p-3 rounded text-xs whitespace-pre-wrap mb-3">
          {submitResult.failedCase.error.message}
        </pre>
      )}
      {copy.code === "RUNTIME_ERROR" && submitResult.failedCase?.error?.message && (
        <pre className="app-code p-3 rounded text-xs whitespace-pre-wrap mb-3">
          {submitResult.failedCase.error.message}
        </pre>
      )}
      {hidden && copy.code === "WRONG_ANSWER" && (
        <div className="mb-3">Hidden test details are not shown.</div>
      )}
      {showPublicIo && (
        <>
          <div className="mb-3">
            <span className="font-bold">Input:</span>
            <pre className="mt-1 app-code p-2 rounded whitespace-pre-wrap text-sm">
              {submitResult.failedCase.input || "N/A"}
            </pre>
          </div>
          <div className="mb-3">
            <span className="font-bold">Expected Output:</span>
            <pre className="mt-1 app-code p-2 rounded whitespace-pre-wrap text-sm">
              {submitResult.failedCase.output || "N/A"}
            </pre>
          </div>
          <div className="mb-3">
            <span className="font-bold">Your Output:</span>
            <pre className="mt-1 app-code p-2 rounded whitespace-pre-wrap text-sm">
              {submitResult.failedCase.actual || "N/A"}
            </pre>
          </div>
        </>
      )}
      {Number.isFinite(submitResult.passedCount) && Number.isFinite(submitResult.totalCount) && (
        <div className="mt-4 pt-4 border-t border-current/30">
          Passed {submitResult.passedCount} of {submitResult.totalCount} test cases.
        </div>
      )}
      {reference && (
        <div className="mt-3 text-sm">
          Reference: {reference}
        </div>
      )}
      {copy.code && (
        <div className="mt-1 text-xs app-muted">{copy.code}</div>
      )}
    </div>
  );
}

export default Judge0CodeEditor;
