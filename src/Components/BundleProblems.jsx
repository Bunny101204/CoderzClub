import React, { useState, useEffect } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const BundleProblems = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [bundle, setBundle] = useState(null);
  const [problems, setProblems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [userProgress, setUserProgress] = useState({});
  const [selectedProblem, setSelectedProblem] = useState(null);

  useEffect(() => {
    fetchBundleData();
  }, [id]);

  const fetchBundleData = async () => {
    try {
      const response = await fetch(`http://localhost:8080/api/bundles/${id}`);
      if (response.ok) {
        const data = await response.json();
        setBundle(data);
        
        // Fetch all problems in the bundle
        if (data.problemIds && data.problemIds.length > 0) {
          await fetchBundleProblems(data.problemIds);
        }
      }
    } catch (error) {
      console.error("Error fetching bundle:", error);
    } finally {
      setLoading(false);
    }
  };

  const fetchBundleProblems = async (problemIds) => {
    try {
      const response = await fetch("http://localhost:8080/api/problems");
      if (response.ok) {
        const allProblems = await response.json();
        // Filter and order problems based on bundle's problemIds order
        const orderedProblems = problemIds
          .map(id => allProblems.find(p => p.id === id))
          .filter(Boolean);
        setProblems(orderedProblems);
        
        // Fetch user progress if authenticated
        if (user) {
          await fetchUserProgress(problemIds);
        }
      }
    } catch (error) {
      console.error("Error fetching problems:", error);
    }
  };

  const fetchUserProgress = async (problemIds) => {
    try {
      const token = localStorage.getItem('jwtToken');
      if (!token) return;

      // Fetch submissions for each problem
      const progress = {};
      for (const problemId of problemIds) {
        const response = await fetch(`http://localhost:8080/api/submissions/problem/${problemId}`, {
          headers: {
            "Authorization": `Bearer ${token}`
          }
        });
        
        if (response.ok) {
          const submissions = await response.json();
          const hasAccepted = submissions.some(s => s.status === "ACCEPTED");
          progress[problemId] = {
            attempted: submissions.length > 0,
            solved: hasAccepted,
            submissions: submissions.length
          };
        }
      }
      setUserProgress(progress);
    } catch (error) {
      console.error("Error fetching user progress:", error);
    }
  };

  const getDifficultyColor = (difficulty) => {
    const colors = {
      EASY: "bg-green-500",
      MEDIUM: "bg-yellow-500",
      HARD: "bg-red-500"
    };
    return colors[difficulty] || "bg-gray-500";
  };

  const getBundleDifficultyColor = (difficulty) => {
    const colors = {
      BASIC: "bg-green-500",
      INTERMEDIATE: "bg-yellow-500",
      ADVANCED: "bg-orange-500",
      SDE: "bg-red-500",
      EXPERT: "bg-purple-500"
    };
    return colors[difficulty] || "bg-gray-500";
  };

  const getProgressStats = () => {
    const total = problems.length;
    const solved = Object.values(userProgress).filter(p => p.solved).length;
    const attempted = Object.values(userProgress).filter(p => p.attempted).length;
    return { total, solved, attempted, percentage: total > 0 ? Math.round((solved / total) * 100) : 0 };
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="text-white text-xl">Loading bundle...</div>
      </div>
    );
  }

  if (!bundle) {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="text-center">
          <div className="text-white text-xl mb-4">Bundle not found</div>
          <Link to="/bundles" className="text-blue-400 hover:text-blue-300">
            Back to Bundles
          </Link>
        </div>
      </div>
    );
  }

  const stats = getProgressStats();

  return (
    <div className="min-h-screen bg-gray-900 text-white">
      {/* Bundle Header */}
      <div className="bg-gradient-to-r from-blue-900 to-purple-900 py-12 px-8">
        <div className="max-w-6xl mx-auto">
          <div className="flex items-center justify-between mb-6">
            <Link
              to="/bundles"
              className="flex items-center text-gray-300 hover:text-white transition-colors"
            >
              <span className="mr-2">←</span> Back to Bundles
            </Link>
            {user?.role === 'ADMIN' && (
              <Link
                to={`/admin/manage-bundle/${id}`}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors"
              >
                Manage Problems
              </Link>
            )}
          </div>
          
          <div className="flex items-center space-x-4 mb-4">
            <span className={`px-4 py-2 rounded-full text-sm font-semibold ${getBundleDifficultyColor(bundle.difficulty)}`}>
              {bundle.difficulty}
            </span>
            {bundle.isPremium && (
              <span className="px-4 py-2 rounded-full text-sm font-semibold bg-yellow-500 text-black">
                PREMIUM
              </span>
            )}
          </div>

          <h1 className="text-4xl font-bold mb-4">{bundle.name}</h1>
          <p className="text-xl text-gray-300 mb-6">{bundle.description}</p>

          {/* Bundle Stats */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="bg-white/10 backdrop-blur-sm rounded-lg p-4">
              <div className="text-3xl font-bold text-blue-400">{stats.total}</div>
              <div className="text-sm text-gray-300">Total Problems</div>
            </div>
            <div className="bg-white/10 backdrop-blur-sm rounded-lg p-4">
              <div className="text-3xl font-bold text-green-400">{stats.solved}</div>
              <div className="text-sm text-gray-300">Solved</div>
            </div>
            <div className="bg-white/10 backdrop-blur-sm rounded-lg p-4">
              <div className="text-3xl font-bold text-purple-400">{bundle.totalPoints || 0}</div>
              <div className="text-sm text-gray-300">Total Points</div>
            </div>
            <div className="bg-white/10 backdrop-blur-sm rounded-lg p-4">
              <div className="text-3xl font-bold text-yellow-400">{stats.percentage}%</div>
              <div className="text-sm text-gray-300">Progress</div>
            </div>
          </div>

          {/* Progress Bar */}
          {user && (
            <div className="mt-6">
              <div className="flex justify-between text-sm mb-2">
                <span>Your Progress</span>
                <span>{stats.solved} / {stats.total} completed</span>
              </div>
              <div className="w-full bg-gray-700 rounded-full h-3">
                <div
                  className="bg-gradient-to-r from-blue-500 to-green-500 h-3 rounded-full transition-all duration-300"
                  style={{ width: `${stats.percentage}%` }}
                ></div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Problems List */}
      <div className="max-w-6xl mx-auto px-8 py-12">
        <h2 className="text-2xl font-bold mb-6">Problems</h2>
        
        {problems.length === 0 ? (
          <div className="text-center py-12">
            <div className="text-6xl mb-4">📝</div>
            <h3 className="text-xl font-semibold mb-2">No problems in this bundle yet</h3>
            <p className="text-gray-400">Check back later for new content</p>
          </div>
        ) : (
          <div className="space-y-4">
            {problems.map((problem, index) => {
              const progress = userProgress[problem.id] || {};
              const isSolved = progress.solved;
              const isAttempted = progress.attempted;

              return (
                <div
                  key={problem.id}
                  className={`bg-gray-800 rounded-xl p-6 border transition-all duration-300 hover:scale-[1.02] ${
                    isSolved
                      ? 'border-green-500'
                      : isAttempted
                      ? 'border-yellow-500'
                      : 'border-gray-700 hover:border-blue-500'
                  }`}
                >
                  <div className="flex items-start justify-between">
                    <div className="flex items-start space-x-4 flex-1">
                      {/* Problem Number */}
                      <div className={`flex-shrink-0 w-12 h-12 rounded-full flex items-center justify-center text-xl font-bold ${
                        isSolved ? 'bg-green-500' : isAttempted ? 'bg-yellow-500' : 'bg-gray-700'
                      }`}>
                        {isSolved ? '✓' : index + 1}
                      </div>

                      {/* Problem Info */}
                      <div className="flex-1">
                        <div className="flex items-center space-x-3 mb-2">
                          <h3 className="text-xl font-bold">{problem.title}</h3>
                          <span className={`px-3 py-1 rounded-full text-xs font-semibold ${getDifficultyColor(problem.difficulty)}`}>
                            {problem.difficulty}
                          </span>
                        </div>
                        
                        <p className="text-gray-400 mb-3">{problem.description}</p>

                        {/* Tags */}
                        {problem.tags && problem.tags.length > 0 && (
                          <div className="flex flex-wrap gap-2 mb-3">
                            {problem.tags.map((tag, i) => (
                              <span
                                key={i}
                                className="px-2 py-1 bg-gray-700 text-xs rounded-full text-gray-300"
                              >
                                {tag}
                              </span>
                            ))}
                          </div>
                        )}

                        {/* Progress Info */}
                        {user && isAttempted && (
                          <div className="flex items-center space-x-4 text-sm">
                            <span className="text-gray-400">
                              {progress.submissions} attempt{progress.submissions !== 1 ? 's' : ''}
                            </span>
                            {isSolved && (
                              <span className="text-green-400 font-semibold flex items-center">
                                <span className="mr-1">✓</span> Solved
                              </span>
                            )}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Action Button */}
                    <div className="flex-shrink-0 ml-4">
                      <Link
                        to={`/problem/${problem.id}`}
                        className={`px-6 py-3 rounded-lg font-semibold transition-colors ${
                          isSolved
                            ? 'bg-green-600 hover:bg-green-700'
                            : isAttempted
                            ? 'bg-yellow-600 hover:bg-yellow-700'
                            : 'bg-blue-600 hover:bg-blue-700'
                        } text-white`}
                      >
                        {isSolved ? 'Review' : isAttempted ? 'Continue' : 'Solve'}
                      </Link>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

export default BundleProblems;



