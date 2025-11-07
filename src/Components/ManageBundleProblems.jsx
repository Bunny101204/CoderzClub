import React, { useState, useEffect } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";

const ManageBundleProblems = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [bundle, setBundle] = useState(null);
  const [bundleProblems, setBundleProblems] = useState([]);
  const [availableProblems, setAvailableProblems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [selectedProblem, setSelectedProblem] = useState("");

  useEffect(() => {
    fetchBundleData();
    fetchAllProblems();
  }, [id]);

  const fetchBundleData = async () => {
    try {
      const response = await fetch(`http://localhost:8080/api/bundles/${id}`);
      if (response.ok) {
        const data = await response.json();
        setBundle(data);
        
        // Fetch full problem details for problems in bundle
        if (data.problemIds && data.problemIds.length > 0) {
          await fetchBundleProblems(data.problemIds);
        } else {
          setBundleProblems([]);
        }
      }
    } catch (error) {
      console.error("Error fetching bundle:", error);
      setError("Failed to load bundle data");
    } finally {
      setLoading(false);
    }
  };

  const fetchBundleProblems = async (problemIds) => {
    try {
      const response = await fetch("http://localhost:8080/api/problems");
      if (response.ok) {
        const allProblems = await response.json();
        const filtered = allProblems.filter(p => problemIds.includes(p.id));
        setBundleProblems(filtered);
      }
    } catch (error) {
      console.error("Error fetching bundle problems:", error);
    }
  };

  const fetchAllProblems = async () => {
    try {
      const response = await fetch("http://localhost:8080/api/problems");
      if (response.ok) {
        const data = await response.json();
        setAvailableProblems(data);
      }
    } catch (error) {
      console.error("Error fetching problems:", error);
    }
  };

  const addProblemToBundle = async () => {
    if (!selectedProblem) {
      setError("Please select a problem to add");
      return;
    }

    // Check if problem already exists
    if (bundle.problemIds && bundle.problemIds.includes(selectedProblem)) {
      setError("Problem already exists in this bundle");
      return;
    }

    try {
      const token = localStorage.getItem('jwtToken');
      const updatedProblemIds = [...(bundle.problemIds || []), selectedProblem];
      
      const response = await fetch(`http://localhost:8080/api/bundles/${id}`, {
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

      if (response.ok) {
        setSuccess("Problem added successfully!");
        setSelectedProblem("");
        fetchBundleData();
        setTimeout(() => setSuccess(""), 3000);
      } else {
        setError("Failed to add problem");
      }
    } catch (error) {
      console.error("Error adding problem:", error);
      setError("Error adding problem to bundle");
    }
  };

  const removeProblemFromBundle = async (problemId) => {
    if (!window.confirm("Are you sure you want to remove this problem from the bundle?")) {
      return;
    }

    try {
      const token = localStorage.getItem('jwtToken');
      const updatedProblemIds = bundle.problemIds.filter(id => id !== problemId);
      
      const response = await fetch(`http://localhost:8080/api/bundles/${id}`, {
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

      if (response.ok) {
        setSuccess("Problem removed successfully!");
        fetchBundleData();
        setTimeout(() => setSuccess(""), 3000);
      } else {
        setError("Failed to remove problem");
      }
    } catch (error) {
      console.error("Error removing problem:", error);
      setError("Error removing problem from bundle");
    }
  };

  const reorderProblem = async (problemId, direction) => {
    const currentIndex = bundle.problemIds.indexOf(problemId);
    if (
      (direction === "up" && currentIndex === 0) ||
      (direction === "down" && currentIndex === bundle.problemIds.length - 1)
    ) {
      return;
    }

    const newIndex = direction === "up" ? currentIndex - 1 : currentIndex + 1;
    const newProblemIds = [...bundle.problemIds];
    [newProblemIds[currentIndex], newProblemIds[newIndex]] = [newProblemIds[newIndex], newProblemIds[currentIndex]];

    try {
      const token = localStorage.getItem('jwtToken');
      const response = await fetch(`http://localhost:8080/api/bundles/${id}`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${token}`
        },
        body: JSON.stringify({
          ...bundle,
          problemIds: newProblemIds
        })
      });

      if (response.ok) {
        fetchBundleData();
      }
    } catch (error) {
      console.error("Error reordering problems:", error);
    }
  };

  const getDifficultyColor = (difficulty) => {
    const colors = {
      EASY: "text-green-400",
      MEDIUM: "text-yellow-400",
      HARD: "text-red-400"
    };
    return colors[difficulty] || "text-gray-400";
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="text-white text-xl">Loading...</div>
      </div>
    );
  }

  if (!bundle) {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="text-white text-xl">Bundle not found</div>
      </div>
    );
  }

  // Filter out problems that are already in the bundle
  const problemsToAdd = availableProblems.filter(
    p => !bundle.problemIds || !bundle.problemIds.includes(p.id)
  );

  return (
    <div className="min-h-screen bg-gray-900 text-white p-8">
      <div className="max-w-6xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h1 className="text-3xl font-bold mb-2">Manage Bundle Problems</h1>
              <h2 className="text-xl text-gray-400">{bundle.name}</h2>
            </div>
            <div className="flex space-x-3">
              <Link
                to={`/admin/edit-bundle/${id}`}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors"
              >
                Edit Bundle Info
              </Link>
              <Link
                to="/bundles"
                className="px-4 py-2 bg-gray-600 hover:bg-gray-700 text-white rounded-lg transition-colors"
              >
                Back to Bundles
              </Link>
            </div>
          </div>
          <p className="text-gray-400">{bundle.description}</p>
        </div>

        {/* Messages */}
        {error && (
          <div className="bg-red-900/50 border border-red-500 text-red-200 px-4 py-3 rounded mb-6">
            {error}
          </div>
        )}
        {success && (
          <div className="bg-green-900/50 border border-green-500 text-green-200 px-4 py-3 rounded mb-6">
            {success}
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Add Problem Section */}
          <div className="bg-gray-800 rounded-xl p-6 border border-gray-700">
            <h3 className="text-xl font-bold mb-4">Add Problem to Bundle</h3>
            <div className="space-y-4">
              <select
                value={selectedProblem}
                onChange={(e) => {
                  setSelectedProblem(e.target.value);
                  setError("");
                }}
                className="w-full p-3 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500"
              >
                <option value="">Select a problem...</option>
                {problemsToAdd.map(problem => (
                  <option key={problem.id} value={problem.id}>
                    {problem.title} ({problem.difficulty})
                  </option>
                ))}
              </select>
              <button
                onClick={addProblemToBundle}
                className="w-full px-4 py-3 bg-green-600 hover:bg-green-700 text-white rounded-lg font-semibold transition-colors"
                disabled={!selectedProblem}
              >
                + Add Problem
              </button>
              <p className="text-sm text-gray-400">
                {problemsToAdd.length} problem(s) available to add
              </p>
            </div>
          </div>

          {/* Bundle Stats */}
          <div className="bg-gray-800 rounded-xl p-6 border border-gray-700">
            <h3 className="text-xl font-bold mb-4">Bundle Statistics</h3>
            <div className="grid grid-cols-2 gap-4">
              <div className="bg-gray-700 rounded-lg p-4">
                <div className="text-3xl font-bold text-blue-400">{bundleProblems.length}</div>
                <div className="text-sm text-gray-400">Total Problems</div>
              </div>
              <div className="bg-gray-700 rounded-lg p-4">
                <div className="text-3xl font-bold text-green-400">
                  {bundle.totalPoints || 0}
                </div>
                <div className="text-sm text-gray-400">Total Points</div>
              </div>
              <div className="bg-gray-700 rounded-lg p-4">
                <div className="text-3xl font-bold text-purple-400">
                  {bundle.estimatedTotalTime || 0}
                </div>
                <div className="text-sm text-gray-400">Total Minutes</div>
              </div>
              <div className="bg-gray-700 rounded-lg p-4">
                <div className="text-3xl font-bold text-yellow-400">
                  {bundle.difficulty}
                </div>
                <div className="text-sm text-gray-400">Difficulty</div>
              </div>
            </div>
          </div>
        </div>

        {/* Problems in Bundle */}
        <div className="mt-8 bg-gray-800 rounded-xl p-6 border border-gray-700">
          <h3 className="text-xl font-bold mb-6">Problems in Bundle ({bundleProblems.length})</h3>
          
          {bundleProblems.length === 0 ? (
            <div className="text-center py-12">
              <div className="text-6xl mb-4">📝</div>
              <p className="text-gray-400 text-lg">No problems in this bundle yet</p>
              <p className="text-gray-500 text-sm mt-2">Add problems using the form above</p>
            </div>
          ) : (
            <div className="space-y-4">
              {bundleProblems.map((problem, index) => (
                <div
                  key={problem.id}
                  className="bg-gray-700 rounded-lg p-4 flex items-center justify-between hover:bg-gray-600 transition-colors"
                >
                  <div className="flex items-center space-x-4 flex-1">
                    <div className="flex flex-col space-y-1">
                      <button
                        onClick={() => reorderProblem(problem.id, "up")}
                        disabled={index === 0}
                        className={`text-xs ${index === 0 ? 'text-gray-600 cursor-not-allowed' : 'text-blue-400 hover:text-blue-300'}`}
                      >
                        ▲
                      </button>
                      <button
                        onClick={() => reorderProblem(problem.id, "down")}
                        disabled={index === bundleProblems.length - 1}
                        className={`text-xs ${index === bundleProblems.length - 1 ? 'text-gray-600 cursor-not-allowed' : 'text-blue-400 hover:text-blue-300'}`}
                      >
                        ▼
                      </button>
                    </div>
                    <div className="text-2xl font-bold text-gray-500">
                      #{index + 1}
                    </div>
                    <div className="flex-1">
                      <h4 className="text-lg font-semibold text-white">{problem.title}</h4>
                      <div className="flex items-center space-x-3 mt-1">
                        <span className={`text-sm font-semibold ${getDifficultyColor(problem.difficulty)}`}>
                          {problem.difficulty}
                        </span>
                        {problem.tags && problem.tags.length > 0 && (
                          <div className="flex space-x-2">
                            {problem.tags.slice(0, 3).map((tag, i) => (
                              <span key={i} className="text-xs bg-gray-600 px-2 py-1 rounded">
                                {tag}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center space-x-3">
                    <Link
                      to={`/problem/${problem.id}`}
                      className="px-3 py-1 bg-blue-600 hover:bg-blue-700 text-white text-sm rounded transition-colors"
                    >
                      View
                    </Link>
                    <button
                      onClick={() => removeProblemFromBundle(problem.id)}
                      className="px-3 py-1 bg-red-600 hover:bg-red-700 text-white text-sm rounded transition-colors"
                    >
                      Remove
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ManageBundleProblems;



