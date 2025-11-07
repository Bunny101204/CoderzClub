import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const BundleDashboard = () => {
  const { user } = useAuth();
  const [bundles, setBundles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedDifficulty, setSelectedDifficulty] = useState("ALL");
  const [selectedCategory, setSelectedCategory] = useState("ALL");
  const [showAdminControls, setShowAdminControls] = useState(false);

  const difficulties = ["ALL", "BASIC", "INTERMEDIATE", "ADVANCED", "SDE", "EXPERT"];
  const categories = ["ALL", "ALGORITHMS", "DATA_STRUCTURES", "SYSTEM_DESIGN", "DATABASE", "WEB_DEVELOPMENT"];

  useEffect(() => {
    fetchBundles();
  }, []);

  useEffect(() => {
    // Check if user is admin
    setShowAdminControls(user?.role === 'ADMIN');
  }, [user]);

  const fetchBundles = async () => {
    try {
      const response = await fetch("http://localhost:8080/api/bundles");
      if (response.ok) {
        const data = await response.json();
        setBundles(data);
      }
    } catch (error) {
      console.error("Error fetching bundles:", error);
    } finally {
      setLoading(false);
    }
  };

  const filteredBundles = bundles.filter(bundle => {
    const difficultyMatch = selectedDifficulty === "ALL" || bundle.difficulty === selectedDifficulty;
    const categoryMatch = selectedCategory === "ALL" || bundle.category === selectedCategory;
    return difficultyMatch && categoryMatch;
  });

  const getDifficultyColor = (difficulty) => {
    const colors = {
      BASIC: "bg-green-500",
      INTERMEDIATE: "bg-yellow-500",
      ADVANCED: "bg-orange-500",
      SDE: "bg-red-500",
      EXPERT: "bg-purple-500"
    };
    return colors[difficulty] || "bg-gray-500";
  };

  const getDifficultyIcon = (difficulty) => {
    const icons = {
      BASIC: "🌱",
      INTERMEDIATE: "🚀",
      ADVANCED: "⚡",
      SDE: "🎯",
      EXPERT: "👑"
    };
    return icons[difficulty] || "📚";
  };

  const handleDeleteBundle = async (bundleId) => {
    if (!window.confirm("Are you sure you want to delete this bundle?")) return;
    try {
      // Get JWT token for authentication
      const token = localStorage.getItem('jwtToken');
      const headers = {};
      
      // Add Authorization header if token exists
      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }

      const response = await fetch(`http://localhost:8080/api/bundles/${bundleId}`, {
        method: "DELETE",
        headers
      });
      if (response.ok) {
        setBundles(bundles.filter(bundle => bundle.id !== bundleId));
      } else {
        alert("Failed to delete bundle");
      }
    } catch (error) {
      console.error("Error deleting bundle:", error);
      alert("Error deleting bundle");
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="text-white text-xl">Loading bundles...</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-900 text-white p-8">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="text-center mb-12">
          <h1 className="text-4xl font-bold mb-4">🚀 Problem Bundles</h1>
          <p className="text-gray-400 text-lg">
            Master coding with our curated problem collections
          </p>
          {showAdminControls && (
            <div className="mt-6">
              <Link
                to="/admin/add-bundle"
                className="bg-green-600 hover:bg-green-700 text-white font-semibold py-2 px-6 rounded-lg transition-all mr-4"
              >
                + Add New Bundle
              </Link>
              <Link
                to="/admin"
                className="bg-blue-600 hover:bg-blue-700 text-white font-semibold py-2 px-6 rounded-lg transition-all"
              >
                Admin Dashboard
              </Link>
            </div>
          )}
        </div>

        {/* Filters */}
        <div className="flex flex-wrap gap-4 mb-8 justify-center">
          <div className="flex flex-col">
            <label className="text-sm text-gray-400 mb-2">Difficulty</label>
            <select
              value={selectedDifficulty}
              onChange={(e) => setSelectedDifficulty(e.target.value)}
              className="bg-gray-800 text-white px-4 py-2 rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500"
            >
              {difficulties.map(diff => (
                <option key={diff} value={diff}>{diff}</option>
              ))}
            </select>
          </div>
          
          <div className="flex flex-col">
            <label className="text-sm text-gray-400 mb-2">Category</label>
            <select
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
              className="bg-gray-800 text-white px-4 py-2 rounded-lg border border-gray-600 focus:outline-none focus:border-blue-500"
            >
              {categories.map(cat => (
                <option key={cat} value={cat}>{cat.replace('_', ' ')}</option>
              ))}
            </select>
          </div>
        </div>

        {/* Bundles Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredBundles.map((bundle) => (
            <div
              key={bundle.id}
              className="bg-gray-800 rounded-xl p-6 hover:bg-gray-700 transition-all duration-300 border border-gray-700 hover:border-blue-500"
            >
              {/* Bundle Header */}
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center space-x-3">
                  <span className="text-2xl">{getDifficultyIcon(bundle.difficulty)}</span>
                  <span className={`px-3 py-1 rounded-full text-xs font-semibold ${getDifficultyColor(bundle.difficulty)}`}>
                    {bundle.difficulty}
                  </span>
                </div>
                {bundle.isPremium && (
                  <span className="px-3 py-1 rounded-full text-xs font-semibold bg-yellow-500 text-black">
                    PREMIUM
                  </span>
                )}
              </div>

              {/* Bundle Info */}
              <h3 className="text-xl font-bold mb-2">{bundle.name}</h3>
              <p className="text-gray-400 text-sm mb-4">{bundle.description}</p>

              {/* Bundle Stats */}
              <div className="grid grid-cols-3 gap-4 mb-6">
                <div className="text-center">
                  <div className="text-2xl font-bold text-blue-400">{bundle.totalProblems}</div>
                  <div className="text-xs text-gray-400">Problems</div>
                </div>
                <div className="text-center">
                  <div className="text-2xl font-bold text-green-400">{bundle.totalPoints}</div>
                  <div className="text-xs text-gray-400">Points</div>
                </div>
                <div className="text-center">
                  <div className="text-2xl font-bold text-purple-400">{bundle.estimatedTotalTime}</div>
                  <div className="text-xs text-gray-400">Minutes</div>
                </div>
              </div>

              {/* Bundle Tags */}
              <div className="flex flex-wrap gap-2 mb-6">
                {bundle.tags?.map((tag, index) => (
                  <span
                    key={index}
                    className="px-2 py-1 bg-gray-700 text-xs rounded-full text-gray-300"
                  >
                    {tag}
                  </span>
                ))}
              </div>

              {/* Bundle Actions */}
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-2">
                  {bundle.isPremium && !user?.isPremium ? (
                    <div className="flex items-center space-x-2">
                      <span className="text-yellow-400 font-semibold">${bundle.price}</span>
                      <button className="px-4 py-2 bg-yellow-500 text-black rounded-lg font-semibold hover:bg-yellow-400 transition-colors">
                        Upgrade
                      </button>
                    </div>
                  ) : (
                    <Link
                      to={`/bundle/${bundle.id}`}
                      className="px-6 py-2 bg-blue-600 text-white rounded-lg font-semibold hover:bg-blue-700 transition-colors"
                    >
                      Start Bundle
                    </Link>
                  )}
                  
                  {showAdminControls && (
                    <div className="flex space-x-2 ml-4">
                      <Link
                        to={`/admin/manage-bundle/${bundle.id}`}
                        className="px-3 py-1 bg-purple-600 hover:bg-purple-500 text-white text-sm rounded transition-colors"
                        title="Manage Problems"
                      >
                        Problems
                      </Link>
                      <Link
                        to={`/admin/edit-bundle/${bundle.id}`}
                        className="px-3 py-1 bg-gray-600 hover:bg-gray-500 text-white text-sm rounded transition-colors"
                      >
                        Edit
                      </Link>
                      <button
                        onClick={() => handleDeleteBundle(bundle.id)}
                        className="px-3 py-1 bg-red-600 hover:bg-red-500 text-white text-sm rounded transition-colors"
                      >
                        Delete
                      </button>
                    </div>
                  )}
                </div>
                
                <div className="text-right">
                  <div className="text-sm text-gray-400">Category</div>
                  <div className="text-sm font-semibold">{bundle.category?.replace('_', ' ')}</div>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Empty State */}
        {filteredBundles.length === 0 && (
          <div className="text-center py-12">
            <div className="text-6xl mb-4">📚</div>
            <h3 className="text-xl font-semibold mb-2">No bundles found</h3>
            <p className="text-gray-400">Try adjusting your filters or check back later for new content.</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default BundleDashboard;
