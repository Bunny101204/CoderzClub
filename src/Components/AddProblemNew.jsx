import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";

const AddProblemNew = () => {
  const navigate = useNavigate();
  
  // Basic problem details
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [difficulty, setDifficulty] = useState("EASY");
  const [tags, setTags] = useState("");
  const [category, setCategory] = useState("ALGORITHMS");
  
  // New stdin/stdout fields
  const [inputFormat, setInputFormat] = useState("");
  const [outputFormat, setOutputFormat] = useState("");
  const [constraints, setConstraints] = useState("");
  // Example
  const [examples, setExamples] = useState([
    { input: "", output: "", explanation: "" }
  ]);
  
  // Test cases
  const [publicTestCases, setPublicTestCases] = useState([
    { input: "", output: "", explanation: "" }
  ]);
  const [hiddenTestCases, setHiddenTestCases] = useState([
    { input: "", output: "" }
  ]);
  
  // Bundle and premium
  const [bundles, setBundles] = useState([]);
  const [selectedBundle, setSelectedBundle] = useState("");
  const [problemType, setProblemType] = useState("standalone"); // "standalone" or "bundle"
  const [isPremium, setIsPremium] = useState(false);
  const [points, setPoints] = useState(10);
  const [estimatedTime, setEstimatedTime] = useState(15);
  
  // UI state
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);

  const difficulties = ["EASY", "MEDIUM", "HARD"];
  const categories = ["ALGORITHMS", "DATA_STRUCTURES", "SYSTEM_DESIGN", "DATABASE", "WEB_DEVELOPMENT"];

  useEffect(() => {
    fetchBundles();
  }, []);

  const fetchBundles = async () => {
    try {
      const response = await fetch("/api/bundles");
      if (response.ok) {
        const data = await response.json();
        setBundles(data);
      }
    } catch (error) {
      console.error("Error fetching bundles:", error);
    }
  };

  // Public test case handlers
  const addPublicTestCase = () => {
    setPublicTestCases([...publicTestCases, { input: "", output: "", explanation: "" }]);
  };

  const removePublicTestCase = (index) => {
    if (publicTestCases.length > 1) {
      setPublicTestCases(publicTestCases.filter((_, i) => i !== index));
    }
  };

  const updatePublicTestCase = (index, field, value) => {
    const updated = [...publicTestCases];
    updated[index][field] = value;
    setPublicTestCases(updated);
  };

  // Hidden test case handlers
  const addHiddenTestCase = () => {
    setHiddenTestCases([...hiddenTestCases, { input: "", output: "" }]);
  };

  const removeHiddenTestCase = (index) => {
    if (hiddenTestCases.length > 1) {
      setHiddenTestCases(hiddenTestCases.filter((_, i) => i !== index));
    }
  };

  const updateHiddenTestCase = (index, field, value) => {
    const updated = [...hiddenTestCases];
    updated[index][field] = value;
    setHiddenTestCases(updated);
  };

  // Example handlers
  const addExample = () => {
    setExamples([...examples, { input: "", output: "", explanation: "" }]);
  };

  const removeExample = (index) => {
    if (examples.length > 1) {
      setExamples(examples.filter((_, i) => i !== index));
    }
  };

  const updateExample = (index, field, value) => {
    const updated = [...examples];
    updated[index][field] = value;
    setExamples(updated);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess("");
    setLoading(true);

    // Validation
    if (!title || !description || !difficulty) {
      setError("Please fill in all required fields (Title, Description, Difficulty).");
      setLoading(false);
      return;
    }

    if (!inputFormat || !outputFormat || !constraints) {
      setError("Please fill in all required fields (Input Format, Output Format, Constraints).");
      setLoading(false);
      return;
    }

    // Validate examples
    for (let i = 0; i < examples.length; i++) {
      if (!examples[i].input || !examples[i].output || !examples[i].explanation) {
        setError(`Example ${i + 1} must have input, output, and explanation.`);
        setLoading(false);
        return;
      }
    }

    if (problemType === "bundle" && !selectedBundle) {
      setError("Please select a bundle for bundle problems.");
      setLoading(false);
      return;
    }

    // Validate test cases
    for (let i = 0; i < publicTestCases.length; i++) {
      if (!publicTestCases[i].input || !publicTestCases[i].output) {
        setError(`Public test case #${i + 1} must have both input and output.`);
        setLoading(false);
        return;
      }
    }

    for (let i = 0; i < hiddenTestCases.length; i++) {
      if (!hiddenTestCases[i].input || !hiddenTestCases[i].output) {
        setError(`Hidden test case #${i + 1} must have both input and output.`);
        setLoading(false);
        return;
      }
    }

    try {
      const token = localStorage.getItem('jwtToken');
      
      const problemData = {
        title,
        statement: description,
        difficulty,
        category,
        tags: tags.split(",").map(t => t.trim()).filter(Boolean),
        executionMode: "STDIN_STDOUT",
        
        // Stdin/stdout fields
        publicTestCases: publicTestCases.map(tc => ({
          input: tc.input,
          output: tc.output,
          explanation: tc.explanation || null
        })),
        hiddenTestCases: hiddenTestCases.map(tc => ({
          input: tc.input,
          output: tc.output
        })),
        inputFormat,
        outputFormat,
        constraints,
        examples: examples.map(ex => ({
          input: ex.input,
          output: ex.output,
          explanation: ex.explanation
        })),
        
        // Bundle and premium
        bundleId: problemType === "bundle" ? selectedBundle : null,
        isPremium: problemType === "bundle" ? bundles.find(b => b.id === selectedBundle)?.isPremium || false : isPremium,
        points,
        estimatedTime,
      };

      const response = await fetch("/api/problems", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${token}`
        },
        body: JSON.stringify(problemData)
      });

      if (response.ok) {
        const createdProblem = await response.json();
        setSuccess("Problem created successfully!");
        
        // If problem was added to a bundle, update the bundle's problemIds
        if (problemType === "bundle" && selectedBundle && createdProblem.id) {
          try {
            const bundleResponse = await fetch(`/api/bundles/${selectedBundle}`);
            if (bundleResponse.ok) {
              const bundle = await bundleResponse.json();
              const updatedProblemIds = [...(bundle.problemIds || []), createdProblem.id];
              
              const updateBundleResponse = await fetch(`/api/bundles/${selectedBundle}`, {
                method: "PUT",
                headers: {
                  "Content-Type": "application/json",
                  "Authorization": `Bearer ${token}`
                },
                body: JSON.stringify({
                  ...bundle,
                  problemIds: updatedProblemIds,
                  totalProblems: updatedProblemIds.length
                })
              });
              
              if (!updateBundleResponse.ok) {
                console.warn("Failed to update bundle with new problem ID");
              }
            }
          } catch (err) {
            console.error("Error updating bundle:", err);
          }
        }
        
        setTimeout(() => {
          navigate("/admin");
        }, 1500);
      } else {
        const errorText = await response.text();
        setError(errorText || "Failed to create problem");
      }
    } catch (err) {
      setError(err.message || "An error occurred while creating the problem");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-900 text-white p-8">
      <div className="max-w-5xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <h1 className="text-3xl font-bold">Add New Problem (Stdin/Stdout Mode)</h1>
          <button
            onClick={() => navigate("/admin")}
            className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors"
          >
            Cancel
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-8">
          {/* Basic Information */}
          <div className="bg-gray-800 rounded-xl p-6 border border-gray-700">
            <h2 className="text-xl font-bold mb-4">Basic Information</h2>
            
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-semibold mb-2">Title *</label>
                <input
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500"
                  placeholder="e.g., Two Sum"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-semibold mb-2">Difficulty *</label>
                <select
                  value={difficulty}
                  onChange={(e) => setDifficulty(e.target.value)}
                  className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500"
                  required
                >
                  {difficulties.map(diff => (
                    <option key={diff} value={diff}>{diff}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-semibold mb-2">Category</label>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500"
                >
                  {categories.map(cat => (
                    <option key={cat} value={cat}>{cat.replace('_', ' ')}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-semibold mb-2">Tags (comma-separated)</label>
                <input
                  type="text"
                  value={tags}
                  onChange={(e) => setTags(e.target.value)}
                  className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500"
                  placeholder="array, hash-table, two-pointers"
                />
              </div>
            </div>

            <div className="mt-4">
              <label className="block text-sm font-semibold mb-2">Description *</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500 min-h-[150px] font-mono text-sm"
                placeholder="Describe the problem here..."
                required
              />
            </div>
          </div>

          {/* Input/Output Format */}
          <div className="bg-gray-800 rounded-xl p-6 border border-gray-700">
            <h2 className="text-xl font-bold mb-4">Input/Output Format</h2>
            
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-semibold mb-2">Input Format *</label>
                <textarea
                  value={inputFormat}
                  onChange={(e) => setInputFormat(e.target.value)}
                  className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500 min-h-[80px] font-mono text-sm"
                  placeholder="e.g., First line: n (array size)&#10;Second line: n space-separated integers&#10;Third line: target value"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-semibold mb-2">Output Format *</label>
                <textarea
                  value={outputFormat}
                  onChange={(e) => setOutputFormat(e.target.value)}
                  className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500 min-h-[80px] font-mono text-sm"
                  placeholder="e.g., Two space-separated integers (indices)"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-semibold mb-2">Constraints *</label>
                <textarea
                  value={constraints}
                  onChange={(e) => setConstraints(e.target.value)}
                  className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500 min-h-[80px] font-mono text-sm"
                  placeholder="e.g., 2 <= n <= 10^4&#10;-10^9 <= nums[i] <= 10^9&#10;-10^9 <= target <= 10^9"
                  required
                />
              </div>
            </div>
          </div>

          {/* Examples */}
          <div className="bg-gray-800 rounded-xl p-6 border border-gray-700">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-bold">Examples (for display)</h2>
              <button
                type="button"
                onClick={addExample}
                className="px-4 py-2 bg-green-600 hover:bg-green-700 rounded-lg text-sm font-semibold transition-colors"
              >
                + Add Example
              </button>
            </div>

            <div className="space-y-4">
              {examples.map((example, index) => (
                <div key={index} className="bg-gray-700 rounded-lg p-4 border border-gray-600">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="font-semibold">Example #{index + 1}</h3>
                    {examples.length > 1 && (
                      <button
                        type="button"
                        onClick={() => removeExample(index)}
                        className="px-3 py-1 bg-red-600 hover:bg-red-700 rounded text-sm transition-colors"
                      >
                        Remove
                      </button>
                    )}
                  </div>
                  
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-semibold mb-2">Input *</label>
                      <textarea
                        value={example.input}
                        onChange={(e) => updateExample(index, "input", e.target.value)}
                        className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500 min-h-[100px] font-mono text-sm"
                        placeholder="4&#10;2 7 11 15&#10;9"
                        required
                      />
                    </div>

                    <div>
                      <label className="block text-sm font-semibold mb-2">Output *</label>
                      <textarea
                        value={example.output}
                        onChange={(e) => updateExample(index, "output", e.target.value)}
                        className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500 min-h-[100px] font-mono text-sm"
                        placeholder="0 1"
                        required
                      />
                    </div>
                  </div>

                  <div className="mt-3">
                    <label className="block text-sm font-semibold mb-2">Explanation *</label>
                    <textarea
                      value={example.explanation}
                      onChange={(e) => updateExample(index, "explanation", e.target.value)}
                      className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500 min-h-[80px]"
                      placeholder="Explain why this output is correct for the given input..."
                      required
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Public Test Cases */}
          <div className="bg-gray-800 rounded-xl p-6 border border-gray-700">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-bold">Public Test Cases (Shown to Users)</h2>
              <button
                type="button"
                onClick={addPublicTestCase}
                className="px-4 py-2 bg-green-600 hover:bg-green-700 rounded-lg text-sm font-semibold transition-colors"
              >
                + Add Test Case
              </button>
            </div>

            <div className="space-y-4">
              {publicTestCases.map((tc, index) => (
                <div key={index} className="bg-gray-700 rounded-lg p-4 border border-gray-600">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="font-semibold">Test Case #{index + 1}</h3>
                    {publicTestCases.length > 1 && (
                      <button
                        type="button"
                        onClick={() => removePublicTestCase(index)}
                        className="px-3 py-1 bg-red-600 hover:bg-red-700 rounded text-sm transition-colors"
                      >
                        Remove
                      </button>
                    )}
                  </div>
                  
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm mb-2">Input (stdin) *</label>
                      <textarea
                        value={tc.input}
                        onChange={(e) => updatePublicTestCase(index, "input", e.target.value)}
                        className="w-full p-2 bg-gray-800 text-white rounded border border-gray-600 focus:outline-none focus:border-blue-500 min-h-[100px] font-mono text-sm"
                        placeholder="4&#10;2 7 11 15&#10;9"
                        required
                      />
                    </div>

                    <div>
                      <label className="block text-sm mb-2">Expected Output (stdout) *</label>
                      <textarea
                        value={tc.output}
                        onChange={(e) => updatePublicTestCase(index, "output", e.target.value)}
                        className="w-full p-2 bg-gray-800 text-white rounded border border-gray-600 focus:outline-none focus:border-blue-500 min-h-[100px] font-mono text-sm"
                        placeholder="0 1"
                        required
                      />
                    </div>
                  </div>

                  <div className="mt-3">
                    <label className="block text-sm mb-2">Explanation (optional)</label>
                    <input
                      type="text"
                      value={tc.explanation}
                      onChange={(e) => updatePublicTestCase(index, "explanation", e.target.value)}
                      className="w-full p-2 bg-gray-800 text-white rounded border border-gray-600 focus:outline-none focus:border-blue-500"
                      placeholder="nums[0] + nums[1] = 2 + 7 = 9"
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Hidden Test Cases */}
          <div className="bg-gray-800 rounded-xl p-6 border border-gray-700">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-bold">Hidden Test Cases (Not Shown to Users)</h2>
              <button
                type="button"
                onClick={addHiddenTestCase}
                className="px-4 py-2 bg-green-600 hover:bg-green-700 rounded-lg text-sm font-semibold transition-colors"
              >
                + Add Test Case
              </button>
            </div>

            <div className="space-y-4">
              {hiddenTestCases.map((tc, index) => (
                <div key={index} className="bg-gray-700 rounded-lg p-4 border border-gray-600">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="font-semibold">Hidden Test Case #{index + 1}</h3>
                    {hiddenTestCases.length > 1 && (
                      <button
                        type="button"
                        onClick={() => removeHiddenTestCase(index)}
                        className="px-3 py-1 bg-red-600 hover:bg-red-700 rounded text-sm transition-colors"
                      >
                        Remove
                      </button>
                    )}
                  </div>
                  
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm mb-2">Input (stdin) *</label>
                      <textarea
                        value={tc.input}
                        onChange={(e) => updateHiddenTestCase(index, "input", e.target.value)}
                        className="w-full p-2 bg-gray-800 text-white rounded border border-gray-600 focus:outline-none focus:border-blue-500 min-h-[100px] font-mono text-sm"
                        placeholder="2&#10;3 3&#10;6"
                        required
                      />
                    </div>

                    <div>
                      <label className="block text-sm mb-2">Expected Output (stdout) *</label>
                      <textarea
                        value={tc.output}
                        onChange={(e) => updateHiddenTestCase(index, "output", e.target.value)}
                        className="w-full p-2 bg-gray-800 text-white rounded border border-gray-600 focus:outline-none focus:border-blue-500 min-h-[100px] font-mono text-sm"
                        placeholder="0 1"
                        required
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Bundle and Premium */}
          <div className="bg-gray-800 rounded-xl p-6 border border-gray-700">
            <h2 className="text-xl font-bold mb-4">Bundle & Settings</h2>
            
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-semibold mb-2">Problem Type</label>
                <select
                  value={problemType}
                  onChange={(e) => setProblemType(e.target.value)}
                  className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500"
                >
                  <option value="standalone">Standalone Problem</option>
                  <option value="bundle">Bundle Problem</option>
                </select>
              </div>

              {problemType === "bundle" && (
                <div>
                  <label className="block text-sm font-semibold mb-2">Select Bundle *</label>
                  <select
                    value={selectedBundle}
                    onChange={(e) => setSelectedBundle(e.target.value)}
                    className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500"
                    required={problemType === "bundle"}
                  >
                    <option value="">Choose a bundle...</option>
                    {bundles.map(bundle => (
                      <option key={bundle.id} value={bundle.id}>
                        {bundle.name} ({bundle.difficulty})
                      </option>
                    ))}
                  </select>
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {problemType === "standalone" && (
                  <div className="flex items-center">
                    <input
                      type="checkbox"
                      checked={isPremium}
                      onChange={(e) => setIsPremium(e.target.checked)}
                      className="mr-2 w-4 h-4"
                    />
                    <label className="text-sm">Premium Problem</label>
                  </div>
                )}

                <div>
                  <label className="block text-sm font-semibold mb-2">Points</label>
                  <input
                    type="number"
                    value={points}
                    onChange={(e) => setPoints(Number(e.target.value))}
                    className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500"
                    min="1"
                  />
                </div>

                <div>
                  <label className="block text-sm font-semibold mb-2">Estimated Time (min)</label>
                  <input
                    type="number"
                    value={estimatedTime}
                    onChange={(e) => setEstimatedTime(Number(e.target.value))}
                    className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500"
                    min="1"
                  />
                </div>
              </div>
            </div>
          </div>

          {/* Messages */}
          {error && (
            <div className="bg-red-900/50 border border-red-500 text-red-200 px-4 py-3 rounded-lg">
              {error}
            </div>
          )}

          {success && (
            <div className="bg-green-900/50 border border-green-500 text-green-200 px-4 py-3 rounded-lg">
              {success}
            </div>
          )}

          {/* Submit Button */}
          <div className="flex gap-4">
            <button
              type="submit"
              disabled={loading}
              className="flex-1 py-3 px-6 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-600 text-white font-bold rounded-lg transition-colors"
            >
              {loading ? "Creating Problem..." : "Create Problem"}
            </button>
            
            <button
              type="button"
              onClick={() => navigate("/admin")}
              className="px-6 py-3 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default AddProblemNew;

