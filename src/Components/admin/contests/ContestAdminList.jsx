import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../../apiClient";
import { canDelete, canPublish, canUnpublish } from "../../../contests/contestLifecycle";
import { formatLocalDateTime } from "../../../contests/contestTime";
import ContestStatusBadge from "../../contests/ContestStatusBadge";

const PAGE_SIZE = 20;

export default function ContestAdminList() {
  const [page, setPage] = useState(0);
  const [contests, setContests] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actingId, setActingId] = useState("");

  const load = useCallback(async (nextPage) => {
    setLoading(true);
    try {
      const response = await api.adminContests.list({ page: nextPage, size: PAGE_SIZE });
      const data = response.data || {};
      setContests(Array.isArray(data.contests) ? data.contests : []);
      setTotalPages(data.totalPages || 0);
      setTotalItems(data.totalItems || 0);
      setPage(typeof data.currentPage === "number" ? data.currentPage : nextPage);
      setError("");
    } catch (err) {
      setError(apiErrorMessage(err, "Could not load contests"));
      setContests([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(page);
  }, [load, page]);

  const act = async (contest, kind) => {
    if (actingId) {
      return;
    }
    if (kind === "delete" && !window.confirm(`Delete “${contest.title}”? This cannot be undone.`)) {
      return;
    }
    setActingId(contest.id);
    setError("");
    try {
      if (kind === "publish") {
        await api.adminContests.publish(contest.id);
      } else if (kind === "unpublish") {
        await api.adminContests.unpublish(contest.id);
      } else if (kind === "delete") {
        await api.adminContests.delete(contest.id);
      }
      await load(page);
    } catch (err) {
      const fallback = kind === "publish"
        ? "Could not publish this contest"
        : kind === "unpublish"
          ? "Could not unpublish this contest"
          : "Action failed";
      setError(apiErrorMessage(err, fallback));
    } finally {
      setActingId("");
    }
  };

  return (
    <>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-4 gap-4">
        <div>
          <h2 className="text-xl font-semibold">Contests</h2>
          <div className="text-sm app-muted">Page {page + 1} of {totalPages || 1} · {totalItems} total</div>
        </div>
        <Link to="/admin/contests/new" className="bg-green-600 hover:bg-green-700 text-white font-semibold py-2 px-6 rounded-lg text-center">
          + Create contest
        </Link>
      </div>
      {error && <div className="mb-4 text-sm text-red-600 dark:text-red-400" role="alert">{error}</div>}
      <div className="app-surface rounded-lg shadow p-4 overflow-x-auto">
        {loading && contests.length === 0 ? (
          <div className="app-muted">Loading contests...</div>
        ) : contests.length === 0 ? (
          <div className="app-muted">No contests yet. Unpublished drafts appear in this list after they are saved.</div>
        ) : (
          <table className="w-full text-left">
            <thead>
              <tr>
                <th className="py-2 px-3">Title</th>
                <th className="py-2 px-3">Status</th>
                <th className="py-2 px-3">Audience</th>
                <th className="py-2 px-3">Start</th>
                <th className="py-2 px-3">End</th>
                <th className="py-2 px-3">Published</th>
                <th className="py-2 px-3">Problems</th>
                <th className="py-2 px-3">Actions</th>
              </tr>
            </thead>
            <tbody>
              {contests.map((contest) => (
                <tr key={contest.id} className="border-t border-gray-200 dark:border-gray-700">
                  <td className="py-2 px-3">{contest.title}</td>
                  <td className="py-2 px-3"><ContestStatusBadge status={contest.status} /></td>
                  <td className="py-2 px-3">{contest.audienceType === "ALL_USERS" ? "All users" : "Batches"}</td>
                  <td className="py-2 px-3 whitespace-nowrap">{formatLocalDateTime(contest.startAt)}</td>
                  <td className="py-2 px-3 whitespace-nowrap">{formatLocalDateTime(contest.endAt)}</td>
                  <td className="py-2 px-3">{contest.published ? "Yes" : "No"}</td>
                  <td className="py-2 px-3">{contest.problemCount ?? 0}</td>
                  <td className="py-2 px-3 space-x-3 whitespace-nowrap">
                    <Link to={`/admin/contests/${contest.id}`} className="text-blue-600 dark:text-blue-400 hover:underline">Open</Link>
                    {canPublish(contest.status, contest.published) && (
                      <button type="button" className="text-blue-600 dark:text-blue-400 hover:underline disabled:opacity-50" disabled={!!actingId} onClick={() => act(contest, "publish")}>Publish</button>
                    )}
                    {canUnpublish(contest.status) && (
                      <button type="button" className="text-blue-600 dark:text-blue-400 hover:underline disabled:opacity-50" disabled={!!actingId} onClick={() => act(contest, "unpublish")}>Unpublish</button>
                    )}
                    {canDelete(contest.status) && (
                      <button type="button" className="text-red-600 dark:text-red-400 hover:underline disabled:opacity-50" disabled={!!actingId} onClick={() => act(contest, "delete")}>Delete</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {totalPages > 1 && (
        <div className="flex gap-2 mt-4">
          <button type="button" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="px-3 py-1 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white disabled:opacity-50">Previous</button>
          <button type="button" onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1} className="px-3 py-1 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white disabled:opacity-50">Next</button>
        </div>
      )}
    </>
  );
}
