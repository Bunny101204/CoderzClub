import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useProblemStatus } from "../hooks/useProblemStatus";

const HomePage = ({ problems: propsProblems }) => {
  const { user } = useAuth();

  const [search, setSearch] = useState("");
  const [difficulty, setDifficulty] = useState("");
  const [topic, setTopic] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [problems, setProblems] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [loading, setLoading] = useState(false);
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

  const getStatusIcon = (status) => {
    if (status === 'SOLVED') {
      return <span className="text-green-400 text-lg">✓</span>;
    }
    if (status === 'ATTEMPTED') {
      return <span className="text-yellow-400 text-lg">●</span>;
    }
    return null;
  };

  const compareById = (a, b) => {
    const idA = String(a.id || "");
    const idB = String(b.id || "");
    return idA.localeCompare(idB, undefined, { numeric: true, sensitivity: 'base' });
  };

  const fetchProblems = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams({
        page: currentPage.toString(),
        size: itemsPerPage.toString()
      });
      
      // Add difficulty filter to API call
      if (difficulty && difficulty !== '') {
        params.append('difficulty', difficulty);
      }
      if (topic && topic !== '') {
        params.append('tags', topic);
      }
      if (search && search.trim() !== '') {
        params.append('search', search.trim());
      }
      
      const response = await fetch(`/api/problems?${params}`);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const data = await response.json();
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
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-900 text-white p-8">
      <div className="max-w-3xl mx-auto">
        <h1 className="text-3xl font-bold mb-6">Problem List</h1>
        <div className="flex flex-col lg:flex-row gap-4 mb-6">
          <div className="flex flex-wrap items-center gap-3">
            <select
              value={difficulty}
              onChange={(e) => {
                setDifficulty(e.target.value);
                setCurrentPage(0);
              }}
              className="px-4 py-2 bg-gray-800 text-white rounded border border-gray-600 focus:outline-none"
            >
              <option value="">All Difficulties</option>
              <option value="EASY">Easy</option>
              <option value="MEDIUM">Medium</option>
              <option value="HARD">Hard</option>
            </select>
            <select
              value={topic}
              onChange={(e) => {
                setTopic(e.target.value);
                setCurrentPage(0);
              }}
              className="px-4 py-2 bg-gray-800 text-white rounded border border-gray-600 focus:outline-none"
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
              <label className="text-sm text-gray-400 mr-2">Items:</label>
              <select
                value={itemsPerPage}
                onChange={(e) => { setItemsPerPage(Number(e.target.value)); setCurrentPage(0); }}
                className="px-3 py-2 bg-gray-800 text-white rounded border border-gray-600 focus:outline-none"
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
            className="flex-1 p-3 rounded bg-gray-800 text-white border border-gray-600 focus:outline-none"
          />
        </div>
        <table className="w-full text-left bg-gray-800 rounded-lg shadow">
          <thead>
            <tr>
              <th className="py-2 px-3">Status</th>
              <th className="py-2 px-3">ID</th>
              <th className="py-2 px-3">Title</th>
              <th className="py-2 px-3">Difficulty</th>
              <th className="py-2 px-3">Tags</th>
              <th className="py-2 px-3">Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="text-gray-400 text-center py-4">Loading...</td>
              </tr>
            ) : problems.length === 0 ? (
              <tr>
                <td colSpan={6} className="text-gray-400 text-center py-4">No problems found.</td>
              </tr>
            ) : (
              problems.map((problem) => {
                const status = problemStatus[problem.id] || 'NOT_STARTED';
                return (
                  <tr key={problem.id} className="border-t border-gray-700">
                    <td className="py-2 px-3 align-top">
                      {getStatusIcon(status)}
                    </td>
                    <td className="py-2 px-3 font-mono">{problem.id}</td>
                    <td className="py-2 px-3">
                      <div className="font-semibold text-white">{problem.title}</div>
                    </td>
                    <td className="py-2 px-3">
                      <span className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold ${getDifficultyBadge(problem.difficulty)}`}>
                        {problem.difficulty || 'N/A'}
                      </span>
                    </td>
                    <td className="py-2 px-3 text-gray-300">{(problem.tags || []).join(', ')}</td>
                    <td className="py-2 px-3">
                      <Link
                        to={`/problem/${problem.id}`}
                        className="text-blue-400 hover:underline"
                      >
                        Solve
                      </Link>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
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
