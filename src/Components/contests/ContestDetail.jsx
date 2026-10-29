import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, apiErrorMessage } from "../../apiClient";
import { displayProblemId } from "../../admin/adminProblems";
import { createBoundaryRefresh } from "../../contests/contestBoundary";
import { formatLocalDateTime } from "../../contests/contestTime";
import ContestCountdown from "./ContestCountdown";
import ContestLeaderboard from "./ContestLeaderboard";
import ContestStatusBadge from "./ContestStatusBadge";

export default function ContestDetail() {
  const { id } = useParams();
  const [contest, setContest] = useState(null);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);
  const [error, setError] = useState("");
  const [boundaryPending, setBoundaryPending] = useState(false);
  const [boundaryExhausted, setBoundaryExhausted] = useState(false);
  const boundaryRef = useRef(null);
  const contestStatusRef = useRef(null);
  contestStatusRef.current = contest?.status || null;

  const load = useCallback(async (silent = false) => {
    if (!silent) {
      setLoading(true);
    }
    try {
      const response = await api.contests.get(id);
      setContest(response.data);
      setUnavailable(false);
      setError("");
    } catch (err) {
      if (err?.response?.status === 404) {
        setUnavailable(true);
        setContest(null);
        setError("");
      } else {
        setError(apiErrorMessage(err, "Could not load contest"));
      }
    } finally {
      if (!silent) {
        setLoading(false);
      }
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    let disposed = false;
    const refresh = createBoundaryRefresh({
      fetchContest: async () => {
        const response = await api.contests.get(id);
        return response.data;
      },
      onContest: (next) => {
        if (disposed || !next) return;
        setContest(next);
        setUnavailable(false);
        setError("");
      },
      onPendingChange: (pending) => {
        if (!disposed) setBoundaryPending(pending);
      },
      onExhausted: () => {
        if (!disposed) setBoundaryExhausted(true);
      },
      onError: (err) => {
        if (disposed) return;
        if (err) setError(apiErrorMessage(err, "Could not refresh contest status"));
      },
      setTimeout: (fn, delay) => window.setTimeout(fn, delay),
      clearTimeout: (timer) => window.clearTimeout(timer),
    });
    boundaryRef.current = refresh;
    return () => {
      disposed = true;
      refresh.dispose();
      boundaryRef.current = null;
    };
  }, [id]);

  const onBoundary = useCallback(() => {
    setBoundaryExhausted(false);
    boundaryRef.current?.start(contestStatusRef.current);
  }, []);

  if (loading) {
    return <div className="app-shell p-8"><div className="max-w-4xl mx-auto app-muted">Loading contest...</div></div>;
  }

  if (unavailable) {
    return (
      <div className="app-shell p-8">
        <div className="max-w-4xl mx-auto text-center">
          <h1 className="text-2xl font-bold mb-3">Contest not available</h1>
          <p className="app-muted mb-4">This contest is not available to you.</p>
          <Link to="/contests" className="text-blue-600 dark:text-blue-400 hover:underline">Back to contests</Link>
        </div>
      </div>
    );
  }

  if (!contest) {
    return (
      <div className="app-shell p-8">
        <div className="max-w-4xl mx-auto">
          {error && <div className="text-red-600 dark:text-red-400 mb-4">{error}</div>}
          <Link to="/contests" className="text-blue-600 dark:text-blue-400 hover:underline">Back to contests</Link>
        </div>
      </div>
    );
  }

  const problems = Array.isArray(contest.problems) ? contest.problems : [];
  const showProblems = contest.status === "LIVE" || contest.status === "ENDED";

  return (
    <div className="app-shell p-8">
      <div className="max-w-4xl mx-auto">
        <Link to="/contests" className="text-blue-600 dark:text-blue-400 hover:underline text-sm">Back to contests</Link>
        <div className="flex items-start justify-between gap-4 mt-3 mb-4">
          <h1 className="text-3xl font-bold">{contest.title}</h1>
          <ContestStatusBadge status={contest.status} />
        </div>
        {error && <div className="mb-4 text-sm text-red-600 dark:text-red-400">{error}</div>}
        {contest.description && <p className="whitespace-pre-wrap mb-4">{contest.description}</p>}
        <div className="app-surface rounded-lg p-4 mb-6 space-y-2 text-sm">
          <div>Start: {formatLocalDateTime(contest.startAt)}</div>
          <div>End: {formatLocalDateTime(contest.endAt)}</div>
          <div>Problems: {contest.problemCount ?? problems.length}</div>
          <div>{contest.audienceType === "ALL_USERS" ? "Open to all users" : "Assigned batch"}</div>
          <ContestCountdown
            contest={contest}
            onBoundary={onBoundary}
            pending={boundaryPending}
            exhausted={boundaryExhausted}
          />
          {boundaryExhausted && (
            <button
              type="button"
              className="text-sm text-blue-600 dark:text-blue-400 hover:underline"
              onClick={onBoundary}
            >
              Check again
            </button>
          )}
        </div>
        {contest.status === "UPCOMING" && (
          <p className="app-muted">Problem details are hidden until the contest starts. The server decides when it is LIVE.</p>
        )}
        {showProblems && problems.length === 0 && (
          <p className="app-muted">No problems to show.</p>
        )}
        {showProblems && problems.length > 0 && (
          <ol className="app-surface rounded-lg divide-y divide-gray-200 dark:divide-gray-700">
            {problems.map((problem, index) => (
              <li key={problem.id || index}>
                <Link
                  to={`/contests/${contest.id}/problems/${problem.id}`}
                  className="flex items-center gap-3 px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-800"
                >
                  <span className="app-muted w-6">{index + 1}</span>
                  <span className="font-mono text-sm w-12">{displayProblemId(problem)}</span>
                  <span className="flex-1">{problem.title || "Problem"}</span>
                  <span className="text-sm app-muted">{problem.difficulty || ""}</span>
                  {problem.points ? <span className="text-sm app-muted">{problem.points} pts</span> : null}
                </Link>
              </li>
            ))}
          </ol>
        )}
        <ContestLeaderboard contestId={contest.id} contestStatus={contest.status} />
      </div>
    </div>
  );
}
