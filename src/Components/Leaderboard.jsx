import React, { useState, useEffect } from 'react';
import axios from 'axios';

const Leaderboard = () => {
  const [leaderboard, setLeaderboard] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchLeaderboard();
  }, []);

  const fetchLeaderboard = async () => {
    try {
      setLoading(true);
      const response = await axios.get('/api/users/leaderboard');
      // Ensure data is an array
      const leaderboardData = Array.isArray(response.data) ? response.data : [];
      setLeaderboard(leaderboardData);
      setError(null);
    } catch (err) {
      setError('Failed to fetch leaderboard. Please restart the backend server.');
      console.error('Error fetching leaderboard:', err);
      setLeaderboard([]);
    } finally {
      setLoading(false);
    }
  };

  const getRankIcon = (index) => {
    switch (index) {
      case 0: return '🥇';
      case 1: return '🥈';
      case 2: return '🥉';
      default: return `#${index + 1}`;
    }
  };

  const getRankColor = (index) => {
    switch (index) {
      case 0: return 'text-yellow-400';
      case 1: return 'text-gray-300';
      case 2: return 'text-orange-400';
      default: return 'text-gray-400';
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
          <div className="bg-red-900/20 border border-red-500 rounded-lg p-4 text-red-300">
            {error}
          </div>
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
              <div className="text-right">
                <div className="text-sm text-gray-300 mb-1">Total Participants</div>
                <div className="text-3xl font-bold">{leaderboard.length}</div>
              </div>
            </div>
          </div>
          
          {/* Leaderboard List */}
          <div className="divide-y divide-gray-700">
            {leaderboard.map((user, index) => {
              const badge = getRankBadge(index);
              return (
                <div 
                  key={user.id} 
                  className={`px-8 py-6 hover:bg-gray-700/30 transition-all duration-200 ${
                    index < 3 ? 'bg-gradient-to-r from-gray-800/50 to-transparent' : ''
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-6 flex-1">
                      {/* Rank Badge */}
                      <div className={`w-16 h-16 ${badge.bg} ${badge.text} rounded-full flex items-center justify-center font-bold text-xl shadow-lg flex-shrink-0`}>
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
                        <h3 className="font-bold text-xl mb-1 truncate">{user.username}</h3>
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
                        <span>{Math.round(((user.totalPoints || 0) / Math.max(leaderboard[0]?.totalPoints || 1, 1)) * 100)}%</span>
                      </div>
                      <div className="w-full bg-gray-700 rounded-full h-2 overflow-hidden">
                        <div 
                          className={`h-2 ${badge.bg} rounded-full transition-all duration-500`}
                          style={{ width: `${Math.min(((user.totalPoints || 0) / Math.max(leaderboard[0]?.totalPoints || 1, 1)) * 100, 100)}%` }}
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
            <p className="text-gray-400">Be the first to solve problems and climb the leaderboard!</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default Leaderboard;
