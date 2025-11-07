import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";

const PARAMETER_TYPES = [
  "int", "int[]", "long", "long[]", "float", "float[]", "double", "double[]", "String", "String[]", "boolean", "boolean[]", "List<Integer>", "List<String>", "ListNode", "TreeNode", "Graph", "Custom Object"
];

const AddProblem = () => {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [difficulty, setDifficulty] = useState("");
  const [tags, setTags] = useState("");
  const [publicTestCases, setPublicTestCases] = useState(""); // JSON string
  const [hiddenTestCases, setHiddenTestCases] = useState(""); // JSON string
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const [parameters, setParameters] = useState([{ name: "", type: "int" }]);
  const [returnType, setReturnType] = useState("int");
  const [functionName, setFunctionName] = useState("");
  const [bundles, setBundles] = useState([]);
  const [selectedBundle, setSelectedBundle] = useState("");
  const [problemType, setProblemType] = useState("standalone"); // "standalone" or "bundle"
  const [template, setTemplate] = useState("");

  useEffect(() => {
    fetchBundles();
  }, []);

  const fetchBundles = async () => {
    try {
      const response = await fetch("http://localhost:8080/api/bundles");
      if (response.ok) {
        const data = await response.json();
        setBundles(data);
      }
    } catch (error) {
      console.error("Error fetching bundles:", error);
    }
  };

  // Update template when bundle is selected
  useEffect(() => {
    if (selectedBundle && problemType === "bundle") {
      const bundle = bundles.find(b => b.id === selectedBundle);
      if (bundle && bundle.sharedTemplate) {
        setTemplate(bundle.sharedTemplate);
      }
    }
  }, [selectedBundle, problemType, bundles]);

  // Add a new parameter row
  const handleAddParameter = () => {
    setParameters([...parameters, { name: "", type: "int" }]);
  };
  // Remove a parameter row
  const handleRemoveParameter = (idx) => {
    setParameters(parameters.filter((_, i) => i !== idx));
  };
  // Update parameter name/type
  const handleParameterChange = (idx, field, value) => {
    setParameters(parameters.map((p, i) => i === idx ? { ...p, [field]: value } : p));
  };

  const validateTestCases = (cases, paramCount) => {
    for (let i = 0; i < cases.length; i++) {
      const tc = cases[i];
      if (!tc.input) return `Test case #${i + 1} is missing an input field.`;
      // Try to parse input as JSON if it's a string
      let input = tc.input;
      if (typeof input === "string") {
        try {
          input = JSON.parse(input);
        } catch {
          // If not JSON, try to parse as array-like string
          if (!(input.startsWith("[") && input.endsWith("]"))) {
            return `Test case #${i + 1} input should be a JSON array (e.g., [2,3])`;
          }
        }
      }
      // If input is not an array or object, error
      if (!Array.isArray(input) && typeof input !== "object") {
        return `Test case #${i + 1} input should be a JSON array/object.`;
      }
      // If input is array, check length matches paramCount
      if (Array.isArray(input) && input.length !== paramCount) {
        return `Test case #${i + 1} input should have ${paramCount} elements (one for each parameter).`;
      }
    }
    return null;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess("");
    setLoading(true);
    if (!title || !description || !difficulty) {
      setError("Please fill in all required fields.");
      setLoading(false);
      return;
    }
    
    if (problemType === "bundle" && !selectedBundle) {
      setError("Please select a bundle for bundle problems.");
      setLoading(false);
      return;
    }
    let publicCases, hiddenCases;
    try {
      publicCases = publicTestCases ? JSON.parse(publicTestCases) : [];
    } catch (err) {
      setError("Public test cases must be valid JSON array.");
      setLoading(false);
      return;
    }
    try {
      hiddenCases = hiddenTestCases ? JSON.parse(hiddenTestCases) : [];
    } catch (err) {
      setError("Hidden test cases must be valid JSON array.");
      setLoading(false);
      return;
    }
    // Validate test cases against parameters
    const paramCount = parameters.length;
    let errMsg = validateTestCases(publicCases, paramCount) || validateTestCases(hiddenCases, paramCount);
    if (errMsg) {
      setError(errMsg);
      setLoading(false);
      return;
    }
    // Backend integration
    try {
      const problem = {
        title,
        statement: description,
        difficulty,
        tags: tags.split(",").map((t) => t.trim()).filter(Boolean),
        parameters,
        returnType,
        functionName,
        testCases: publicCases,
        hiddenTestCases: hiddenCases,
        template: problemType === "bundle" ? template : "",
        bundleId: problemType === "bundle" ? selectedBundle : null,
        language: "java",
        className: "Solution",
        category: problemType === "bundle" ? bundles.find(b => b.id === selectedBundle)?.category : "ALGORITHMS",
        isPremium: problemType === "bundle" ? bundles.find(b => b.id === selectedBundle)?.isPremium : false,
        points: 10,
        estimatedTime: 30
      };
      const res = await fetch("/api/problems", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(problem),
      });
      if (!res.ok) throw new Error("Failed to add problem");
      setSuccess("Problem added!");
      setTitle("");
      setDescription("");
      setDifficulty("");
      setTags("");
      setPublicTestCases("");
      setHiddenTestCases("");
      setTimeout(() => navigate("/admin"), 1200);
    } catch (err) {
      setError("Failed to add problem.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-900 flex items-center justify-center">
      <form
        onSubmit={handleSubmit}
        className="bg-gray-800 p-8 rounded-lg shadow-lg w-full max-w-lg"
      >
        <h2 className="text-2xl font-bold mb-6 text-white text-center">Add New Problem</h2>
        
        {/* Problem Type Selection */}
        <div className="mb-6">
          <label className="block text-white mb-2">Problem Type*</label>
          <div className="flex gap-4">
            <label className="flex items-center">
              <input
                type="radio"
                name="problemType"
                value="standalone"
                checked={problemType === "standalone"}
                onChange={(e) => setProblemType(e.target.value)}
                className="mr-2"
              />
              <span className="text-white">Standalone Problem</span>
            </label>
            <label className="flex items-center">
              <input
                type="radio"
                name="problemType"
                value="bundle"
                checked={problemType === "bundle"}
                onChange={(e) => setProblemType(e.target.value)}
                className="mr-2"
              />
              <span className="text-white">Bundle Problem</span>
            </label>
          </div>
        </div>

        {/* Bundle Selection */}
        {problemType === "bundle" && (
          <div className="mb-4">
            <label className="block text-white mb-2">Select Bundle*</label>
            <select
              value={selectedBundle}
              onChange={(e) => setSelectedBundle(e.target.value)}
              className="w-full p-3 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none"
            >
              <option value="">Select a bundle</option>
              {bundles.map(bundle => (
                <option key={bundle.id} value={bundle.id}>
                  {bundle.name} ({bundle.difficulty})
                </option>
              ))}
            </select>
          </div>
        )}

        <label className="block text-white mb-2">Title*</label>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          className="w-full p-3 mb-4 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none"
        />
        <label className="block text-white mb-2">Description*</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          className="w-full p-3 mb-4 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none min-h-[100px]"
        />
        <label className="block text-white mb-2">Difficulty*</label>
        <select
          value={difficulty}
          onChange={(e) => setDifficulty(e.target.value)}
          className="w-full p-3 mb-4 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none"
        >
          <option value="">Select difficulty</option>
          <option value="Easy">Easy</option>
          <option value="Medium">Medium</option>
          <option value="Hard">Hard</option>
        </select>
        <label className="block text-white mb-2">Tags (comma separated)</label>
        <input
          type="text"
          value={tags}
          onChange={(e) => setTags(e.target.value)}
          className="w-full p-3 mb-4 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none"
        />
        <label className="block text-white mb-2">Function Name*</label>
        <input
          type="text"
          value={functionName}
          onChange={e => setFunctionName(e.target.value)}
          className="w-full p-3 mb-4 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none"
          placeholder="e.g. miracleSum"
          required
        />
        <label className="block text-white mb-2">Function Parameters</label>
        <div className="mb-4 bg-gray-700 p-3 rounded">
          {parameters.map((param, idx) => (
            <div key={idx} className="flex items-center mb-2 gap-2">
              <input
                type="text"
                value={param.name}
                onChange={e => handleParameterChange(idx, "name", e.target.value)}
                placeholder="Parameter name"
                className="p-2 rounded bg-gray-800 text-white border border-gray-600 focus:outline-none w-1/2"
                required
              />
              <select
                value={param.type}
                onChange={e => handleParameterChange(idx, "type", e.target.value)}
                className="p-2 rounded bg-gray-800 text-white border border-gray-600 focus:outline-none w-1/2"
              >
                {PARAMETER_TYPES.map(type => (
                  <option key={type} value={type}>{type}</option>
                ))}
              </select>
              {parameters.length > 1 && (
                <button type="button" onClick={() => handleRemoveParameter(idx)} className="text-red-400 ml-2">Remove</button>
              )}
            </div>
          ))}
          <button type="button" onClick={handleAddParameter} className="bg-blue-500 hover:bg-blue-600 text-white px-3 py-1 rounded text-sm mt-2">+ Add Parameter</button>
        </div>
        <label className="block text-white mb-2">Return Type</label>
        <select
          value={returnType}
          onChange={e => setReturnType(e.target.value)}
          className="w-full p-3 mb-4 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none"
        >
          {PARAMETER_TYPES.map(type => (
            <option key={type} value={type}>{type}</option>
          ))}
        </select>

        {/* Template Code - Only for bundle problems */}
        {problemType === "bundle" && (
          <div className="mb-4">
            <label className="block text-white mb-2">Template Code</label>
            <div className="text-gray-400 text-xs mb-2">
              This template is shared across all problems in the selected bundle. You can modify it if needed.
            </div>
            <textarea
              value={template}
              onChange={(e) => setTemplate(e.target.value)}
              className="w-full p-3 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none min-h-[120px] font-mono text-sm"
              placeholder="Template code will be loaded from the selected bundle..."
            />
          </div>
        )}
        <label className="block text-white mb-2">Public Test Cases (JSON array)</label>
        <div className="text-gray-400 text-xs mb-2">
          <b>Example:</b> <code>[{'{'}"input": [2,3], "output": 5{'}'}]</code> for a function with 2 parameters.<br/>
          <b>Input must be a JSON array/object matching the parameter list.</b>
        </div>
        <textarea
          value={publicTestCases}
          onChange={(e) => setPublicTestCases(e.target.value)}
          className="w-full p-3 mb-4 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none min-h-[80px]"
          placeholder='[{"input": [2,3], "output": 5}]'
        />
        <label className="block text-white mb-2 mt-4">Hidden Test Cases (JSON array)</label>
        <div className="text-gray-400 text-xs mb-2">
          <b>Example:</b> <code>[{'{'}"input": [2,3], "output": 5{'}'}]</code> for a function with 2 parameters.
        </div>
        <textarea
          value={hiddenTestCases}
          onChange={(e) => setHiddenTestCases(e.target.value)}
          className="w-full p-3 mb-4 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none min-h-[80px]"
          placeholder='[{"input": [2,3], "output": 5}]'
        />
        {error && <div className="text-red-400 mb-4 text-center">{error}</div>}
        {success && <div className="text-green-400 mb-4 text-center">{success}</div>}
        <button
          type="submit"
          className="w-full bg-green-500 hover:bg-green-600 text-white font-bold py-2 px-4 rounded"
          disabled={loading}
        >
          {loading ? "Adding..." : "Add Problem"}
        </button>
      </form>
    </div>
  );
};

export default AddProblem; 