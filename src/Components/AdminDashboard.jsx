import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";

const AdminDashboard = ({ problems: propsProblems }) => {
  const [currentPage, setCurrentPage] = useState(1);
  const [problemList, setProblemList] = useState([]);
  const [bundles, setBundles] = useState([]);
  const [activeTab, setActiveTab] = useState("problems");
  const [bundlePage, setBundlePage] = useState(1);
  const [loading, setLoading] = useState(true);
  const problemsPerPage = 10;
  const bundlesPerPage = 10;
  
  // Ensure problemList and bundles are always arrays
  const safeProblemList = Array.isArray(problemList) ? problemList : [];
  const safeBundles = Array.isArray(bundles) ? bundles : [];
  
  const totalPages = Math.ceil(safeProblemList.length / problemsPerPage);
  const totalBundlePages = Math.ceil(safeBundles.length / bundlesPerPage);
  const paginatedProblems = safeProblemList.slice(
    (currentPage - 1) * problemsPerPage,
    currentPage * problemsPerPage
  );
  const paginatedBundles = safeBundles.slice(
    (bundlePage - 1) * bundlesPerPage,
    bundlePage * bundlesPerPage
  );

  useEffect(() => {
    fetchProblems();
    fetchBundles();
    
    // Refresh data periodically (every 30 seconds)
    const interval = setInterval(() => {
      fetchProblems();
      fetchBundles();
    }, 30000);
    
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    // Update problem list when props change - ensure it's an array
    if (propsProblems && Array.isArray(propsProblems)) {
      if (propsProblems.length > 0) {
        setProblemList(propsProblems);
        setLoading(false);
      }
    }
  }, [propsProblems]);

  const fetchProblems = async () => {
    try {
      setLoading(true);
      const response = await fetch("/api/problems");
      if (response.ok) {
        const data = await response.json();
        // Handle both paginated and non-paginated responses
        let problemsArray = [];
        if (Array.isArray(data)) {
          problemsArray = data;
        } else if (data.problems && Array.isArray(data.problems)) {
          problemsArray = data.problems;
        }
        setProblemList(problemsArray);
      } else {
        console.error("Failed to fetch problems:", response.status);
        setProblemList([]);
      }
    } catch (error) {
      console.error("Error fetching problems:", error);
      setProblemList([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchBundles = async () => {
    try {
      const response = await fetch("/api/bundles");
      if (response.ok) {
        const data = await response.json();
        // Ensure data is an array
        const bundlesArray = Array.isArray(data) ? data : [];
        setBundles(bundlesArray);
      } else {
        console.error("Failed to fetch bundles:", response.status);
        setBundles([]);
      }
    } catch (error) {
      console.error("Error fetching bundles:", error);
      setBundles([]);
    }
  };

  // Delete problem handler
  const handleDeleteProblem = async (id) => {
    if (!window.confirm("Are you sure you want to delete this problem?")) return;
    try {
      const res = await fetch(`/api/problems/${id}`, { method: "DELETE" });
      if (!res.ok) throw new Error("Failed to delete problem");
      // Refresh the list after deletion
      fetchProblems();
    } catch (err) {
      alert("Error deleting problem.");
    }
  };

  // Toggle bundle status handler
  const handleToggleBundleStatus = async (id, currentStatus) => {
    try {
      const token = localStorage.getItem('jwtToken');
      const headers = {
        "Content-Type": "application/json"
      };
      
      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }

      // Fetch current bundle data
      const bundleResponse = await fetch(`/api/bundles/${id}`);
      if (!bundleResponse.ok) {
        alert("Failed to fetch bundle data");
        return;
      }
      
      const bundle = await bundleResponse.json();
      
      // Toggle status
      const updatedBundle = {
        ...bundle,
        isActive: !currentStatus
      };

      const res = await fetch(`/api/bundles/${id}`, {
        method: "PUT",
        headers,
        body: JSON.stringify(updatedBundle)
      });
      
      if (!res.ok) throw new Error("Failed to update bundle status");
      // Refresh the list after update
      fetchBundles();
    } catch (err) {
      alert("Error updating bundle status.");
    }
  };

  // Delete bundle handler
  const handleDeleteBundle = async (id) => {
    if (!window.confirm("Are you sure you want to delete this bundle?")) return;
    try {
      // Get JWT token for authentication
      const token = localStorage.getItem('jwtToken');
      const headers = {};
      
      // Add Authorization header if token exists
      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }

      const res = await fetch(`/api/bundles/${id}`, { 
        method: "DELETE",
        headers
      });
      if (!res.ok) throw new Error("Failed to delete bundle");
      // Refresh the list after deletion
      fetchBundles();
    } catch (err) {
      alert("Error deleting bundle.");
    }
  };

  console.log("[AdminDashboard] Rendering with:", { loading, problemList: safeProblemList.length, bundles: safeBundles.length });

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 text-white flex items-center justify-center">
        <div className="text-xl">Loading admin dashboard...</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-900 text-white p-8">
      <div className="max-w-5xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <h1 className="text-3xl font-bold">Admin Dashboard</h1>
          <div className="flex gap-4">
            {activeTab === "problems" && (
              <Link
                to="/admin/add-problem"
                className="bg-green-600 hover:bg-green-700 text-white font-semibold py-2 px-6 rounded-lg transition-all"
              >
                + Add New Problem
              </Link>
            )}
            {activeTab === "bundles" && (
              <Link
                to="/admin/add-bundle"
                className="bg-green-600 hover:bg-green-700 text-white font-semibold py-2 px-6 rounded-lg transition-all"
              >
                + Add New Bundle
              </Link>
            )}
          </div>
        </div>

        {/* Tabs */}
        <div className="flex mb-6 border-b border-gray-700">
          <button
            onClick={() => setActiveTab("problems")}
            className={`px-6 py-3 font-semibold ${
              activeTab === "problems"
                ? "text-blue-400 border-b-2 border-blue-400"
                : "text-gray-400 hover:text-white"
            }`}
          >
            Problems ({safeProblemList.length})
          </button>
          <button
            onClick={() => setActiveTab("bundles")}
            className={`px-6 py-3 font-semibold ${
              activeTab === "bundles"
                ? "text-blue-400 border-b-2 border-blue-400"
                : "text-gray-400 hover:text-white"
            }`}
          >
            Bundles ({safeBundles.length})
          </button>
        </div>

        {activeTab === "problems" && (
          <>
            <h2 className="text-xl font-semibold mb-4">All Problems</h2>
        <div className="bg-gray-800 rounded-lg shadow p-4">
          {paginatedProblems.length === 0 ? (
            <div className="text-gray-400">No problems found.</div>
          ) : (
            <table className="w-full text-left">
              <thead>
                <tr>
                  <th className="py-2 px-3">Title</th>
                  <th className="py-2 px-3">Difficulty</th>
                  <th className="py-2 px-3">Tags</th>
                  <th className="py-2 px-3">Actions</th>
                </tr>
              </thead>
              <tbody>
                {paginatedProblems.map((problem) => (
                  <tr key={problem.id} className="border-t border-gray-700">
                    <td className="py-2 px-3">{problem.title}</td>
                    <td className="py-2 px-3">{problem.difficulty || "N/A"}</td>
                    <td className="py-2 px-3">
                      {(problem.tags || []).join(", ")}
                    </td>
                    <td className="py-2 px-3">
                      <Link
                        to={`/admin/edit-problem/${problem.id}`}
                        className="text-blue-400 hover:underline mr-4"
                      >
                        Edit
                      </Link>
                      <button
                        className="text-red-400 hover:underline"
                        onClick={() => handleDeleteProblem(problem.id)}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        {/* Problems Pagination Controls */}
        {totalPages > 1 && (
          <div className="flex justify-center mt-6 space-x-2">
            <button
              onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              disabled={currentPage === 1}
              className="px-3 py-1 rounded bg-gray-700 text-white disabled:opacity-50"
            >
              Prev
            </button>
            {Array.from({ length: totalPages }, (_, i) => (
              <button
                key={i + 1}
                onClick={() => setCurrentPage(i + 1)}
                className={`px-3 py-1 rounded ${currentPage === i + 1 ? 'bg-blue-500' : 'bg-gray-700'} text-white`}
              >
                {i + 1}
              </button>
            ))}
            <button
              onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              disabled={currentPage === totalPages}
              className="px-3 py-1 rounded bg-gray-700 text-white disabled:opacity-50"
            >
              Next
            </button>
          </div>
        )}
          </>
        )}

        {activeTab === "bundles" && (
          <>
            <h2 className="text-xl font-semibold mb-4">All Bundles</h2>
            <div className="bg-gray-800 rounded-lg shadow p-4">
              {paginatedBundles.length === 0 ? (
                <div className="text-gray-400">No bundles found.</div>
              ) : (
                <table className="w-full text-left">
                  <thead>
                    <tr>
                      <th className="py-2 px-3">Name</th>
                      <th className="py-2 px-3">Difficulty</th>
                      <th className="py-2 px-3">Category</th>
                      <th className="py-2 px-3">Problems</th>
                      <th className="py-2 px-3">Premium</th>
                      <th className="py-2 px-3">Status</th>
                      <th className="py-2 px-3">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {paginatedBundles.map((bundle) => (
                      <tr key={bundle.id} className="border-t border-gray-700">
                        <td className="py-2 px-3">{bundle.name}</td>
                        <td className="py-2 px-3">
                          <span className={`px-2 py-1 rounded text-xs ${
                            bundle.difficulty === 'BASIC' ? 'bg-green-500' :
                            bundle.difficulty === 'INTERMEDIATE' ? 'bg-yellow-500' :
                            bundle.difficulty === 'ADVANCED' ? 'bg-orange-500' :
                            bundle.difficulty === 'SDE' ? 'bg-red-500' :
                            'bg-purple-500'
                          }`}>
                            {bundle.difficulty}
                          </span>
                        </td>
                        <td className="py-2 px-3">{bundle.category?.replace('_', ' ')}</td>
                        <td className="py-2 px-3">{bundle.totalProblems || 0}</td>
                        <td className="py-2 px-3">
                          {bundle.isPremium ? (
                            <span className="text-yellow-400 font-semibold">${bundle.price}</span>
                          ) : (
                            <span className="text-green-400">Free</span>
                          )}
                        </td>
                        <td className="py-2 px-3">
                          <button
                            onClick={() => handleToggleBundleStatus(bundle.id, bundle.isActive !== false)}
                            className={`px-2 py-1 rounded text-xs font-semibold transition-colors ${
                              bundle.isActive !== false 
                                ? 'bg-green-500 hover:bg-green-600 text-white' 
                                : 'bg-red-500 hover:bg-red-600 text-white'
                            }`}
                            title={bundle.isActive !== false ? 'Click to deactivate' : 'Click to activate'}
                          >
                            {bundle.isActive !== false ? 'Active' : 'Inactive'}
                          </button>
                        </td>
                        <td className="py-2 px-3">
                          <Link
                            to={`/admin/edit-bundle/${bundle.id}`}
                            className="text-blue-400 hover:underline mr-4"
                          >
                            Edit
                          </Link>
                          <Link
                            to={`/admin/manage-bundle/${bundle.id}`}
                            className="text-purple-400 hover:underline mr-4"
                          >
                            Manage
                          </Link>
                          <button
                            className="text-red-400 hover:underline"
                            onClick={() => handleDeleteBundle(bundle.id)}
                          >
                            Delete
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
            {/* Bundles Pagination Controls */}
            {totalBundlePages > 1 && (
              <div className="flex justify-center mt-6 space-x-2">
                <button
                  onClick={() => setBundlePage((p) => Math.max(1, p - 1))}
                  disabled={bundlePage === 1}
                  className="px-3 py-1 rounded bg-gray-700 text-white disabled:opacity-50"
                >
                  Prev
                </button>
                {Array.from({ length: totalBundlePages }, (_, i) => (
                  <button
                    key={i + 1}
                    onClick={() => setBundlePage(i + 1)}
                    className={`px-3 py-1 rounded ${bundlePage === i + 1 ? 'bg-blue-500' : 'bg-gray-700'} text-white`}
                  >
                    {i + 1}
                  </button>
                ))}
                <button
                  onClick={() => setBundlePage((p) => Math.min(totalBundlePages, p + 1))}
                  disabled={bundlePage === totalBundlePages}
                  className="px-3 py-1 rounded bg-gray-700 text-white disabled:opacity-50"
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}
      </div>
      {activeTab === "problems" && (
        <Link
          to="/admin/add-problem"
          className="fixed bottom-6 right-6 h-14 w-14 rounded-full bg-green-600 hover:bg-green-700 text-white flex items-center justify-center text-3xl shadow-lg z-50"
          aria-label="Add Problem"
          title="Add Problem"
        >
          +
        </Link>
      )}
    </div>
  );
};

export default AdminDashboard;
