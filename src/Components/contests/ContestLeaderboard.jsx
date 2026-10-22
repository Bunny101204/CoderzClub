import { useCallback, useEffect, useState } from "react";
import { api, apiErrorMessage } from "../../apiClient";
import { useAuth } from "../../context/AuthContext";

const PAGE_SIZE = 20;
const LIVE_REFRESH_MS = 12000;

export default function ContestLeaderboard({ contestId, contestStatus }) {
  const { user } = useAuth();
  const [entries, setEntries] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async (silent = false) => {
    if (!contestId) {
      return;
    }
    if (!silent) {
      setLoading(true);
    } else {
      setRefreshing(true);
    }
    try {
      const response = await api.contests.getLeaderboard(contestId, { page, size: PAGE_SIZE });
      const data = response.data || {};
      setEntries(Array.isArray(data.entries) ? data.entries : []);
      setTotalPages(typeof data.totalPages === "number" ? data.totalPages : 0);
      setTotalItems(typeof data.totalItems === "number" ? data.totalItems : 0);
      setError("");
    } catch (err) {
      if (err?.response?.status === 404) {
        setError("");
        setEntries([]);
      } else {
        setError(apiErrorMessage(err, "Could not load standings"));
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [contestId, page]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (contestStatus !== "LIVE") {
      return undefined;
    }
    const timer = window.setInterval(() => load(true), LIVE_REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [contestStatus, load]);

  if (contestStatus !== "LIVE" && contestStatus !== "ENDED") {
    return null;
  }

  return (
    <section className="mt-8">
      <div className="flex items-center justify-between gap-3 mb-3">
        <h2 className="text-xl font-semibold">Standings</h2>
        <button
          type="button"
          className="text-sm text-blue-600 dark:text-blue-400 hover:underline"
          onClick={() => load(true)}
          disabled={refreshing}
        >
          {refreshing ? "Refreshing..." : "Refresh"}
        </button>
      </div>
      <p className="text-sm app-muted mb-4">
        Each problem awards its configured points once on first accepted submission.
        No time or wrong-attempt penalty.
        {contestStatus === "ENDED" ? " Late judged submissions admitted before the end may still change standings." : ""}
      </p>
      {error && <div className="text-sm text-red-600 dark:text-red-400 mb-3">{error}</div>}
      {loading ? (
        <p className="app-muted">Loading standings...</p>
      ) : entries.length === 0 ? (
        <p className="app-muted">No judged contest submissions yet.</p>
      ) : (
        <div className="app-surface rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left app-muted border-b border-gray-200 dark:border-gray-700">
                <th className="px-4 py-2 font-medium">#</th>
                <th className="px-4 py-2 font-medium">User</th>
                <th className="px-4 py-2 font-medium">Points</th>
                <th className="px-4 py-2 font-medium">Solved</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr
                  key={`${entry.rank}-${entry.username}`}
                  className={isCurrentUserRow(entry, user) ? "bg-blue-50 dark:bg-blue-950/40" : ""}
                >
                  <td className="px-4 py-2">{entry.rank}</td>
                  <td className="px-4 py-2 font-medium">{entry.username}</td>
                  <td className="px-4 py-2">{entry.totalPoints}</td>
                  <td className="px-4 py-2">{entry.solvedCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {totalPages > 1 && (
        <div className="flex items-center gap-3 mt-3 text-sm">
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => setPage((value) => Math.max(0, value - 1))}
            className="text-blue-600 dark:text-blue-400 disabled:opacity-40"
          >
            Previous
          </button>
          <span className="app-muted">
            Page {page + 1} of {totalPages} ({totalItems})
          </span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((value) => value + 1)}
            className="text-blue-600 dark:text-blue-400 disabled:opacity-40"
          >
            Next
          </button>
        </div>
      )}
    </section>
  );
}

function isCurrentUserRow(entry, user) {
  if (entry?.currentUser) {
    return true;
  }
  return Boolean(user?.username && entry?.username && user.username === entry.username);
}
