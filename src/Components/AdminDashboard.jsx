import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";

const AdminDashboard = ({ problems }) => {
  const [currentPage, setCurrentPage] = useState(1);
  const [problemList, setProblemList] = useState(problems);
  const [bundles, setBundles] = useState([]);
  const [activeTab, setActiveTab] = useState("problems");
  const [bundlePage, setBundlePage] = useState(1);
  const problemsPerPage = 10;
  const bundlesPerPage = 10;
  const totalPages = Math.ceil(problemList.length / problemsPerPage);
  const totalBundlePages = Math.ceil(bundles.length / bundlesPerPage);
  const paginatedProblems = problemList.slice(
    (currentPage - 1) * problemsPerPage,
    currentPage * problemsPerPage
  );
  const paginatedBundles = bundles.slice(
    (bundlePage - 1) * bundlesPerPage,
    bundlePage * bundlesPerPage
  );

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

  // Delete problem handler
  const handleDeleteProblem = async (id) => {
    if (!window.confirm("Are you sure you want to delete this problem?")) return;
    try {
      const res = await fetch(`/api/problems/${id}`, { method: "DELETE" });
      if (!res.ok) throw new Error("Failed to delete problem");
      setProblemList(problemList.filter((p) => p.id !== id));
    } catch (err) {
      alert("Error deleting problem.");
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

      const res = await fetch(`http://localhost:8080/api/bundles/${id}`, { 
        method: "DELETE",
        headers
      });
      if (!res.ok) throw new Error("Failed to delete bundle");
      setBundles(bundles.filter((b) => b.id !== id));
    } catch (err) {
      alert("Error deleting bundle.");
    }
  };

  return (
    <div className="min-h-screen bg-gray-900 text-white p-8">
      <div className="max-w-5xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <h1 className="text-3xl font-bold">🛠️ Admin Dashboard</h1>
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
            Problems ({problemList.length})
          </button>
          <button
            onClick={() => setActiveTab("bundles")}
            className={`px-6 py-3 font-semibold ${
              activeTab === "bundles"
                ? "text-blue-400 border-b-2 border-blue-400"
                : "text-gray-400 hover:text-white"
            }`}
          >
            Bundles ({bundles.length})
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
                          <span className={`px-2 py-1 rounded text-xs ${
                            bundle.isActive ? 'bg-green-500' : 'bg-red-500'
                          }`}>
                            {bundle.isActive ? 'Active' : 'Inactive'}
                          </span>
                        </td>
                        <td className="py-2 px-3">
                          <Link
                            to={`/admin/edit-bundle/${bundle.id}`}
                            className="text-blue-400 hover:underline mr-4"
                          >
                            Edit
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
