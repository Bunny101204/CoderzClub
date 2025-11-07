import React, { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const HomePage = ({ problems }) => {
  const { user } = useAuth();
  const [search, setSearch] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const problemsPerPage = 10;

  // Filter problems by ID or title (case-insensitive)
  const filteredProblems = problems.filter((p) => {
    const idMatch = p.id && p.id.toString().includes(search.trim());
    const titleMatch = p.title && p.title.toLowerCase().includes(search.trim().toLowerCase());
    return idMatch || titleMatch;
  });

  // Pagination logic
  const totalPages = Math.ceil(filteredProblems.length / problemsPerPage);
  const paginatedProblems = filteredProblems.slice(
    (currentPage - 1) * problemsPerPage,
    currentPage * problemsPerPage
  );

  return (
    <div className="min-h-screen bg-gray-900 text-white p-8">
      <div className="max-w-3xl mx-auto">
        <h1 className="text-3xl font-bold mb-6">📝 Problem List</h1>
        <input
          type="text"
          placeholder="Search by ID or Title..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full p-3 mb-6 rounded bg-gray-800 text-white border border-gray-600 focus:outline-none"
        />
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
            {paginatedProblems.length === 0 ? (
              <tr>
                <td colSpan={5} className="text-gray-400 text-center py-4">No problems found.</td>
              </tr>
            ) : (
              paginatedProblems.map((problem, idx) => (
                <tr key={problem.id} className="border-t border-gray-700">
                  <td className="py-2 px-3 font-mono">{(currentPage - 1) * problemsPerPage + idx + 1}</td>
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
