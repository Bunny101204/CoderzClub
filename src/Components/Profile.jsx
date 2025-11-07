import React, { useState, useEffect, useMemo } from 'react';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

const Profile = ({ isOpen, onClose, asPage = false }) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [stats, setStats] = useState(null);
  const [submissions, setSubmissions] = useState([]);
  const [problems, setProblems] = useState([]);
  const [loadingStats, setLoadingStats] = useState(true);
  const [statsError, setStatsError] = useState(null);

  useEffect(() => {
    if (!asPage && !isOpen) return;
    const token = localStorage.getItem('jwtToken') || localStorage.getItem('token');
    const headers = token ? { 'Authorization': `Bearer ${token}` } : {};
    const fetchAll = async () => {
      try {
        setLoadingStats(true);
        setStatsError(null);
        const [statsRes, subsRes, probsRes] = await Promise.all([
          axios.get('/api/users/stats', { headers }),
          axios.get('/api/submissions/my-submissions', { headers }),
          axios.get('/api/problems')
        ]);
        setStats(statsRes.data);
        setSubmissions(subsRes.data || []);
        setProblems(probsRes.data || []);
      } catch (e) {
        setStatsError('Failed to load stats');
      } finally {
        setLoadingStats(false);
      }
    };
    fetchAll();
  }, [isOpen, asPage]);

  const handleLogout = async () => {
    setIsLoggingOut(true);
    logout();
    if (onClose) onClose();
    navigate('/auth');
    setIsLoggingOut(false);
  };

  if (!asPage && !isOpen) return null;

  // Build maps and aggregates
  const problemMap = useMemo(() => {
    const map = new Map();
    (problems || []).forEach((p) => map.set(String(p.id), p));
    return map;
  }, [problems]);

  const difficultyCounts = useMemo(() => {
    const counts = { BASIC: 0, INTERMEDIATE: 0, ADVANCED: 0, SDE: 0, EXPERT: 0, UNKNOWN: 0 };
    const accepted = (submissions || []).filter((s) => s.result === 'ACCEPTED');
    accepted.forEach((s) => {
      const p = problemMap.get(String(s.problemId));
      const diff = (p?.difficulty || 'UNKNOWN');
      counts[diff] = (counts[diff] || 0) + 1;
    });
    return counts;
  }, [submissions, problemMap]);

  const activityByDay = useMemo(() => {
    const counts = new Map();
    (submissions || []).forEach((s) => {
      const d = new Date(s.createdAt);
      const key = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate())).toISOString().slice(0, 10);
      counts.set(key, (counts.get(key) || 0) + 1);
    });
    return counts;
  }, [submissions]);

  // Build last 180 days array
  const lastDays = useMemo(() => {
    const days = [];
    const today = new Date();
    for (let i = 179; i >= 0; i--) {
      const d = new Date(today);
      d.setDate(today.getDate() - i);
      const key = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate())).toISOString().slice(0, 10);
      days.push({ key, date: d, count: activityByDay.get(key) || 0 });
    }
    return days;
  }, [activityByDay]);

  const heatColor = (count) => {
    if (count === 0) return 'bg-gray-800';
    if (count === 1) return 'bg-green-900';
    if (count === 2) return 'bg-green-700';
    if (count <= 4) return 'bg-green-500';
    return 'bg-green-300';
  };

  const Wrapper = ({ children }) => (
    asPage ? (
      <div className="min-h-screen bg-gray-900 text-white p-6">
        {children}
      </div>
    ) : (
      <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
        {children}
      </div>
    )
  );

  return (
    <Wrapper>
      <div className={asPage ? 'max-w-7xl mx-auto' : 'w-full max-w-2xl mx-auto'}>
        <h2 className="text-2xl font-bold mb-6">Profile</h2>
        <div className="grid grid-cols-1 lg:grid-cols-10 gap-6">
          {/* Left - 30% */}
          <div className="lg:col-span-3 space-y-4">
            <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
              <div className="flex items-center space-x-3 mb-4">
                <div className="w-12 h-12 bg-gradient-to-r from-blue-500 to-purple-600 rounded-full flex items-center justify-center">
                  <span className="text-white font-bold text-lg">{user?.username?.charAt(0).toUpperCase()}</span>
                </div>
                <div>
                  <h3 className="text-white font-semibold">{user?.username}</h3>
                  <p className="text-gray-300 text-sm capitalize">{user?.role}</p>
                </div>
              </div>
              <div className="space-y-3 text-sm">
                <div className="flex justify-between py-2 border-b border-gray-700">
                  <span className="text-gray-300">Username</span>
                  <span className="text-white font-medium">{user?.username}</span>
                </div>
                <div className="flex justify-between py-2 border-b border-gray-700">
                  <span className="text-gray-300">Role</span>
                  <span className="text-white font-medium capitalize">{user?.role}</span>
                </div>
              </div>
              <div className="pt-4">
                <button
                  onClick={handleLogout}
                  disabled={isLoggingOut}
                  className="w-full bg-red-600 hover:bg-red-700 disabled:bg-red-800 text-white font-semibold py-3 px-4 rounded-lg transition-colors disabled:cursor-not-allowed"
                >
                  {isLoggingOut ? 'Logging Out...' : 'Logout'}
                </button>
              </div>
            </div>

            {/* Quick stats card */}
            <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
              <h3 className="text-white font-semibold mb-3">Overview</h3>
              {loadingStats ? (
                <div className="text-gray-400 text-sm">Loading stats...</div>
              ) : statsError ? (
                <div className="text-red-400 text-sm">{statsError}</div>
              ) : stats ? (
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div className="bg-gray-900/50 p-3 rounded border border-gray-700">
                    <div className="text-gray-400">Points</div>
                    <div className="text-green-400 text-xl font-bold">{stats.totalPoints}</div>
                  </div>
                  <div className="bg-gray-900/50 p-3 rounded border border-gray-700">
                    <div className="text-gray-400">Solved</div>
                    <div className="text-blue-400 text-xl font-bold">{stats.totalProblemsSolved}</div>
                  </div>
                  <div className="bg-gray-900/50 p-3 rounded border border-gray-700">
                    <div className="text-gray-400">Success</div>
                    <div className="text-yellow-400 text-xl font-bold">{Math.round((stats.successRate || 0) * 100)}%</div>
                  </div>
                  <div className="bg-gray-900/50 p-3 rounded border border-gray-700">
                    <div className="text-gray-400">Streak</div>
                    <div className="text-orange-400 text-xl font-bold">{stats.currentStreak}</div>
                  </div>
                </div>
              ) : (
                <div className="text-gray-400 text-sm">No stats available.</div>
              )}
            </div>
          </div>

          {/* Right - 70% */}
          <div className="lg:col-span-7 space-y-6">
            {/* Total Solved visual */}
            <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
              <div className="flex items-center justify-between">
                <h3 className="text-white font-semibold">Problems Solved</h3>
                <div className="text-4xl font-extrabold text-blue-400">{stats?.totalProblemsSolved ?? 0}</div>
              </div>
            </div>

            {/* Difficulty distribution */}
            <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
              <h3 className="text-white font-semibold mb-4">Solved by Difficulty</h3>
              <div className="space-y-3">
                {['BASIC','INTERMEDIATE','ADVANCED','SDE','EXPERT'].map((d) => (
                  <div key={d} className="flex items-center gap-3">
                    <div className="w-32 text-sm text-gray-300">{d}</div>
                    <div className="flex-1 bg-gray-900 rounded h-3 overflow-hidden">
                      <div
                        className={`h-3 ${d==='BASIC'?'bg-green-500':d==='INTERMEDIATE'?'bg-yellow-500':d==='ADVANCED'?'bg-orange-500':d==='SDE'?'bg-red-500':'bg-purple-500'}`}
                        style={{ width: `${Math.min((difficultyCounts[d] || 0) / Math.max(1, stats?.totalProblemsSolved || 0) * 100, 100)}%` }}
                      />
                    </div>
                    <div className="w-10 text-right text-sm text-gray-300">{difficultyCounts[d] || 0}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* Activity heatmap */}
            <div className="bg-gray-800 rounded-2xl p-6 border border-gray-700">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-white font-semibold">Active Days (Last 6 months)</h3>
                <div className="text-sm text-gray-400">A day is active if it has ≥1 submission</div>
              </div>
              <div className="grid grid-cols-30 gap-1" style={{ gridTemplateColumns: 'repeat(30, minmax(0, 1fr))' }}>
                {lastDays.map((d, idx) => (
                  <div key={idx} className={`w-4 h-4 ${heatColor(d.count)} rounded`}
                    title={`${d.key}: ${d.count} submission(s)`} />
                ))}
              </div>
              <div className="flex items-center gap-2 mt-3 text-xs text-gray-400">
                <span>Less</span>
                <span className="w-4 h-4 bg-gray-800 rounded inline-block"></span>
                <span className="w-4 h-4 bg-green-900 rounded inline-block"></span>
                <span className="w-4 h-4 bg-green-700 rounded inline-block"></span>
                <span className="w-4 h-4 bg-green-500 rounded inline-block"></span>
                <span className="w-4 h-4 bg-green-300 rounded inline-block"></span>
                <span>More</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Wrapper>
  );
};

export default Profile; 