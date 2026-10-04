import React, { useState, useEffect } from "react";
import { useParams, Link } from "react-router-dom";
import Judge0CodeEditor from "./Judge0CodeEditor";
import ProblemSubmissionHistory from "./ProblemSubmissionHistory";
import { BackArrowIcon } from "../icons/AppIcons.jsx";

const ProblemPageNew = ({ problems }) => {
  const { id } = useParams();
  const [problem, setProblem] = useState(null);
  const [loading, setLoading] = useState(true);
  const [leftWidth, setLeftWidth] = useState(50);
  const [isDragging, setIsDragging] = useState(false);
  const [contentTab, setContentTab] = useState("problem");
  const [submissionsOpened, setSubmissionsOpened] = useState(false);
  const [historyRefreshKey, setHistoryRefreshKey] = useState(0);

  useEffect(() => {
    if (problems && problems.length > 0) {
      const found = problems.find((p) => p.id === id);
      setProblem(found);
      setLoading(false);
    } else {
      fetchProblem();
    }
  }, [id, problems]);

  const fetchProblem = async () => {
    try {
      const response = await fetch(`/api/problems/${id}`);
      if (response.ok) {
        const data = await response.json();
        setProblem(data);
      }
    } catch (error) {
      console.error("Error fetching problem:", error);
    } finally {
      setLoading(false);
    }
  };

  const handleMouseDown = (e) => {
    setIsDragging(true);
    e.preventDefault();
  };

  const handleMouseMove = (e) => {
    if (!isDragging) return;
    const containerWidth = window.innerWidth;
    const newLeftWidth = (e.clientX / containerWidth) * 100;
    const constrainedWidth = Math.min(Math.max(newLeftWidth, 20), 80);
    setLeftWidth(constrainedWidth);
  };

  const handleMouseUp = () => {
    setIsDragging(false);
  };

  useEffect(() => {
    if (isDragging) {
      document.addEventListener("mousemove", handleMouseMove);
      document.addEventListener("mouseup", handleMouseUp);
      document.body.style.cursor = "col-resize";
      document.body.style.userSelect = "none";
    } else {
      document.removeEventListener("mousemove", handleMouseMove);
      document.removeEventListener("mouseup", handleMouseUp);
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
    }

    return () => {
      document.removeEventListener("mousemove", handleMouseMove);
      document.removeEventListener("mouseup", handleMouseUp);
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
    };
  }, [isDragging]);

  if (loading) {
    return (
      <div className="app-shell flex items-center justify-center">
        <div className="text-xl">Loading problem...</div>
      </div>
    );
  }

  if (!problem) {
    return (
      <div className="app-shell flex items-center justify-center">
        <div className="text-center">
          <div className="text-xl mb-4">Problem not found</div>
          <Link
            to="/home"
            aria-label="Back to problems"
            title="Back to problems"
            className="inline-flex h-10 w-10 items-center justify-center rounded-lg app-btn-secondary"
          >
            <BackArrowIcon className="h-5 w-5" />
          </Link>
        </div>
      </div>
    );
  }

  const isStdinMode = problem.executionMode !== "FUNCTION_HARNESS_BATCH";
  const tabClass = (name) =>
    `px-4 py-2 font-semibold ${
      contentTab === name
        ? "text-blue-600 dark:text-blue-400 border-b-2 border-blue-600 dark:border-blue-400"
        : "app-muted"
    }`;

  return (
    <div className="app-shell">
      <div className="flex h-screen">
        <div
          className="overflow-auto border-r border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800"
          style={{ width: `${leftWidth}%` }}
        >
          <div className="p-6 max-w-full">
            <div className="flex items-center gap-2 mb-3">
              <Link
                to="/home"
                aria-label="Back to problems"
                title="Back to problems"
                className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-lg app-btn-secondary"
              >
                <BackArrowIcon className="h-5 w-5" />
              </Link>
              <h1 className="text-3xl font-bold m-0">{problem.title}</h1>
            </div>
            <div className="flex mb-6 border-b border-gray-200 dark:border-gray-700">
              <button type="button" className={tabClass("problem")} onClick={() => setContentTab("problem")}>
                Problem
              </button>
              <button
                type="button"
                className={tabClass("submissions")}
                onClick={() => {
                  setSubmissionsOpened(true);
                  setContentTab("submissions");
                }}
              >
                Submissions
              </button>
            </div>

            {contentTab === "problem" && (
              <>
                <div className="flex items-center space-x-3 mb-6">
                  <span
                    className={`px-3 py-1 rounded-full text-sm font-semibold ${
                      problem.difficulty === "EASY"
                        ? "bg-green-500 text-white"
                        : problem.difficulty === "MEDIUM"
                        ? "bg-yellow-500 text-black"
                        : "bg-red-500 text-white"
                    }`}
                  >
                    {problem.difficulty}
                  </span>
                  {problem.category && (
                    <span className="px-3 py-1 bg-gray-200 dark:bg-gray-700 rounded-full text-sm">
                      {problem.category.replace("_", " ")}
                    </span>
                  )}
                  {problem.isPremium && (
                    <span className="px-3 py-1 bg-yellow-500 text-black rounded-full text-sm font-semibold">
                      PREMIUM
                    </span>
                  )}
                </div>

                {problem.tags && problem.tags.length > 0 && (
                  <div className="mb-6">
                    <div className="flex flex-wrap gap-2">
                      {problem.tags.map((tag, index) => (
                        <span
                          key={index}
                          className="px-3 py-1 bg-gray-200 dark:bg-gray-700 rounded-full text-sm app-muted"
                        >
                          {tag}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                <div className="mb-6">
                  <h2 className="text-xl font-bold mb-3">Description</h2>
                  <div className="whitespace-pre-wrap">
                    {problem.statement || problem.description}
                  </div>
                </div>

                {isStdinMode && problem.inputFormat && (
                  <div className="mb-6">
                    <h2 className="text-xl font-bold mb-3">Input Format</h2>
                    <div className="app-code p-4 rounded-lg">
                      <pre className="whitespace-pre-wrap font-mono text-sm">
                        {problem.inputFormat}
                      </pre>
                    </div>
                  </div>
                )}

                {isStdinMode && problem.outputFormat && (
                  <div className="mb-6">
                    <h2 className="text-xl font-bold mb-3">Output Format</h2>
                    <div className="app-code p-4 rounded-lg">
                      <pre className="whitespace-pre-wrap font-mono text-sm">
                        {problem.outputFormat}
                      </pre>
                    </div>
                  </div>
                )}

                {isStdinMode && problem.publicTestCases && problem.publicTestCases.length > 0 && (
                  <div className="mb-6">
                    <h2 className="text-xl font-bold mb-3">Test Cases</h2>
                    <div className="space-y-4">
                      {problem.publicTestCases.map((testCase, index) => (
                        <div key={index} className="app-inset p-4 rounded-lg">
                          <div className="mb-3">
                            <div className="text-sm app-muted mb-1 font-semibold">Test Case #{index + 1}</div>
                          </div>
                          <div className="grid grid-cols-2 gap-4">
                            <div>
                              <div className="text-sm app-muted mb-1">Input:</div>
                              <pre className="text-green-700 dark:text-green-400 font-mono text-sm app-code p-3 rounded">
                                {testCase.input}
                              </pre>
                            </div>
                            <div>
                              <div className="text-sm app-muted mb-1">Output:</div>
                              <pre className="text-green-700 dark:text-green-400 font-mono text-sm app-code p-3 rounded">
                                {testCase.output}
                              </pre>
                            </div>
                          </div>
                          {testCase.explanation && (
                            <div className="mt-3">
                              <div className="text-sm app-muted mb-1">Explanation:</div>
                              <div className="text-sm">{testCase.explanation}</div>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {problem.constraints && (
                  <div className="mb-6">
                    <h2 className="text-xl font-bold mb-3">Constraints</h2>
                    <div className="app-code p-4 rounded-lg">
                      <pre className="whitespace-pre-wrap font-mono text-sm">
                        {problem.constraints}
                      </pre>
                    </div>
                  </div>
                )}

                {isStdinMode && problem.exampleInput && problem.exampleOutput && (
                  <div className="mb-6">
                    <h2 className="text-xl font-bold mb-3">Example</h2>
                    <div className="app-inset p-4 rounded-lg">
                      <div className="mb-3">
                        <div className="text-sm app-muted mb-1">Input:</div>
                        <pre className="text-green-700 dark:text-green-400 font-mono text-sm">
                          {problem.exampleInput}
                        </pre>
                      </div>
                      <div className="mb-3">
                        <div className="text-sm app-muted mb-1">Output:</div>
                        <pre className="text-green-700 dark:text-green-400 font-mono text-sm">
                          {problem.exampleOutput}
                        </pre>
                      </div>
                      {problem.exampleExplanation && (
                        <div>
                          <div className="text-sm app-muted mb-1">Explanation:</div>
                          <div className="text-sm">{problem.exampleExplanation}</div>
                        </div>
                      )}
                    </div>
                  </div>
                )}

                {(problem.points || problem.estimatedTime) && (
                  <div className="flex items-center space-x-6 mb-6 text-sm app-muted">
                    {problem.points && (
                      <div>
                        <span className="font-semibold">Points:</span> {problem.points}
                      </div>
                    )}
                    {problem.estimatedTime && (
                      <div>
                        <span className="font-semibold">Estimated Time:</span>{" "}
                        {problem.estimatedTime} min
                      </div>
                    )}
                  </div>
                )}
              </>
            )}

            {submissionsOpened && (
              <div className={contentTab === "submissions" ? "" : "hidden"}>
                <ProblemSubmissionHistory
                  problemId={problem.id}
                  numericId={problem.numericId}
                  enabled={contentTab === "submissions"}
                  refreshKey={historyRefreshKey}
                />
              </div>
            )}
          </div>
        </div>

        <div
          className={`w-1 bg-gray-300 dark:bg-gray-600 hover:bg-gray-400 dark:hover:bg-gray-500 cursor-col-resize flex-shrink-0 ${
            isDragging ? "bg-blue-500" : ""
          }`}
          onMouseDown={handleMouseDown}
        >
          <div className="w-full h-full flex items-center justify-center">
            <div className="w-0.5 h-8 bg-gray-400 rounded-full"></div>
          </div>
        </div>

        <div
          className="overflow-auto bg-gray-50 dark:bg-gray-900"
          style={{ width: `${100 - leftWidth}%` }}
        >
          <div className="p-4">
            <Judge0CodeEditor
              initialCode={problem.template || ""}
              testCases={isStdinMode ? (problem.publicTestCases || []) : (problem.testCases || [])}
              functionName={problem.functionName}
              parameters={problem.parameters || []}
              problemId={problem.id}
              executionMode={problem.executionMode || "STDIN_STDOUT"}
              onSubmissionSuccess={() => {
                setHistoryRefreshKey((current) => current + 1);
              }}
            />
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProblemPageNew;
