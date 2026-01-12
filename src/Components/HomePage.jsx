import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const HomePage = ({ problems: propsProblems }) => {
  const { user } = useAuth();
  const [search, setSearch] = useState("");
  const [difficulty, setDifficulty] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [problems, setProblems] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [loading, setLoading] = useState(false);
  const problemsPerPage = 10;

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
  }, [currentPage, difficulty]);

  const fetchProblems = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams({
        page: currentPage.toString(),
        size: problemsPerPage.toString()
      });
      
      // Add difficulty filter to API call
      if (difficulty && difficulty !== '') {
        params.append('difficulty', difficulty);
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
        
        // Apply client-side filtering if API doesn't fully support it
        if (difficulty && difficulty !== '') {
          problemsArray = problemsArray.filter(p => 
            p.difficulty === difficulty || 
            (difficulty === 'EASY' && p.difficulty === 'BASIC') ||
            (difficulty === 'MEDIUM' && p.difficulty === 'INTERMEDIATE') ||
            (difficulty === 'HARD' && p.difficulty === 'ADVANCED')
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
            
            // For title search: prefix match or substring match
            return title.startsWith(searchLower) || title.includes(searchLower);
          });
        }
        
        setTotalPages(data.totalPages || 0);
        setTotalItems(problemsArray.length);
      } else if (Array.isArray(data)) {
        // Fallback for old API format - filter client-side if needed
        problemsArray = data;
        
        // Apply client-side filtering if API doesn't support it
        if (difficulty && difficulty !== '') {
          problemsArray = problemsArray.filter(p => 
            p.difficulty === difficulty || 
            (difficulty === 'EASY' && p.difficulty === 'BASIC') ||
            (difficulty === 'MEDIUM' && p.difficulty === 'INTERMEDIATE') ||
            (difficulty === 'HARD' && p.difficulty === 'ADVANCED')
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
            
            // For title search: prefix match or substring match
            return title.startsWith(searchLower) || title.includes(searchLower);
          });
        }
        
        setTotalPages(Math.ceil(problemsArray.length / problemsPerPage));
        setTotalItems(problemsArray.length);
        
        // Apply pagination
        const startIndex = currentPage * problemsPerPage;
        problemsArray = problemsArray.slice(startIndex, startIndex + problemsPerPage);
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
        if (search && search.trim() !== '') {
          const searchLower = search.toLowerCase().trim();
          problemsArray = problemsArray.filter(p => {
            const title = (p.title || '').toLowerCase();
            const idStr = String(p.id || '');
            
            // For ID search: exact match or prefix match only
            if (/^\d+$/.test(searchLower)) {
              return idStr === searchLower || idStr.startsWith(searchLower);
            }
            
            // For title search: prefix match or substring match
            return title.startsWith(searchLower) || title.includes(searchLower);
          });
        }
        setTotalPages(Math.ceil(problemsArray.length / problemsPerPage));
        setTotalItems(problemsArray.length);
      }
      
      // Sort by difficulty order
      if (problemsArray.length > 0) {
        const difficultyOrder = {
          'EASY': 1, 'BASIC': 1,
          'MEDIUM': 2, 'INTERMEDIATE': 2,
          'HARD': 3, 'ADVANCED': 3,
          'SDE': 4,
          'EXPERT': 5
        };
        problemsArray.sort((a, b) => {
          const aOrder = difficultyOrder[a.difficulty] || 99;
          const bOrder = difficultyOrder[b.difficulty] || 99;
          return aOrder - bOrder;
        });
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
        if (search && search.trim() !== '') {
          const searchLower = search.toLowerCase();
          problemsArray = problemsArray.filter(p => 
            p.title?.toLowerCase().includes(searchLower) ||
            String(p.id).includes(searchLower)
          );
        }
        setProblems(problemsArray);
        setTotalPages(Math.ceil(problemsArray.length / problemsPerPage));
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
        <div className="flex gap-4 mb-6">
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
            <option value="BASIC">Basic</option>
            <option value="INTERMEDIATE">Intermediate</option>
            <option value="ADVANCED">Advanced</option>
            <option value="SDE">SDE</option>
            <option value="EXPERT">Expert</option>
          </select>
          <input
            type="text"
            placeholder="Search by ID or Title..."
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
              <th className="py-2 px-3">#</th>
              <th className="py-2 px-3">Title</th>
              <th className="py-2 px-3">Difficulty</th>
              <th className="py-2 px-3">Tags</th>
              <th className="py-2 px-3">Action</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={5} className="text-gray-400 text-center py-4">Loading...</td>
              </tr>
            ) : problems.length === 0 ? (
              <tr>
                <td colSpan={5} className="text-gray-400 text-center py-4">No problems found.</td>
              </tr>
            ) : (
              problems.map((problem, idx) => (
                <tr key={problem.id} className="border-t border-gray-700">
                  <td className="py-2 px-3 font-mono">{currentPage * problemsPerPage + idx + 1}</td>
                  <td className="py-2 px-3">{problem.title}</td>
                  <td className="py-2 px-3">{problem.difficulty || "N/A"}</td>
                  <td className="py-2 px-3">{(problem.tags || []).join(", ")}</td>
                  <td className="py-2 px-3">
                    <Link
                      to={`/problem/${problem.id}`}
                      className="text-blue-400 hover:underline"
                    >
                      Solve
                    </Link>
                  </td>
                </tr>
              ))
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
