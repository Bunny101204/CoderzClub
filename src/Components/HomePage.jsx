import React, { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../apiClient";
import { useAuth } from "../context/AuthContext";
import { useProblemStatus } from "../hooks/useProblemStatus";
import ProblemStatusMark from "./ProblemStatusMark";
import { catalogStatus, filtersAreActive } from "../catalog/problemCatalog.js";

const HomePage = ({ problems: propsProblems }) => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [search, setSearch] = useState("");
  const [difficulty, setDifficulty] = useState("");
  const [topic, setTopic] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [problems, setProblems] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(null);
  const [itemsPerPage, setItemsPerPage] = useState(10);

  // Use optimized custom hook for problem status (batches requests, prevents N+1 queries)
  const { problemStatus } = useProblemStatus(problems, user);

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => {
      if (currentPage === 0) {
        fetchProblems();
      } else {
        setCurrentPage(0);
      }
    }, search ? 500 : 0);
    
    return () => clearTimeout(timer);
  }, [search]);

  // Fetch problems from API with filters
  useEffect(() => {
    fetchProblems();
  }, [currentPage, difficulty, topic, itemsPerPage]);

  const getDifficultyBadge = (difficulty) => {
    const styles = {
      EASY: 'bg-green-500 text-white',
      BASIC: 'bg-green-500 text-white',
      MEDIUM: 'bg-yellow-500 text-black',
      INTERMEDIATE: 'bg-yellow-500 text-black',
      HARD: 'bg-red-500 text-white',
      ADVANCED: 'bg-red-500 text-white',
      SDE: 'bg-purple-600 text-white',
      EXPERT: 'bg-purple-600 text-white'
    };
    return styles[difficulty] || 'bg-gray-600 text-white';
  };

  const getStatusIcon = (status) => <ProblemStatusMark status={status} />;

  const compareById = (a, b) => {
    const idA = String(a.id || "");
    const idB = String(b.id || "");
    return idA.localeCompare(idB, undefined, { numeric: true, sensitivity: 'base' });
  };

  const fetchProblems = async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const params = {
        page: currentPage,
        size: itemsPerPage,
      };

      if (difficulty && difficulty !== '') {
        params.difficulty = difficulty;
      }
      if (topic && topic !== '') {
        params.tags = topic;
      }
      if (search && search.trim() !== '') {
        params.search = search.trim();
      }

      const response = await api.problems.getAll(params);
      const data = response.data;
      let problemsArray = [];
      
      // Handle both new paginated response and old array response
      if (data.problems && Array.isArray(data.problems)) {
        problemsArray = data.problems;
        setTotalPages(data.totalPages || 0);
        setTotalItems(data.totalItems || data.totalElements || problemsArray.length);
      } else if (Array.isArray(data)) {
        // Fallback for old API format - filter client-side if needed
        problemsArray = data;
        
        if (difficulty && difficulty !== '') {
          problemsArray = problemsArray.filter(p => 
            p.difficulty === difficulty || 
            (difficulty === 'EASY' && p.difficulty === 'BASIC') ||
            (difficulty === 'MEDIUM' && p.difficulty === 'INTERMEDIATE') ||
            (difficulty === 'HARD' && p.difficulty === 'ADVANCED')
          );
        }
        if (topic && topic !== '') {
          problemsArray = problemsArray.filter(p => 
            (p.tags || []).map(String).map(t => t.toLowerCase()).includes(topic.toLowerCase())
          );
        }
        
        if (search && search.trim() !== '') {
          const searchLower = search.toLowerCase().trim();
          problemsArray = problemsArray.filter(p => {
            const title = (p.title || '').toLowerCase();
            const idStr = String(p.id || '');
            
            if (/^\d+$/.test(searchLower)) {
              return idStr === searchLower || idStr.startsWith(searchLower);
            }
            
            return title.startsWith(searchLower);
          });
        }
        
        setTotalPages(Math.ceil(problemsArray.length / itemsPerPage));
        setTotalItems(problemsArray.length);
        
        const startIndex = currentPage * itemsPerPage;
        problemsArray = problemsArray.slice(startIndex, startIndex + itemsPerPage);
      } else if (propsProblems && Array.isArray(propsProblems)) {
        // Use props if API fails
        problemsArray = propsProblems;
        if (difficulty && difficulty !== '') {
          problemsArray = problemsArray.filter(p => 
            p.difficulty === difficulty || 
            (difficulty === 'EASY' && p.difficulty === 'BASIC') ||
            (difficulty === 'MEDIUM' && p.difficulty === 'INTERMEDIATE') ||
            (difficulty === 'HARD' && p.difficulty === 'ADVANCED')
          );
        }
        if (topic && topic !== '') {
          problemsArray = problemsArray.filter(p => 
            (p.tags || []).map(String).map(t => t.toLowerCase()).includes(topic.toLowerCase())
          );
        }
        if (search && search.trim() !== '') {
          const searchLower = search.toLowerCase().trim();
          problemsArray = problemsArray.filter(p => {
            const title = (p.title || '').toLowerCase();
            const idStr = String(p.id || '');
            
            // For ID search: exact match or prefix match only
            if (/^\d+$/.test(searchLower)) {
              return idStr === searchLower || idStr.startsWith(searchLower);
            }
            
            // For title search: prefix match only
            return title.startsWith(searchLower);
          });
        }
        setTotalPages(Math.ceil(problemsArray.length / itemsPerPage));
        setTotalItems(problemsArray.length);
      }
      
      // Sort by ID for stable display order
      if (problemsArray.length > 0) {
        problemsArray.sort(compareById);
      }
      
      setProblems(problemsArray);
      setLoadError(null);
    } catch (error) {
      console.error('Error fetching problems:', error);
      // Fallback to props if available
      if (propsProblems && Array.isArray(propsProblems)) {
        let problemsArray = propsProblems;
        if (difficulty && difficulty !== '') {
          problemsArray = problemsArray.filter(p => p.difficulty === difficulty);
        }
        if (topic && topic !== '') {
          problemsArray = problemsArray.filter(p => 
            (p.tags || []).map(String).map(t => t.toLowerCase()).includes(topic.toLowerCase())
          );
        }
        if (search && search.trim() !== '') {
          const searchLower = search.toLowerCase();
          problemsArray = problemsArray.filter(p => 
            p.title?.toLowerCase().startsWith(searchLower) ||
            String(p.id).startsWith(searchLower)
          );
        }
        setProblems(problemsArray);
        setTotalPages(Math.ceil(problemsArray.length / itemsPerPage));
        setTotalItems(problemsArray.length);
      } else {
        setProblems([]);
        setTotalPages(0);
        setTotalItems(0);
        const offline = typeof navigator !== "undefined" && navigator.onLine === false;
        setLoadError(offline ? "offline" : "error");
      }
    } finally {
      setLoading(false);
    }
  };

  const activeFilters = filtersAreActive({ search, difficulty, topic });
  const listStatus = catalogStatus({
    loading,
    error: loadError,
    offline: loadError === "offline",
    count: problems.length,
    filtersActive: activeFilters,
  });
  const clearFilters = () => {
    setSearch("");
    setDifficulty("");
    setTopic("");
    setCurrentPage(0);
  };

  return (
    <div className="app-shell px-4 py-6 sm:p-8 pb-24">
      <div className="max-w-3xl mx-auto">
        <h1 className="text-3xl font-bold mb-6">Problem List</h1>
        <div className="flex flex-col lg:flex-row gap-4 mb-6">
          <div className="flex flex-wrap items-center gap-3">
            <select
              aria-label="Difficulty"
              value={difficulty}
              onChange={(e) => {
                setDifficulty(e.target.value);
                setCurrentPage(0);
              }}
              className="px-4 py-2 app-input rounded focus:outline-none"
            >
              <option value="">All Difficulties</option>
              <option value="EASY">Easy</option>
              <option value="MEDIUM">Medium</option>
              <option value="HARD">Hard</option>
            </select>
            <select
              aria-label="Topic"
              value={topic}
              onChange={(e) => {
                setTopic(e.target.value);
                setCurrentPage(0);
              }}
              className="px-4 py-2 app-input rounded focus:outline-none"
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
            <div className="flex items-center">
              <label className="text-sm app-muted mr-2">Items:</label>
              <select
                aria-label="Items per page"
                value={itemsPerPage}
                onChange={(e) => { setItemsPerPage(Number(e.target.value)); setCurrentPage(0); }}
                className="px-3 py-2 app-input rounded focus:outline-none"
              >
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
                <option value={100}>100</option>
              </select>
            </div>
          </div>
          <input
            type="text"
            placeholder="Search by ID or Title prefix..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setCurrentPage(0);
            }}
            className="flex-1 min-w-0 p-3 rounded app-input focus:outline-none"
            aria-label="Search by ID or title prefix"
          />
        </div>
        {activeFilters && (
          <div className="mb-4">
            <button type="button" onClick={clearFilters} className="px-3 py-2 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white">
              Clear filters
            </button>
          </div>
        )}
        {listStatus.kind !== "ready" && (
          <div className="mb-4 app-surface rounded-lg p-4" role="status">
            <p>{listStatus.text}</p>
            {(listStatus.kind === "error" || listStatus.kind === "offline") && (
              <button type="button" onClick={fetchProblems} className="mt-3 px-3 py-2 rounded bg-blue-600 text-white">
                Retry
              </button>
            )}
          </div>
        )}
        <div className="md:hidden space-y-3">
          {listStatus.kind === "ready" && problems.map((problem) => {
            const status = problemStatus[String(problem.id)] || problemStatus[String(problem.numericId)] || "UNSOLVED";
            const displayId = problem.numericId != null ? problem.numericId : problem.id;
            return (
              <button
                key={problem.id}
                type="button"
                onClick={() => navigate(`/problem/${problem.id}`)}
                className="w-full text-left app-surface rounded-lg p-4"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="font-mono text-sm app-muted">#{displayId}</p>
                    <p className="font-semibold break-words">{problem.title}</p>
                  </div>
                  {getStatusIcon(status)}
                </div>
                <p className="mt-2 text-sm">{problem.difficulty || "N/A"}</p>
                <p className="mt-1 text-sm app-muted break-words">{(problem.tags || []).join(", ") || "No tags"}</p>
              </button>
            );
          })}
        </div>
        <div className="hidden md:block overflow-x-auto">
        <table className="w-full text-left app-surface rounded-lg shadow">
          <thead>
            <tr>
              <th className="py-2 px-3" aria-label="Status"></th>
              <th className="py-2 px-3">ID</th>
              <th className="py-2 px-3">Title</th>
              <th className="py-2 px-3">Difficulty</th>
              <th className="py-2 px-3">Tags</th>
            </tr>
          </thead>
          <tbody>
            {listStatus.kind === "ready" ? (
              problems.map((problem) => {
                const status = problemStatus[String(problem.id)] || problemStatus[String(problem.numericId)] || 'UNSOLVED';
                const displayId = problem.numericId != null ? problem.numericId : problem.id;
                return (
                  <tr
                    key={problem.id}
                    className="border-t border-gray-200 dark:border-gray-700 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-700/40"
                    onClick={() => navigate(`/problem/${problem.id}`)}
                  >
                    <td className="py-2 px-3 align-top">
                      {getStatusIcon(status)}
                    </td>
                    <td className="py-2 px-3 font-mono">{displayId}</td>
                    <td className="py-2 px-3">
                      <div className="font-semibold">{problem.title}</div>
                    </td>
                    <td className="py-2 px-3">
                      <span className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold ${getDifficultyBadge(problem.difficulty)}`}>
                        {problem.difficulty || 'N/A'}
                      </span>
                    </td>
                    <td className="py-2 px-3 app-muted">{(problem.tags || []).join(', ')}</td>
                  </tr>
                );
              })
            ) : null}
          </tbody>
        </table>
        </div>
        {/* Pagination Controls */}
        {totalPages > 1 && (
          <div className="flex justify-center items-center mt-6 space-x-2">
            <button
              onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
              disabled={currentPage === 0}
              className="px-3 py-1 rounded bg-gray-700 text-white disabled:opacity-50 hover:bg-gray-600"
            >
              Prev
            </button>
            <span className="text-gray-400">
              Page {currentPage + 1} of {totalPages} ({totalItems} total)
            </span>
            <button
              onClick={() => setCurrentPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={currentPage >= totalPages - 1}
              className="px-3 py-1 rounded bg-gray-700 text-white disabled:opacity-50 hover:bg-gray-600"
            >
              Next
            </button>
          </div>
        )}
      </div>
      {user?.role === 'ADMIN' && (
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

export default HomePage;
