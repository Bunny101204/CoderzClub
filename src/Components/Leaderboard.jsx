import React, { useState, useEffect } from "react";
import axios from "axios";

const Leaderboard = () => {
  const [leaderboard, setLeaderboard] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(null);
  const [isRefreshing, setIsRefreshing] = useState(false);

  useEffect(() => {
    // Initial fetch
    fetchLeaderboard();

    // Set up auto-refresh every 30 seconds
    const intervalId = setInterval(() => {
      fetchLeaderboard(true); // true = silent refresh
    }, 30000);

    // Refresh immediately when a successful submission occurs
    const handleLeaderboardRefresh = () => fetchLeaderboard(true);
    window.addEventListener("leaderboardRefresh", handleLeaderboardRefresh);

    // Cleanup on component unmount
    return () => {
      clearInterval(intervalId);
      window.removeEventListener("leaderboardRefresh", handleLeaderboardRefresh);
    };
  }, []);

  const fetchLeaderboard = async (silent = false) => {
    try {
      if (!silent) setLoading(true);
      setIsRefreshing(true);
      
      const response = await axios.get("/api/users/leaderboard");
      // Ensure data is an array
      const leaderboardData = Array.isArray(response.data) ? response.data : [];
      setLeaderboard(leaderboardData);
      setLastUpdated(new Date());
      setError(null);
    } catch (err) {
      setError(
        "Failed to fetch leaderboard. Please restart the backend server."
      );
      console.error("Error fetching leaderboard:", err);
      if (!silent) setLeaderboard([]);
    } finally {
      if (!silent) setLoading(false);
      setIsRefreshing(false);
    }
  };

  const getRankBadge = (index) => {
    switch (index) {
      case 0:
        return {
          icon: "🥇",
          bg: "bg-yellow-500/20",
          text: "text-yellow-400",
          border: "border-yellow-500/30"
        };
      case 1:
        return {
          icon: "🥈",
          bg: "bg-gray-500/20",
          text: "text-gray-300",
          border: "border-gray-500/30"
        };
      case 2:
        return {
          icon: "🥉",
          bg: "bg-orange-500/20",
          text: "text-orange-400",
          border: "border-orange-500/30"
        };
      default:
        return {
          icon: `#${index + 1}`,
          bg: "bg-blue-500/10",
          text: "text-blue-400",
          border: "border-blue-500/20"
        };
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 text-white p-6">
        <div className="max-w-4xl mx-auto">
          <h1 className="text-3xl font-bold mb-6">🏆 Leaderboard</h1>
          <div className="flex justify-center items-center h-64">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-900 text-white p-6">
        <div className="max-w-4xl mx-auto">
          <h1 className="text-3xl font-bold mb-6">🏆 Leaderboard</h1>
          <div className="bg-red-900/20 border border-red-500 rounded-lg p-4 text-red-300 mb-4">
            {error}
          </div>
          <button
            onClick={() => fetchLeaderboard()}
            className="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors"
          >
            Try Again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-900 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-5xl font-bold mb-2 bg-gradient-to-r from-yellow-400 via-purple-500 to-blue-500 bg-clip-text text-transparent">
            Leaderboard
          </h1>
          <p className="text-gray-400 text-lg">Ranked by total points earned</p>
        </div>

        <div className="bg-gray-800 rounded-2xl shadow-2xl overflow-hidden border border-gray-700">
          {/* Header */}
          <div className="bg-gradient-to-r from-blue-900 via-purple-900 to-blue-900 px-8 py-6">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-2xl font-bold mb-1">Top Coders</h2>
                <p className="text-gray-300">Compete and climb the ranks</p>
              </div>
              <div className="flex items-center gap-6">
                <div className="text-right">
                  <div className="text-sm text-gray-300 mb-1">
                    Total Participants
                  </div>
                  <div className="text-3xl font-bold">{leaderboard.length}</div>
                </div>
                <button
                  onClick={() => fetchLeaderboard(false)}
                  disabled={isRefreshing}
                  className={`px-4 py-2 rounded-lg flex items-center gap-2 transition-all ${
                    isRefreshing
                      ? "bg-gray-600 cursor-not-allowed opacity-50"
                      : "bg-blue-600 hover:bg-blue-700"
                  }`}
                  title="Refresh leaderboard"
                >
                  <span>🔄</span>
                  {isRefreshing ? "Refreshing..." : "Refresh"}
                </button>
              </div>
            </div>
            {lastUpdated && (
              <div className="text-xs text-gray-400 mt-3">
                Last updated: {lastUpdated.toLocaleTimeString()}
              </div>
            )}
          </div>

          {/* Leaderboard List */}
          <div className="divide-y divide-gray-700">
            {leaderboard.map((user, index) => {
              const badge = getRankBadge(index);
              return (
                <div
                  key={user.id}
                  className={`px-8 py-6 hover:bg-gray-700/30 transition-all duration-200 ${
                    index < 3
                      ? "bg-gradient-to-r from-gray-800/50 to-transparent"
                      : ""
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-6 flex-1">
                      {/* Rank Badge */}
                      <div
                        className={`w-16 h-16 ${badge.bg} ${badge.text} rounded-full flex items-center justify-center font-bold text-xl shadow-lg flex-shrink-0 border ${badge.border}`}
                      >
                        {badge.icon}
                      </div>

                      {/* User Avatar */}
                      <div className="w-16 h-16 bg-gradient-to-br from-blue-500 via-purple-500 to-pink-500 rounded-full flex items-center justify-center shadow-lg flex-shrink-0">
                        <span className="text-white font-bold text-xl">
                          {user.username?.charAt(0).toUpperCase()}
                        </span>
                      </div>

                      {/* User Info */}
                      <div className="flex-1 min-w-0">
                        <h3 className="font-bold text-xl mb-1 truncate">
                          {user.username}
                        </h3>
                        <div className="flex items-center gap-4 text-sm text-gray-400">
                          <span className="flex items-center gap-1">
                            <span className="w-2 h-2 bg-green-500 rounded-full"></span>
                            {user.problemsSolved || 0} Solved
                          </span>
                          <span className="flex items-center gap-1">
                            <span className="w-2 h-2 bg-blue-500 rounded-full"></span>
                            {user.currentStreak || 0} Day Streak
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Points */}
                    <div className="text-right ml-6">
                      <div className="text-4xl font-bold bg-gradient-to-r from-green-400 to-emerald-500 bg-clip-text text-transparent">
                        {user.totalPoints || 0}
                      </div>
                      <div className="text-sm text-gray-400 mt-1">points</div>
                    </div>
                  </div>

                  {/* Progress Bar for top 3 */}
                  {index < 3 && leaderboard.length > 0 && (
                    <div className="mt-4 pt-4 border-t border-gray-700">
                      <div className="flex items-center justify-between text-xs text-gray-400 mb-1">
                        <span>Progress to next rank</span>
                        <span>
                          {Math.round(
                            ((user.totalPoints || 0) /
                              Math.max(leaderboard[0]?.totalPoints || 1, 1)) *
                              100
                          )}
                          %
                        </span>
                      </div>
                      <div className="w-full bg-gray-700 rounded-full h-2 overflow-hidden">
                        <div
                          className={`h-2 ${badge.bg} rounded-full transition-all duration-500`}
                          style={{
                            width: `${Math.min(
                              ((user.totalPoints || 0) /
                                Math.max(leaderboard[0]?.totalPoints || 1, 1)) *
                                100,
                              100
                            )}%`,
                          }}
                        ></div>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {leaderboard.length === 0 && (
          <div className="text-center py-16 bg-gray-800 rounded-2xl border border-gray-700">
            <div className="text-6xl mb-4">📊</div>
            <h3 className="text-2xl font-semibold mb-2">No Data Yet</h3>
            <p className="text-gray-400">
              Be the first to solve problems and climb the leaderboard!
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default Leaderboard;
