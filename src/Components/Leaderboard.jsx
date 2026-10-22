import React, { useState, useEffect } from "react";
import { useAuth } from "../context/AuthContext";
import { api } from "../apiClient";

const PAGE_SIZE = 20;

const Leaderboard = () => {
  const { isAuthenticated, user } = useAuth();
  const [entries, setEntries] = useState([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [size, setSize] = useState(PAGE_SIZE);
  const [viewer, setViewer] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const load = async (silent, pageToLoad) => {
    try {
      if (!silent) setLoading(true);
      setIsRefreshing(true);
      const response = await api.users.getLeaderboard({ page: pageToLoad, size: PAGE_SIZE });
      const data = response.data || {};
      const users = Array.isArray(data.users) ? data.users : [];
      setEntries(users);
      setTotal(typeof data.total === "number" ? data.total : users.length);
      setSize(typeof data.size === "number" ? data.size : PAGE_SIZE);
      setViewer(data.viewer || null);
      setError(null);
    } catch {
      setError("Leaderboard is unavailable right now. Try again in a moment.");
      if (!silent) setEntries([]);
    } finally {
      if (!silent) setLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    load(false, page);
    const handleRefresh = () => load(true, page);
    window.addEventListener("leaderboardRefresh", handleRefresh);
    return () => window.removeEventListener("leaderboardRefresh", handleRefresh);
  }, [page]);

  const totalPages = Math.max(1, Math.ceil((total || 0) / (size || PAGE_SIZE)));
  const podium = page === 0 ? entries.slice(0, 3) : [];

  const isCurrentUser = (entry) =>
    Boolean(user?.username && entry?.username && user.username === entry.username);

  if (loading) {
    return (
      <div className="app-shell p-6">
        <div className="max-w-5xl mx-auto">
          <h1 className="text-3xl font-bold mb-2">Leaderboard</h1>
          <p className="app-muted mb-8">Ranked by total points from first accepted solves.</p>
          <p role="status">Loading rankings…</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="app-shell p-6">
        <div className="max-w-5xl mx-auto">
          <h1 className="text-3xl font-bold mb-4">Leaderboard</h1>
          <p className="mb-4" role="alert">{error}</p>
          <button
            type="button"
            onClick={() => load(false, page)}
            className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg focus:outline-none"
          >
            Try again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="app-shell p-6 md:p-8">
      <div className="max-w-5xl mx-auto">
        <header className="mb-8">
          <h1 className="text-3xl md:text-4xl font-bold mb-2">Leaderboard</h1>
          <p className="app-muted">
            Ranked by total points. Points are awarded once per problem on the first accepted solve.
          </p>
        </header>

        {isAuthenticated && viewer && (
          <section aria-label="Your rank" className="app-surface rounded-xl p-4 mb-6">
            <h2 className="text-sm font-semibold uppercase tracking-wide app-muted mb-1">Your rank</h2>
            <p>
              {viewer.rank
                ? `${viewer.username} is rank ${viewer.rank} with ${viewer.totalPoints} points`
                : `${viewer.username} does not have a rank yet`}
              {viewer.onPage ? " and is on this page." : "."}
            </p>
          </section>
        )}

        {isAuthenticated && !viewer && (
          <p className="app-muted mb-6">Sign in to see your rank on this board.</p>
        )}

        {podium.length > 0 && (
          <section aria-label="Top three" className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
            {podium.map((entry) => (
              <article
                key={entry.id}
                className={`app-surface rounded-xl p-4 ${isCurrentUser(entry) ? "ring-2 ring-blue-500" : ""}`}
              >
                <p className="text-sm font-medium app-muted">Rank {entry.rank}</p>
                <h2 className="text-xl font-bold truncate">{entry.username}</h2>
                <p>{entry.totalPoints} points</p>
                <p className="text-sm app-muted">{entry.problemsSolved || 0} solved</p>
              </article>
            ))}
          </section>
        )}

        <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
          <p className="app-muted text-sm">{total} participants</p>
          <button
            type="button"
            onClick={() => load(false, page)}
            disabled={isRefreshing}
            className="px-3 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white disabled:opacity-50"
          >
            {isRefreshing ? "Refreshing" : "Refresh"}
          </button>
        </div>

        {entries.length === 0 ? (
          <div className="app-surface rounded-xl p-8 text-center">
            <h2 className="text-xl font-semibold mb-2">No rankings yet</h2>
            <p className="app-muted">Solve a problem to appear on the leaderboard.</p>
          </div>
        ) : (
          <div className="app-surface rounded-xl overflow-hidden">
            <table className="hidden md:table w-full text-left">
              <thead className="border-b border-gray-200 dark:border-gray-700">
                <tr>
                  <th scope="col" className="px-4 py-3">Rank</th>
                  <th scope="col" className="px-4 py-3">Coder</th>
                  <th scope="col" className="px-4 py-3">Solved</th>
                  <th scope="col" className="px-4 py-3">Points</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => (
                  <tr
                    key={entry.id}
                    className={`border-t border-gray-200 dark:border-gray-700 ${
                      isCurrentUser(entry) ? "bg-blue-50 dark:bg-blue-950/40" : ""
                    }`}
                  >
                    <td className="px-4 py-3 font-medium">{entry.rank}</td>
                    <td className="px-4 py-3">
                      {entry.username}
                      {isCurrentUser(entry) ? <span className="sr-only"> (you)</span> : null}
                    </td>
                    <td className="px-4 py-3">{entry.problemsSolved || 0}</td>
                    <td className="px-4 py-3 font-semibold">{entry.totalPoints}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <ul className="md:hidden divide-y divide-gray-200 dark:divide-gray-700">
              {entries.map((entry) => (
                <li
                  key={entry.id}
                  className={`p-4 ${isCurrentUser(entry) ? "bg-blue-50 dark:bg-blue-950/40" : ""}`}
                >
                  <p className="text-sm app-muted">Rank {entry.rank}</p>
                  <p className="font-semibold">{entry.username}{isCurrentUser(entry) ? " (you)" : ""}</p>
                  <p>{entry.totalPoints} points · {entry.problemsSolved || 0} solved</p>
                </li>
              ))}
            </ul>
          </div>
        )}

        {totalPages > 1 && (
          <nav className="flex justify-center gap-2 mt-6" aria-label="Leaderboard pages">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1 rounded app-surface disabled:opacity-50"
            >
              Previous
            </button>
            <p className="px-3 py-1">Page {page + 1} of {totalPages}</p>
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="px-3 py-1 rounded app-surface disabled:opacity-50"
            >
              Next
            </button>
          </nav>
        )}
      </div>
    </div>
  );
};

export default Leaderboard;
