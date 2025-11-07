import React, { useState, useEffect } from "react";
import { useParams, Link } from "react-router-dom";
import Judge0CodeEditor from "./Judge0CodeEditor";

const ProblemPageNew = ({ problems }) => {
  const { id } = useParams();
  const [problem, setProblem] = useState(null);
  const [loading, setLoading] = useState(true);
  const [leftWidth, setLeftWidth] = useState(50); // Percentage
  const [isDragging, setIsDragging] = useState(false);

  useEffect(() => {
    // If problems are passed as props, use them
    if (problems && problems.length > 0) {
      const found = problems.find((p) => p.id === id);
      setProblem(found);
      setLoading(false);
    } else {
      // Otherwise fetch from API
      fetchProblem();
    }
  }, [id, problems]);

  const fetchProblem = async () => {
    try {
      const response = await fetch(`http://localhost:8080/api/problems/${id}`);
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

  // Handle mouse events for resizing
  const handleMouseDown = (e) => {
    setIsDragging(true);
    e.preventDefault();
  };

  const handleMouseMove = (e) => {
    if (!isDragging) return;
    
    const containerWidth = window.innerWidth;
    const newLeftWidth = (e.clientX / containerWidth) * 100;
    
    // Constrain between 20% and 80%
    const constrainedWidth = Math.min(Math.max(newLeftWidth, 20), 80);
    setLeftWidth(constrainedWidth);
  };

  const handleMouseUp = () => {
    setIsDragging(false);
  };

  useEffect(() => {
    if (isDragging) {
      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
    } else {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    }

    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [isDragging]);

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="text-white text-xl">Loading problem...</div>
      </div>
    );
  }

  if (!problem) {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="text-center">
          <div className="text-white text-xl mb-4">Problem not found</div>
          <Link to="/home" className="text-blue-400 hover:text-blue-300">
            Back to Problems
          </Link>
        </div>
      </div>
    );
  }

  const isStdinMode = problem.executionMode === "STDIN_STDOUT" || problem.publicTestCases;

  return (
    <div className="min-h-screen bg-gray-900 text-white">
      <div className="flex h-screen">
        {/* Left Panel - Problem Description */}
        <div 
          className="bg-gray-800 overflow-auto border-r border-gray-700"
          style={{ width: `${leftWidth}%` }}
        >
          <div className="p-6 max-w-full">
            {/* Header */}
            <div className="mb-6">
              <Link
                to="/home"
                className="text-blue-400 hover:text-blue-300 mb-4 inline-block"
              >
                ← Back to Problems
              </Link>
              <h1 className="text-3xl font-bold mb-3">{problem.title}</h1>
              
              <div className="flex items-center space-x-3">
                <span
                  className={`px-3 py-1 rounded-full text-sm font-semibold ${
                    problem.difficulty === "EASY"
                      ? "bg-green-500"
                      : problem.difficulty === "MEDIUM"
                      ? "bg-yellow-500"
                      : "bg-red-500"
                  }`}
                >
                  {problem.difficulty}
                </span>
                
                {problem.category && (
                  <span className="px-3 py-1 bg-gray-700 rounded-full text-sm">
                    {problem.category.replace('_', ' ')}
                  </span>
                )}
                
                {problem.isPremium && (
                  <span className="px-3 py-1 bg-yellow-500 text-black rounded-full text-sm font-semibold">
                    PREMIUM
                  </span>
                )}
              </div>
            </div>

            {/* Tags */}
            {problem.tags && problem.tags.length > 0 && (
              <div className="mb-6">
                <div className="flex flex-wrap gap-2">
                  {problem.tags.map((tag, index) => (
                    <span
                      key={index}
                      className="px-3 py-1 bg-gray-700 rounded-full text-sm text-gray-300"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* Description */}
            <div className="mb-6">
              <h2 className="text-xl font-bold mb-3">Description</h2>
              <div className="text-gray-300 whitespace-pre-wrap">
                {problem.statement || problem.description}
              </div>
            </div>

            {/* Input Format (for stdin/stdout mode) */}
            {isStdinMode && problem.inputFormat && (
              <div className="mb-6">
                <h2 className="text-xl font-bold mb-3">Input Format</h2>
                <div className="bg-gray-900 p-4 rounded-lg border border-gray-700">
                  <pre className="text-gray-300 whitespace-pre-wrap font-mono text-sm">
                    {problem.inputFormat}
                  </pre>
                </div>
              </div>
            )}

            {/* Output Format (for stdin/stdout mode) */}
            {isStdinMode && problem.outputFormat && (
              <div className="mb-6">
                <h2 className="text-xl font-bold mb-3">Output Format</h2>
                <div className="bg-gray-900 p-4 rounded-lg border border-gray-700">
                  <pre className="text-gray-300 whitespace-pre-wrap font-mono text-sm">
                    {problem.outputFormat}
                  </pre>
                </div>
              </div>
            )}

            {/* Constraints */}
            {problem.constraints && (
              <div className="mb-6">
                <h2 className="text-xl font-bold mb-3">Constraints</h2>
                <div className="bg-gray-900 p-4 rounded-lg border border-gray-700">
                  <pre className="text-gray-300 whitespace-pre-wrap font-mono text-sm">
                    {problem.constraints}
                  </pre>
                </div>
              </div>
            )}

            {/* Example (for stdin/stdout mode) */}
            {isStdinMode && problem.exampleInput && problem.exampleOutput && (
              <div className="mb-6">
                <h2 className="text-xl font-bold mb-3">Example</h2>
                <div className="bg-gray-900 p-4 rounded-lg border border-gray-700">
                  <div className="mb-3">
                    <div className="text-sm text-gray-400 mb-1">Input:</div>
                    <pre className="text-green-400 font-mono text-sm">
                      {problem.exampleInput}
                    </pre>
                  </div>
                  <div className="mb-3">
                    <div className="text-sm text-gray-400 mb-1">Output:</div>
                    <pre className="text-green-400 font-mono text-sm">
                      {problem.exampleOutput}
                    </pre>
                  </div>
                  {problem.exampleExplanation && (
                    <div>
                      <div className="text-sm text-gray-400 mb-1">Explanation:</div>
                      <div className="text-gray-300 text-sm">
                        {problem.exampleExplanation}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Points and Time */}
            {(problem.points || problem.estimatedTime) && (
              <div className="flex items-center space-x-6 mb-6 text-sm text-gray-400">
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
          </div>
        </div>

        {/* Resizable Divider */}
        <div
          className={`w-1 bg-gray-600 hover:bg-gray-500 cursor-col-resize flex-shrink-0 ${
            isDragging ? 'bg-blue-500' : ''
          }`}
          onMouseDown={handleMouseDown}
        >
          <div className="w-full h-full flex items-center justify-center">
            <div className="w-0.5 h-8 bg-gray-400 rounded-full"></div>
          </div>
        </div>

        {/* Right Panel - Code Editor */}
        <div 
          className="bg-gray-900 overflow-hidden"
          style={{ width: `${100 - leftWidth}%` }}
        >
          <Judge0CodeEditor
            initialCode={problem.template || ""}
            testCases={problem.testCases || []}
            hiddenTestCases={problem.hiddenTestCases || []}
            functionName={problem.functionName}
            parameters={problem.parameters || []}
            problemId={problem.id}
            onSubmissionSuccess={() => {
              console.log("Problem solved!");
            }}
          />
        </div>
      </div>
    </div>
  );
};

export default ProblemPageNew;

