import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';

const UserStats = () => {
  const { user } = useAuth();
  const [stats, setStats] = useState(null);
  const [submissions, setSubmissions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (user) {
      fetchUserStats();
      fetchUserSubmissions();
    }
  }, [user]);

  const fetchUserStats = async () => {
    try {
      const token = localStorage.getItem('token');
      const response = await axios.get('/api/users/stats', {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      setStats(response.data);
      setError(null);
    } catch (err) {
      setError('Failed to fetch user statistics');
      console.error('Error fetching stats:', err);
    }
  };

  const fetchUserSubmissions = async () => {
    try {
      const token = localStorage.getItem('token');
      const response = await axios.get('/api/submissions/my-submissions', {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      setSubmissions(response.data);
    } catch (err) {
      console.error('Error fetching submissions:', err);
    } finally {
      setLoading(false);
    }
  };

  const getSuccessRateColor = (rate) => {
    if (rate >= 0.8) return 'text-green-400';
    if (rate >= 0.6) return 'text-yellow-400';
    return 'text-red-400';
  };

  const getResultColor = (result) => {
    switch (result) {
      case 'ACCEPTED': return 'text-green-400 bg-green-900/20';
      case 'WRONG_ANSWER': return 'text-red-400 bg-red-900/20';
      case 'TIME_LIMIT_EXCEEDED': return 'text-yellow-400 bg-yellow-900/20';
      case 'COMPILATION_ERROR': return 'text-orange-400 bg-orange-900/20';
      default: return 'text-gray-400 bg-gray-900/20';
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 text-white p-6">
        <div className="max-w-6xl mx-auto">
          <h1 className="text-3xl font-bold mb-6">📊 My Statistics</h1>
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
        <div className="max-w-6xl mx-auto">
          <h1 className="text-3xl font-bold mb-6">📊 My Statistics</h1>
          <div className="bg-red-900/20 border border-red-500 rounded-lg p-4 text-red-300">
            {error}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-900 text-white p-6">
      <div className="max-w-6xl mx-auto">
        <h1 className="text-3xl font-bold mb-6">📊 My Statistics</h1>
        
        {stats && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
            {/* Total Points */}
            <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-gray-400 text-sm">Total Points</p>
                  <p className="text-3xl font-bold text-green-400">{stats.totalPoints}</p>
                </div>
                <div className="text-4xl">🏆</div>
              </div>
            </div>

            {/* Problems Solved */}
            <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-gray-400 text-sm">Problems Solved</p>
                  <p className="text-3xl font-bold text-blue-400">{stats.totalProblemsSolved}</p>
                </div>
                <div className="text-4xl">✅</div>
              </div>
            </div>

            {/* Current Streak */}
            <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-gray-400 text-sm">Current Streak</p>
                  <p className="text-3xl font-bold text-orange-400">{stats.currentStreak}</p>
                  <p className="text-xs text-gray-500">days</p>
                </div>
                <div className="text-4xl">🔥</div>
              </div>
            </div>

            {/* Success Rate */}
            <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-gray-400 text-sm">Success Rate</p>
                  <p className={`text-3xl font-bold ${getSuccessRateColor(stats.successRate)}`}>
                    {Math.round(stats.successRate * 100)}%
                  </p>
                </div>
                <div className="text-4xl">📈</div>
              </div>
            </div>
          </div>
        )}

        {/* Additional Stats */}
        {stats && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
            <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
              <h3 className="text-xl font-semibold mb-4">📊 Detailed Stats</h3>
              <div className="space-y-3">
                <div className="flex justify-between">
                  <span className="text-gray-400">Total Submissions:</span>
                  <span className="font-semibold">{stats.totalSubmissions}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-400">Accepted Submissions:</span>
                  <span className="font-semibold text-green-400">{stats.acceptedSubmissions}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-400">Longest Streak:</span>
                  <span className="font-semibold text-blue-400">{stats.longestStreak} days</span>
                </div>
              </div>
            </div>

            <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
              <h3 className="text-xl font-semibold mb-4">🎯 Progress</h3>
              <div className="space-y-3">
                <div>
                  <div className="flex justify-between text-sm mb-1">
                    <span>Problems Solved</span>
                    <span>{stats.totalProblemsSolved}/100</span>
                  </div>
                  <div className="w-full bg-gray-700 rounded-full h-2">
                    <div 
                      className="bg-blue-500 h-2 rounded-full transition-all duration-300"
                      style={{ width: `${Math.min((stats.totalProblemsSolved / 100) * 100, 100)}%` }}
                    ></div>
                  </div>
                </div>
                <div>
                  <div className="flex justify-between text-sm mb-1">
                    <span>Success Rate</span>
                    <span>{Math.round(stats.successRate * 100)}%</span>
                  </div>
                  <div className="w-full bg-gray-700 rounded-full h-2">
                    <div 
                      className={`h-2 rounded-full transition-all duration-300 ${getSuccessRateColor(stats.successRate).replace('text-', 'bg-')}`}
                      style={{ width: `${stats.successRate * 100}%` }}
                    ></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Recent Submissions */}
        <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
          <h3 className="text-xl font-semibold mb-4">📝 Recent Submissions</h3>
          
          {submissions.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-gray-700">
                    <th className="text-left py-3 px-4">Date</th>
                    <th className="text-left py-3 px-4">Problem</th>
                    <th className="text-left py-3 px-4">Language</th>
                    <th className="text-left py-3 px-4">Result</th>
                  </tr>
                </thead>
                <tbody>
                  {submissions.slice(0, 10).map((submission) => (
                    <tr key={submission.id} className="border-b border-gray-700/50">
                      <td className="py-3 px-4 text-gray-400 text-sm">
                        {new Date(submission.createdAt).toLocaleDateString()}
                      </td>
                      <td className="py-3 px-4">{submission.problemId}</td>
                      <td className="py-3 px-4 text-gray-400">{submission.language}</td>
                      <td className="py-3 px-4">
                        <span className={`px-2 py-1 rounded-full text-xs font-semibold ${getResultColor(submission.result)}`}>
                          {submission.result}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="text-center py-8">
              <div className="text-4xl mb-2">📝</div>
              <p className="text-gray-400">No submissions yet. Start solving problems!</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default UserStats;
