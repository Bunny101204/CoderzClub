import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, apiErrorMessage } from "../../apiClient";
import { ProblemWorkspace } from "../ProblemPageNew";
import ContestStatusBadge from "./ContestStatusBadge";

export default function ContestProblemPage() {
  const { contestId, problemId } = useParams();
  const [contest, setContest] = useState(null);
  const [problem, setProblem] = useState(null);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setUnavailable(false);
    setError("");
    (async () => {
      try {
        const contestResponse = await api.contests.get(contestId);
        if (cancelled) {
          return;
        }
        const nextContest = contestResponse.data;
        setContest(nextContest);
        if (nextContest.status !== "LIVE" && nextContest.status !== "ENDED") {
          setProblem(null);
          return;
        }
        const allowed = (nextContest.problems || []).some((item) => String(item.id) === String(problemId));
        if (!allowed) {
          setUnavailable(true);
          setProblem(null);
          return;
        }
        const problemResponse = await api.contests.getProblem(contestId, problemId);
        if (cancelled) {
          return;
        }
        const data = problemResponse.data || {};
        setProblem({
          ...data,
          isPremium: Boolean(data.isPremium ?? data.premium),
        });
      } catch (err) {
        if (cancelled) {
          return;
        }
        if (err?.response?.status === 404) {
          setUnavailable(true);
          setContest(null);
          setProblem(null);
        } else {
          setError(apiErrorMessage(err, "Could not load contest problem"));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [contestId, problemId]);

  if (loading) {
    return <div className="app-shell flex items-center justify-center"><div className="text-xl">Loading contest problem...</div></div>;
  }

  if (unavailable) {
    return (
      <div className="app-shell flex items-center justify-center">
        <div className="text-center">
          <h1 className="text-2xl font-bold mb-3">Contest not available</h1>
          <p className="app-muted mb-4">This contest is not available to you.</p>
          <Link to="/contests" className="text-blue-600 dark:text-blue-400 hover:underline">Back to contests</Link>
        </div>
      </div>
    );
  }

  if (contest && contest.status !== "LIVE" && contest.status !== "ENDED") {
    return (
      <div className="app-shell p-8">
        <div className="max-w-3xl mx-auto">
          <Link to={`/contests/${contestId}`} className="text-blue-600 dark:text-blue-400 hover:underline text-sm">Back to contest</Link>
          <h1 className="text-2xl font-bold mt-3 mb-2">{contest.title}</h1>
          <p className="app-muted">Problem details stay hidden until the contest is LIVE.</p>
        </div>
      </div>
    );
  }

  if (!problem) {
    return (
      <div className="app-shell p-8">
        <div className="max-w-3xl mx-auto">
          {error && <div className="text-red-600 dark:text-red-400 mb-4">{error}</div>}
          <Link to={`/contests/${contestId}`} className="text-blue-600 dark:text-blue-400 hover:underline">Back to contest</Link>
        </div>
      </div>
    );
  }

  return (
    <ProblemWorkspace
      problem={problem}
      backTo={`/contests/${contestId}`}
      backLabel="Back to contest"
      contestId={contest?.id || contestId}
      contestStatus={contest?.status}
      showSubmissions={false}
      banner={(
        <div className="mb-4 app-inset rounded-lg px-3 py-2 flex items-center justify-between gap-3">
          <div>
            <div className="text-sm app-muted">Contest</div>
            <div className="font-semibold">{contest?.title}</div>
          </div>
          <ContestStatusBadge status={contest?.status} />
        </div>
      )}
    />
  );
}
