import React, { useState, useEffect } from "react";
import axios from "axios";
import "../index.css";
import { getTemplate, LANGUAGE_NAMES, FILE_EXTENSIONS } from "./LanguageTemplates";

const SimpleCodeEditor = ({ 
  problemId,
  testCases = [], 
  hiddenTestCases = [],
  onSubmissionSuccess 
}) => {
  const [languageId, setLanguageId] = useState(62); // Default: Java
  const [sourceCode, setSourceCode] = useState("");
  const [output, setOutput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [results, setResults] = useState([]);
  const [submitResult, setSubmitResult] = useState(null);
  const [showAccepted, setShowAccepted] = useState(false);
  const [customInput, setCustomInput] = useState("");
  const [activeTab, setActiveTab] = useState("testcases"); // testcases | custom | results

  // Load template when language changes
  useEffect(() => {
    const template = getTemplate(languageId);
    setSourceCode(template);
    setResults([]);
    setOutput("");
    setSubmitResult(null);
  }, [languageId]);

  const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

  // Post to Judge0 API with retry logic
  const postToJudge0 = async (payload, retries = 3) => {
    const API_URL = "https://judge0-ce.p.rapidapi.com/submissions";
    const API_KEY = import.meta.env.VITE_RAPIDAPI_KEY;

    if (!API_KEY) {
      throw new Error("RapidAPI key not found. Please set VITE_RAPIDAPI_KEY in your .env file.");
    }

    for (let attempt = 0; attempt < retries; attempt++) {
      try {
        // Create submission
        const createResponse = await axios.post(
          `${API_URL}?base64_encoded=false&wait=false`,
          payload,
          {
            headers: {
              "content-type": "application/json",
              "X-RapidAPI-Key": API_KEY,
              "X-RapidAPI-Host": "judge0-ce.p.rapidapi.com",
            },
          }
        );

        const token = createResponse.data.token;
        await sleep(2000); // Wait for execution

        // Get result
        const resultResponse = await axios.get(`${API_URL}/${token}`, {
          headers: {
            "X-RapidAPI-Key": API_KEY,
            "X-RapidAPI-Host": "judge0-ce.p.rapidapi.com",
          },
          params: { base64_encoded: "false" },
        });

        return resultResponse;
      } catch (error) {
        console.error(`Attempt ${attempt + 1} failed:`, error);
        if (attempt === retries - 1) {
          if (error.response?.status === 401) {
            throw new Error("Invalid API key. Please check your VITE_RAPIDAPI_KEY.");
          } else if (error.response?.status === 429) {
            throw new Error("Rate limit exceeded. Please try again later.");
          } else {
            throw new Error(error.response?.data?.error || error.message || "Unknown error");
          }
        }
        await sleep(1000 * (attempt + 1));
      }
    }
  };

  // Run code with custom input
  const handleRunCustom = async () => {
    setIsLoading(true);
    setOutput("");
    setActiveTab("results");

    try {
      const response = await postToJudge0({
        language_id: languageId,
        source_code: sourceCode,
        stdin: customInput,
      });

      const res = response.data;
      const result = res.stdout || res.compile_output || res.stderr || "No Output";
      setOutput(result);
    } catch (error) {
      setOutput("Error: " + (error.message || "Unknown error"));
    } finally {
      setIsLoading(false);
    }
  };

  // Run all test cases
  const handleRunTestCases = async () => {
    setIsLoading(true);
    setResults([]);
    setOutput("");
    setActiveTab("results");

    try {
      const allResults = [];
      
      for (const tc of testCases) {
        const response = await postToJudge0({
          language_id: languageId,
          source_code: sourceCode,
          stdin: tc.input,
        });

        const res = response.data;
        const actual = (res.stdout || "").trim();
        const expected = tc.output.trim();
        const passed = actual === expected;

        allResults.push({
          input: tc.input,
          expected,
          actual,
          passed,
          explanation: tc.explanation || ""
        });

        await sleep(600); // Rate limiting
      }

      setResults(allResults);
    } catch (error) {
      setOutput("Error running test cases: " + (error.message || "Unknown error"));
    } finally {
      setIsLoading(false);
    }
  };

  // Submit solution
  const handleSubmit = async () => {
    setIsLoading(true);
    setSubmitResult(null);
    setShowAccepted(false);
    setActiveTab("results");

    try {
      let passedCount = 0;
      const totalCount = testCases.length + hiddenTestCases.length;

      // Test public cases
      for (let i = 0; i < testCases.length; i++) {
        const tc = testCases[i];
        const response = await postToJudge0({
          language_id: languageId,
          source_code: sourceCode,
          stdin: tc.input,
        });

        const res = response.data;
        const actual = (res.stdout || "").trim();
        const expected = tc.output.trim();
        const passed = actual === expected;

        if (!passed) {
          setSubmitResult({
            status: "failed",
            failedCase: { ...tc, actual, type: "public", index: i + 1 },
            passedCount,
            totalCount,
          });
          await saveSubmission("WRONG_ANSWER", `Failed on public test case ${i + 1}`);
          setIsLoading(false);
          return;
        }

        passedCount++;
        await sleep(600);
      }

      // Test hidden cases
      for (let i = 0; i < hiddenTestCases.length; i++) {
        const tc = hiddenTestCases[i];
        const response = await postToJudge0({
          language_id: languageId,
          source_code: sourceCode,
          stdin: tc.input,
        });

        const res = response.data;
        const actual = (res.stdout || "").trim();
        const expected = tc.output.trim();
        const passed = actual === expected;

        if (!passed) {
          setSubmitResult({
            status: "failed",
            failedCase: { type: "hidden", index: i + 1 },
            passedCount,
            totalCount,
          });
          await saveSubmission("WRONG_ANSWER", `Failed on hidden test case ${i + 1}`);
          setIsLoading(false);
          return;
        }

        passedCount++;
        await sleep(600);
      }

      // All passed!
      setSubmitResult({ status: "accepted", passedCount, totalCount });
      setShowAccepted(true);
      setTimeout(() => setShowAccepted(false), 3000);
      await saveSubmission("ACCEPTED", "All test cases passed");
      
      if (onSubmissionSuccess) {
        onSubmissionSuccess();
      }
    } catch (error) {
      setOutput("Error during submission: " + (error.message || "Unknown error"));
    } finally {
      setIsLoading(false);
    }
  };

  // Save submission to backend
  const saveSubmission = async (status, output) => {
    try {
      const token = localStorage.getItem('jwtToken');
      if (!token || !problemId) return;

      const submissionData = {
        problemId,
        code: sourceCode,
        language: LANGUAGE_NAMES[languageId] || "Unknown",
        status,
        output,
      };

      await axios.post('http://localhost:8080/api/submissions', submissionData, {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });
    } catch (error) {
      console.error('Error saving submission:', error);
    }
  };

  // Download code
  const downloadCode = () => {
    const extension = FILE_EXTENSIONS[languageId] || "txt";
    const blob = new Blob([sourceCode], { type: "text/plain" });
    const link = document.createElement("a");
    link.download = `solution.${extension}`;
    link.href = URL.createObjectURL(blob);
    link.click();
  };

  return (
    <div className="flex flex-col h-full bg-gray-900 text-white">
      {/* Toolbar */}
      <div className="flex justify-between items-center p-4 bg-gray-800 border-b border-gray-700">
        <div className="flex items-center space-x-4">
          <select
            value={languageId}
            onChange={(e) => setLanguageId(Number(e.target.value))}
            className="bg-gray-700 text-white px-4 py-2 rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500"
          >
            <option value={62}>Java</option>
            <option value={71}>Python</option>
            <option value={54}>C++</option>
            <option value={50}>C</option>
            <option value={51}>C#</option>
            <option value={63}>JavaScript</option>
            <option value={74}>TypeScript</option>
            <option value={60}>Go</option>
            <option value={68}>PHP</option>
            <option value={72}>Ruby</option>
            <option value={73}>Rust</option>
            <option value={78}>Kotlin</option>
            <option value={81}>Scala</option>
            <option value={83}>Swift</option>
          </select>

          <button
            onClick={downloadCode}
            className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors"
            title="Download Code"
          >
            Download
          </button>
        </div>

        <div className="flex items-center space-x-2">
          <button
            onClick={handleRunTestCases}
            disabled={isLoading || testCases.length === 0}
            className="px-6 py-2 bg-green-600 hover:bg-green-700 disabled:bg-gray-600 rounded-lg font-semibold transition-colors"
          >
            {isLoading ? "Running..." : "Run"}
          </button>
          
          <button
            onClick={handleSubmit}
            disabled={isLoading}
            className="px-6 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-600 rounded-lg font-semibold transition-colors"
          >
            {isLoading ? "Submitting..." : "Submit"}
          </button>
        </div>
      </div>

      {/* Editor */}
      <div className="flex-1 overflow-auto">
        <textarea
          value={sourceCode}
          onChange={(e) => setSourceCode(e.target.value)}
          className="w-full h-full p-4 bg-gray-800 border border-gray-700 font-mono text-sm resize-none focus:outline-none focus:ring-2 focus:ring-blue-500 text-white"
          placeholder="Write your code here..."
          spellCheck={false}
        />
      </div>

      {/* Bottom Panel - Tabs */}
      <div className="h-80 border-t border-gray-700">
        {/* Tab Headers */}
        <div className="flex border-b border-gray-700 bg-gray-800">
          <button
            onClick={() => setActiveTab("testcases")}
            className={`px-6 py-3 font-semibold ${activeTab === "testcases" ? "bg-gray-900 text-blue-400 border-b-2 border-blue-400" : "text-gray-400 hover:text-white"}`}
          >
            Test Cases ({testCases.length})
          </button>
          <button
            onClick={() => setActiveTab("custom")}
            className={`px-6 py-3 font-semibold ${activeTab === "custom" ? "bg-gray-900 text-blue-400 border-b-2 border-blue-400" : "text-gray-400 hover:text-white"}`}
          >
            Custom Input
          </button>
          <button
            onClick={() => setActiveTab("results")}
            className={`px-6 py-3 font-semibold ${activeTab === "results" ? "bg-gray-900 text-blue-400 border-b-2 border-blue-400" : "text-gray-400 hover:text-white"}`}
          >
            Results
          </button>
        </div>

        {/* Tab Content */}
        <div className="h-full overflow-auto p-4 bg-gray-900">
          {activeTab === "testcases" && (
            <div className="space-y-4">
              {testCases.map((tc, idx) => (
                <div key={idx} className="bg-gray-800 rounded-lg p-4 border border-gray-700">
                  <div className="font-semibold mb-2">Test Case {idx + 1}</div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <div className="text-sm text-gray-400 mb-1">Input:</div>
                      <pre className="bg-gray-900 p-2 rounded text-sm overflow-auto">{tc.input}</pre>
                    </div>
                    <div>
                      <div className="text-sm text-gray-400 mb-1">Expected Output:</div>
                      <pre className="bg-gray-900 p-2 rounded text-sm overflow-auto">{tc.output}</pre>
                    </div>
                  </div>
                  {tc.explanation && (
                    <div className="mt-2 text-sm text-gray-400">
                      <span className="font-semibold">Explanation:</span> {tc.explanation}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}

          {activeTab === "custom" && (
            <div className="space-y-4">
              <div>
                <label className="block text-sm text-gray-400 mb-2">Custom Input (stdin):</label>
                <textarea
                  value={customInput}
                  onChange={(e) => setCustomInput(e.target.value)}
                  className="w-full h-32 bg-gray-800 text-white p-3 rounded-lg border border-gray-700 focus:outline-none focus:border-blue-500 font-mono"
                  placeholder="Enter your custom input here..."
                />
              </div>
              <button
                onClick={handleRunCustom}
                disabled={isLoading}
                className="px-6 py-2 bg-green-600 hover:bg-green-700 disabled:bg-gray-600 rounded-lg font-semibold transition-colors"
              >
                {isLoading ? "⏳ Running..." : "▶️ Run with Custom Input"}
              </button>
            </div>
          )}

          {activeTab === "results" && (
            <div>
              {/* Submission Result */}
              {submitResult && (
                <div className={`mb-4 p-4 rounded-lg ${submitResult.status === "accepted" ? "bg-green-900/50 border border-green-500" : "bg-red-900/50 border border-red-500"}`}>
                  {submitResult.status === "accepted" ? (
                    <div>
                      <div className="text-2xl font-bold text-green-400 mb-2">✅ Accepted!</div>
                      <div className="text-green-200">
                        All test cases passed ({submitResult.passedCount}/{submitResult.totalCount})
                      </div>
                    </div>
                  ) : (
                    <div>
                      <div className="text-2xl font-bold text-red-400 mb-2">❌ Wrong Answer</div>
                      <div className="text-red-200 mb-2">
                        Failed on {submitResult.failedCase.type} test case #{submitResult.failedCase.index}
                      </div>
                      {submitResult.failedCase.type === "public" && (
                        <div className="grid grid-cols-2 gap-4 mt-3">
                          <div>
                            <div className="text-sm text-gray-400 mb-1">Input:</div>
                            <pre className="bg-gray-900 p-2 rounded text-sm overflow-auto">{submitResult.failedCase.input}</pre>
                          </div>
                          <div>
                            <div className="text-sm text-gray-400 mb-1">Expected:</div>
                            <pre className="bg-gray-900 p-2 rounded text-sm overflow-auto text-green-400">{submitResult.failedCase.expected}</pre>
                            <div className="text-sm text-gray-400 mb-1 mt-2">Your Output:</div>
                            <pre className="bg-gray-900 p-2 rounded text-sm overflow-auto text-red-400">{submitResult.failedCase.actual}</pre>
                          </div>
                        </div>
                      )}
                      <div className="mt-2 text-sm text-gray-400">
                        Passed: {submitResult.passedCount}/{submitResult.totalCount}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Test Results */}
              {results.length > 0 && (
                <div className="space-y-3">
                  <div className="font-semibold text-lg mb-3">Test Results:</div>
                  {results.map((result, idx) => (
                    <div key={idx} className={`p-4 rounded-lg border ${result.passed ? "bg-green-900/30 border-green-500" : "bg-red-900/30 border-red-500"}`}>
                      <div className="flex items-center justify-between mb-2">
                        <div className="font-semibold">Test Case {idx + 1}</div>
                        <div className={`font-bold ${result.passed ? "text-green-400" : "text-red-400"}`}>
                          {result.passed ? "✓ PASSED" : "✗ FAILED"}
                        </div>
                      </div>
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <div className="text-sm text-gray-400 mb-1">Input:</div>
                          <pre className="bg-gray-900 p-2 rounded text-sm overflow-auto">{result.input}</pre>
                        </div>
                        <div>
                          <div className="text-sm text-gray-400 mb-1">Expected:</div>
                          <pre className="bg-gray-900 p-2 rounded text-sm overflow-auto text-green-400">{result.expected}</pre>
                          <div className="text-sm text-gray-400 mb-1 mt-2">Your Output:</div>
                          <pre className={`bg-gray-900 p-2 rounded text-sm overflow-auto ${result.passed ? "text-green-400" : "text-red-400"}`}>{result.actual}</pre>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Custom Run Output */}
              {output && !results.length && !submitResult && (
                <div>
                  <div className="font-semibold text-lg mb-3">Output:</div>
                  <pre className="bg-gray-800 border border-gray-700 p-4 rounded-lg overflow-auto text-green-300">
                    {output}
                  </pre>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Accepted Animation */}
      {showAccepted && (
        <div className="fixed inset-0 flex items-center justify-center pointer-events-none">
          <div className="bg-green-500 text-white text-4xl font-bold p-8 rounded-lg shadow-2xl animate-bounce">
            🎉 ACCEPTED! 🎉
          </div>
        </div>
      )}
    </div>
  );
};

export default SimpleCodeEditor;

