import React, { useState, useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import {
  SEARCH_DEBOUNCE_MS,
  displayProblemId,
  internalProblemId,
  editProblemPath,
  nextPageForFilterChange
} from "../admin/adminProblems";

const AdminDashboard = () => {
  const [currentPage, setCurrentPage] = useState(1);
  const [problemList, setProblemList] = useState([]); // holds current page of problems
  const [bundles, setBundles] = useState([]);
  const [activeTab, setActiveTab] = useState("problems");
  const [bundlePage, setBundlePage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [problemsPerPage, setProblemsPerPage] = useState(10);
  const [problemSearch, setProblemSearch] = useState("");
  const [debouncedProblemSearch, setDebouncedProblemSearch] = useState("");
  const [problemTopic, setProblemTopic] = useState("");
  const [bundlesPerPage, setBundlesPerPage] = useState(10);
  const [bundleSearch, setBundleSearch] = useState("");
  const [totalProblemPages, setTotalProblemPages] = useState(0);
  const [totalProblemItems, setTotalProblemItems] = useState(0);
  const [batches, setBatches] = useState([]);
  const [batchSearch, setBatchSearch] = useState("");
  const [newBatchName, setNewBatchName] = useState("");
  const [newBatchDescription, setNewBatchDescription] = useState("");
  const [batchError, setBatchError] = useState("");
  const [creatingBatch, setCreatingBatch] = useState(false);
  const problemFetchSeq = useRef(0);
  
  // Ensure problemList and bundles are always arrays
  const safeProblemList = Array.isArray(problemList) ? problemList : [];
  const safeBundles = Array.isArray(bundles) ? bundles : [];
  
  const paginatedProblems = safeProblemList;
  const totalPages = totalProblemPages;
  const filteredBundles = safeBundles.filter((bundle) => {
    if (!bundleSearch || bundleSearch.trim() === "") return true;
    const searchLower = bundleSearch.toLowerCase().trim();
    const bundleId = String(bundle.id || "").toLowerCase();
    const name = String(bundle.name || "").toLowerCase();
    return bundleId === searchLower || bundleId.startsWith(searchLower) || name.includes(searchLower);
  });
  const totalBundlePages = Math.ceil(filteredBundles.length / bundlesPerPage);
  const paginatedBundles = filteredBundles.slice(
    (bundlePage - 1) * bundlesPerPage,
    bundlePage * bundlesPerPage
  );

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedProblemSearch(problemSearch), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [problemSearch]);

  useEffect(() => {
    fetchProblems({ silent: false });
    fetchBundles();
    
    const interval = setInterval(() => {
      fetchProblems({ silent: true });
      fetchBundles();
    }, 30000);
    
    return () => clearInterval(interval);
  }, [currentPage, problemsPerPage, debouncedProblemSearch, problemTopic]);
  // Note: AdminDashboard fetches paginated problems directly from API; props are not used.

  const fetchProblems = async ({ silent = false } = {}) => {
    const seq = ++problemFetchSeq.current;
    try {
      if (!silent) {
        setLoading(true);
      }
      const respPage = Math.max(0, currentPage - 1);
      const params = new URLSearchParams({ page: respPage.toString(), size: problemsPerPage.toString() });
      if (debouncedProblemSearch && debouncedProblemSearch.trim() !== '') {
        params.append('search', debouncedProblemSearch.trim());
      }
      if (problemTopic && problemTopic !== '') {
        params.append('tags', problemTopic);
      }
      const response = await fetch(`/api/problems?${params}`);
      if (seq !== problemFetchSeq.current) {
        return;
      }
      if (response.ok) {
        const data = await response.json();
        if (seq !== problemFetchSeq.current) {
          return;
        }
        let problemsArray = [];
        if (data.problems && Array.isArray(data.problems)) {
          problemsArray = data.problems;
          setTotalProblemPages(data.totalPages || 0);
          setTotalProblemItems(data.totalItems || (data.totalElements || problemsArray.length));
        } else if (Array.isArray(data)) {
          problemsArray = data;
          if (problemTopic && problemTopic !== '') {
            problemsArray = problemsArray.filter(p => (p.tags || []).map(String).map(t => t.toLowerCase()).includes(problemTopic.toLowerCase()));
          }
          setTotalProblemPages(Math.ceil(problemsArray.length / problemsPerPage));
          setTotalProblemItems(problemsArray.length);
        }
        setProblemList(problemsArray);
      } else if (seq === problemFetchSeq.current) {
        console.error("Failed to fetch problems:", response.status);
        setProblemList([]);
      }
    } catch (error) {
      if (seq !== problemFetchSeq.current) {
        return;
      }
      console.error("Error fetching problems:", error);
      setProblemList([]);
    } finally {
      if (seq === problemFetchSeq.current) {
        setLoading(false);
      }
    }
  };

  const fetchBundles = async () => {
    try {
      const token = localStorage.getItem("jwtToken");
      const response = await fetch("/api/bundles/admin/all", {
        headers: token ? { Authorization: `Bearer ${token}` } : {}
      });
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

  const fetchAdminBatches = async () => {
    try {
      const token = localStorage.getItem("jwtToken") || localStorage.getItem("token");
      const params = new URLSearchParams({ page: "0", size: "20" });
      if (batchSearch.trim()) params.set("search", batchSearch.trim());
      const response = await fetch(`/api/admin/batches?${params}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {}
      });
      if (response.ok) {
        const data = await response.json();
        setBatches(Array.isArray(data.batches) ? data.batches : []);
      }
    } catch (error) {
      console.error("Error fetching batches:", error);
    }
  };

  const createBatch = async (event) => {
    event.preventDefault();
    setBatchError("");
    if (!newBatchName.trim()) {
      setBatchError("name is required");
      return;
    }
    if (creatingBatch) return;
    setCreatingBatch(true);
    try {
      const token = localStorage.getItem("jwtToken") || localStorage.getItem("token");
      const response = await fetch("/api/admin/batches", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: JSON.stringify({ name: newBatchName, description: newBatchDescription })
      });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        setBatchError(data.error || "Could not create batch");
        return;
      }
      setNewBatchName("");
      setNewBatchDescription("");
      fetchAdminBatches();
    } finally {
      setCreatingBatch(false);
    }
  };

  useEffect(() => {
    if (activeTab === "batches") {
      fetchAdminBatches();
    }
  }, [activeTab, batchSearch]);

  // Delete problem handler
  const handleDeleteProblem = async (id) => {
    if (!window.confirm("Are you sure you want to delete this problem?")) return;
    try {
      const res = await fetch(`/api/problems/${id}`, { method: "DELETE" });
      if (!res.ok) throw new Error("Failed to delete problem");
      // Refresh the list after deletion
      fetchProblems({ silent: true });
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

  return (
    <div className="app-shell p-8">
      <div className="max-w-7xl mx-auto">
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
        <div className="flex mb-6 border-b border-gray-200 dark:border-gray-700">
          <button
            onClick={() => setActiveTab("problems")}
            className={`px-6 py-3 font-semibold ${
              activeTab === "problems"
                ? "text-blue-600 dark:text-blue-400 border-b-2 border-blue-600 dark:border-blue-400"
                : "app-muted hover:text-gray-900 dark:hover:text-white"
            }`}
          >
            Problems ({totalProblemItems})
          </button>
          <button
            onClick={() => setActiveTab("bundles")}
            className={`px-6 py-3 font-semibold ${
              activeTab === "bundles"
                ? "text-blue-600 dark:text-blue-400 border-b-2 border-blue-600 dark:border-blue-400"
                : "app-muted hover:text-gray-900 dark:hover:text-white"
            }`}
          >
            Bundles ({safeBundles.length})
          </button>
          <button
            onClick={() => setActiveTab("batches")}
            className={`px-6 py-3 font-semibold ${
              activeTab === "batches"
                ? "text-blue-600 dark:text-blue-400 border-b-2 border-blue-600 dark:border-blue-400"
                : "app-muted hover:text-gray-900 dark:hover:text-white"
            }`}
          >
            Batches ({batches.length})
          </button>
        </div>

        {activeTab === "problems" && (
          <div className="grid grid-cols-1 md:grid-cols-[240px_minmax(0,1fr)] gap-6 items-start">
            <aside className="w-full md:w-[240px] md:max-w-[240px] app-surface rounded-lg shadow p-4">
              <h2 className="text-lg font-semibold mb-4">Filters</h2>
              <label className="block text-sm app-muted mb-2" htmlFor="admin-problem-topic">Topic</label>
              <select
                id="admin-problem-topic"
                value={problemTopic}
                onChange={(e) => { setProblemTopic(e.target.value); setCurrentPage(nextPageForFilterChange()); }}
                className="w-full mb-4 px-3 py-2 app-input rounded"
              >
                <option value="">All Topics</option>
                <option value="arrays">Arrays</option>
                <option value="strings">Strings</option>
                <option value="dynamic-programming">Dynamic Programming</option>
                <option value="graphs">Graphs</option>
                <option value="trees">Trees</option>
                <option value="greedy">Greedy</option>
                <option value="math">Math</option>
                <option value="bit-manipulation">Bit Manipulation</option>
                <option value="two-pointers">Two Pointers</option>
                <option value="sliding-window">Sliding Window</option>
                <option value="backtracking">Backtracking</option>
                <option value="sorting">Sorting</option>
              </select>
              <label className="block text-sm app-muted mb-2" htmlFor="admin-problem-items">Items</label>
              <select
                id="admin-problem-items"
                value={problemsPerPage}
                onChange={(e) => { setProblemsPerPage(Number(e.target.value)); setCurrentPage(nextPageForFilterChange()); }}
                className="w-full px-3 py-2 app-input rounded"
              >
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
                <option value={100}>100</option>
              </select>
            </aside>
            <div className="min-w-0 w-full overflow-x-auto">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-4 gap-4">
              <div>
                <h2 className="text-xl font-semibold">All Problems</h2>
                <div className="text-sm app-muted">Showing page {currentPage} of {totalPages || 1}</div>
              </div>
              <input
                type="search"
                placeholder="Search problems by ID or title prefix..."
                value={problemSearch}
                onChange={(e) => { setProblemSearch(e.target.value); setCurrentPage(nextPageForFilterChange()); }}
                className="px-3 py-2 app-input rounded w-full sm:w-80"
              />
            </div>
        <div className="app-surface rounded-lg shadow p-4 overflow-x-auto">
          {loading && paginatedProblems.length === 0 ? (
            <div className="app-muted">Loading problems...</div>
          ) : paginatedProblems.length === 0 ? (
            <div className="app-muted">No problems found.</div>
          ) : (
            <table className="w-full text-left">
              <thead>
                <tr>
                  <th className="py-2 px-3">ID</th>
                  <th className="py-2 px-3">Title</th>
                  <th className="py-2 px-3">Difficulty</th>
                  <th className="py-2 px-3">Tags</th>
                  <th className="py-2 px-3">Actions</th>
                </tr>
              </thead>
              <tbody>
                {paginatedProblems.map((problem) => (
                  <tr key={internalProblemId(problem)} className="border-t border-gray-200 dark:border-gray-700">
                    <td className="py-2 px-3 font-mono">{displayProblemId(problem)}</td>
                    <td className="py-2 px-3">{problem.title}</td>
                    <td className="py-2 px-3">{problem.difficulty || "N/A"}</td>
                    <td className="py-2 px-3">
                      {(problem.tags || []).join(", ")}
                    </td>
                    <td className="py-2 px-3">
                      <Link
                        to={editProblemPath(problem)}
                        className="text-blue-600 dark:text-blue-400 hover:underline mr-4"
                      >
                        Edit
                      </Link>
                      <button
                        className="text-red-600 dark:text-red-400 hover:underline"
                        onClick={() => handleDeleteProblem(internalProblemId(problem))}
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
              className="px-3 py-1 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white disabled:opacity-50"
            >
              Prev
            </button>
            {Array.from({ length: totalPages }, (_, i) => (
              <button
                key={i + 1}
                onClick={() => setCurrentPage(i + 1)}
                className={`px-3 py-1 rounded ${currentPage === i + 1 ? 'bg-blue-500 text-white' : 'bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white'}`}
              >
                {i + 1}
              </button>
            ))}
            <button
              onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              disabled={currentPage === totalPages}
              className="px-3 py-1 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white disabled:opacity-50"
            >
              Next
            </button>
          </div>
        )}
            </div>
          </div>
        )}

        {activeTab === "bundles" && (
          <>
            <h2 className="text-xl font-semibold mb-4">All Bundles</h2>
            <div className="flex items-center justify-between mb-4">
              <div className="text-sm app-muted">Bundles: {safeBundles.length}</div>
              <div className="flex flex-col lg:flex-row items-center gap-3">
                <div className="flex items-center gap-3">
                  <label className="text-sm app-muted">Items:</label>
                  <select value={bundlesPerPage} onChange={(e) => { setBundlesPerPage(Number(e.target.value)); setBundlePage(1); }} className="px-3 py-1 app-input rounded">
                    <option value={10}>10</option>
                    <option value={20}>20</option>
                    <option value={50}>50</option>
                    <option value={100}>100</option>
                  </select>
                </div>
                <input
                  type="text"
                  placeholder="Search bundles by ID or name..."
                  value={bundleSearch}
                  onChange={(e) => { setBundleSearch(e.target.value); setBundlePage(1); }}
                  className="px-3 py-2 app-input rounded w-full lg:w-80"
                />
              </div>
            </div>
            <div className="app-surface rounded-lg shadow p-4">
              {paginatedBundles.length === 0 ? (
                <div className="app-muted">No bundles found.</div>
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
                      <tr key={bundle.id} className="border-t border-gray-200 dark:border-gray-700">
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
                            className="text-blue-600 dark:text-blue-400 hover:underline mr-4"
                          >
                            Edit
                          </Link>
                          <Link
                            to={`/admin/manage-bundle/${bundle.id}`}
                            className="text-purple-600 dark:text-purple-400 hover:underline mr-4"
                          >
                            Manage
                          </Link>
                          <button
                            className="text-red-600 dark:text-red-400 hover:underline"
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
                  className="px-3 py-1 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white disabled:opacity-50"
                >
                  Prev
                </button>
                {Array.from({ length: totalBundlePages }, (_, i) => (
                  <button
                    key={i + 1}
                    onClick={() => setBundlePage(i + 1)}
                    className={`px-3 py-1 rounded ${bundlePage === i + 1 ? 'bg-blue-500 text-white' : 'bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white'}`}
                  >
                    {i + 1}
                  </button>
                ))}
                <button
                  onClick={() => setBundlePage((p) => Math.min(totalBundlePages, p + 1))}
                  disabled={bundlePage === totalBundlePages}
                  className="px-3 py-1 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white disabled:opacity-50"
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}

        {activeTab === "batches" && (
          <>
            <h2 className="text-xl font-semibold mb-4">Batches</h2>
            <form onSubmit={createBatch} className="app-surface rounded-lg p-4 mb-4 grid gap-3 sm:grid-cols-2">
              <input
                value={newBatchName}
                onChange={(e) => setNewBatchName(e.target.value)}
                placeholder="Batch name"
                className="px-3 py-2 app-input rounded"
              />
              <input
                value={newBatchDescription}
                onChange={(e) => setNewBatchDescription(e.target.value)}
                placeholder="Optional description"
                className="px-3 py-2 app-input rounded"
              />
              <div className="sm:col-span-2 flex items-center gap-3">
                <button type="submit" disabled={creatingBatch} className="bg-green-600 hover:bg-green-700 text-white px-4 py-2 rounded disabled:opacity-50">Create Batch</button>
                {batchError && <span className="text-red-600 dark:text-red-400 text-sm">{batchError}</span>}
              </div>
            </form>
            <input
              value={batchSearch}
              onChange={(e) => setBatchSearch(e.target.value)}
              placeholder="Search batches by name"
              className="px-3 py-2 app-input rounded mb-4 w-full sm:w-80"
            />
            <div className="app-surface rounded-lg shadow p-4 overflow-x-auto">
              {batches.length === 0 ? (
                <div className="app-muted">No batches found.</div>
              ) : (
                <table className="w-full text-left">
                  <thead>
                    <tr>
                      <th className="py-2 px-3">Name</th>
                      <th className="py-2 px-3">Status</th>
                      <th className="py-2 px-3">Students</th>
                      <th className="py-2 px-3">Problems</th>
                      <th className="py-2 px-3">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {batches.map((batch) => (
                      <tr key={batch.id} className="border-t border-gray-200 dark:border-gray-700">
                        <td className="py-2 px-3">{batch.name}</td>
                        <td className="py-2 px-3">{batch.active ? "Active" : "Archived"}</td>
                        <td className="py-2 px-3">{batch.memberCount}</td>
                        <td className="py-2 px-3">{batch.assignmentCount}</td>
                        <td className="py-2 px-3">
                          <Link to={`/admin/batches/${batch.id}`} className="text-blue-600 dark:text-blue-400 hover:underline">Open</Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
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
