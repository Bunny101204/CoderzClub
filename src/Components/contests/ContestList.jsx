import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../apiClient";
import { formatLocalDateTime } from "../../contests/contestTime";
import ContestCountdown from "./ContestCountdown";
import ContestStatusBadge from "./ContestStatusBadge";

const PAGE_SIZE = 20;
const SECTIONS = ["LIVE", "UPCOMING", "ENDED"];

function ContestCard({ contest, onBoundary }) {
  const description = contest.description ? String(contest.description).trim() : "";
  const short = description.length > 160 ? `${description.slice(0, 157)}…` : description;
  return (
    <Link to={`/contests/${contest.id}`} className="block app-surface rounded-lg p-4 hover:ring-2 hover:ring-blue-500">
      <div className="flex items-start justify-between gap-3 mb-2">
        <h3 className="text-lg font-semibold">{contest.title}</h3>
        <ContestStatusBadge status={contest.status} />
      </div>
      {short && <p className="text-sm app-muted mb-3 whitespace-pre-wrap">{short}</p>}
      <div className="text-sm space-y-1">
        <div>Start: {formatLocalDateTime(contest.startAt)}</div>
        <div>End: {formatLocalDateTime(contest.endAt)}</div>
        <div>Problems: {contest.problemCount ?? 0}</div>
        <div>{contest.audienceType === "ALL_USERS" ? "Open to all users" : "Assigned batch"}</div>
        <ContestCountdown contest={contest} onBoundary={onBoundary} />
      </div>
    </Link>
  );
}

export default function ContestList() {
  const [page, setPage] = useState(0);
  const [contests, setContests] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async (nextPage, silent = false) => {
    if (!silent) {
      setLoading(true);
    }
    try {
      const response = await api.contests.list({ page: nextPage, size: PAGE_SIZE });
      const data = response.data || {};
      setContests(Array.isArray(data.contests) ? data.contests : []);
      setTotalPages(data.totalPages || 0);
      setPage(typeof data.currentPage === "number" ? data.currentPage : nextPage);
      setError("");
    } catch (err) {
      setError(apiErrorMessage(err, "Could not load contests"));
      setContests([]);
    } finally {
      if (!silent) {
        setLoading(false);
      }
    }
  }, []);

  const pageRef = useRef(page);
  pageRef.current = page;
  const boundaryRefresh = useRef(false);

  useEffect(() => {
    load(page);
  }, [load, page]);

  const onBoundary = useCallback(() => {
    if (boundaryRefresh.current) {
      return;
    }
    boundaryRefresh.current = true;
    load(pageRef.current, true).finally(() => {
      boundaryRefresh.current = false;
    });
  }, [load]);

  const grouped = SECTIONS.map((status) => ({
    status,
    items: contests.filter((contest) => contest.status === status),
  })).filter((group) => group.items.length > 0);

  return (
    <div className="app-shell p-8">
      <div className="max-w-5xl mx-auto">
        <h1 className="text-3xl font-bold mb-6">Contests</h1>
        {error && <div className="mb-4 text-sm text-red-600 dark:text-red-400" role="alert">{error}</div>}
        {loading && contests.length === 0 ? (
          <div className="app-muted">Loading contests...</div>
        ) : contests.length === 0 ? (
          <div className="app-muted">No contests available.</div>
        ) : (
          <div className="space-y-8">
            {grouped.map((group) => (
              <section key={group.status}>
                <h2 className="text-xl font-semibold mb-3">{group.status}</h2>
                <div className="grid gap-4 sm:grid-cols-2">
                  {group.items.map((contest) => (
                    <ContestCard key={contest.id} contest={contest} onBoundary={onBoundary} />
                  ))}
                </div>
              </section>
            ))}
          </div>
        )}
        {totalPages > 1 && (
          <div className="flex gap-2 mt-6">
            <button type="button" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="px-3 py-1 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white disabled:opacity-50">Previous</button>
            <button type="button" onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1} className="px-3 py-1 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white disabled:opacity-50">Next</button>
          </div>
        )}
      </div>
    </div>
  );
}
