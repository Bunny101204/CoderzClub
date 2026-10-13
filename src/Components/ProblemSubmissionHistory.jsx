import { useEffect, useState } from "react";
import { apiFetch } from "../apiClient";
import { formatCodingDuration } from "../execution/codingDuration.js";
import {
  formatSubmissionMemory,
  formatSubmissionRuntime,
  formatSubmissionTimestamp,
} from "../execution/submissionMetrics.js";

function verdictClass(verdict) {
  if (verdict === "ACCEPTED") return "text-green-700 dark:text-green-400";
  if (verdict === "INTERNAL_ERROR") return "text-orange-600 dark:text-orange-400";
  return "text-yellow-700 dark:text-yellow-300";
}

const ProblemSubmissionHistory = ({ problemId, numericId, enabled = true, refreshKey = 0 }) => {
  const [submissions, setSubmissions] = useState([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [expandedId, setExpandedId] = useState(null);

  useEffect(() => {
    setPage(0);
    setSubmissions([]);
    setExpandedId(null);
  }, [problemId, refreshKey]);

  useEffect(() => {
    if (!enabled || !problemId) return;
    const token = localStorage.getItem("jwtToken") || localStorage.getItem("token");
    if (!token) return;
    const controller = new AbortController();
    const load = async () => {
      setLoading(true);
      setError("");
      try {
        const params = new URLSearchParams({ page: String(page), size: "20", problemId });
        const response = await apiFetch(`/api/submissions/my-submissions?${params}`, {
          headers: { Authorization: `Bearer ${token}` },
          signal: controller.signal,
        });
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        const data = await response.json();
        const rows = Array.isArray(data.submissions) ? data.submissions : [];
        setSubmissions((current) => page === 0 ? rows : [...current, ...rows]);
        setHasNext(Boolean(data.hasNext));
      } catch (err) {
        if (err.name !== "AbortError") {
          setError("Could not load submission history.");
        }
      } finally {
        setLoading(false);
      }
    };
    load();
    return () => controller.abort();
  }, [problemId, numericId, page, enabled, refreshKey]);

  if (!problemId) return null;

  return (
    <div>
      <h2 className="text-xl font-bold mb-3">Your submissions</h2>
      {error && <div className="text-red-600 dark:text-red-400 text-sm mb-3">{error}</div>}
      {loading && submissions.length === 0 && (
        <div className="app-muted text-sm">Loading history...</div>
      )}
      {!loading && submissions.length === 0 && !error && (
        <div className="app-muted text-sm">No submissions yet.</div>
      )}
      {submissions.length > 0 && (
        <div className="text-sm">
          {submissions.map((submission) => {
            const verdict = submission.result || submission.verdict || "UNKNOWN";
            const expanded = expandedId === submission.id;
            const codePanelId = `submission-code-${submission.id}`;
            return (
              <article key={submission.id} className="border-t border-gray-200 dark:border-gray-700">
                <button
                  type="button"
                  className="block w-full text-left py-2 hover:bg-gray-100 dark:hover:bg-gray-900/60"
                  aria-expanded={expanded}
                  aria-controls={codePanelId}
                  onClick={() => setExpandedId((current) => current === submission.id ? null : submission.id)}
                >
                  <div className={`font-semibold ${verdictClass(verdict)}`}>{verdict}</div>
                  <div>{submission.language || "—"}</div>
                  <div>Runtime: {formatSubmissionRuntime(submission.runtime)}</div>
                  <div>Memory: {formatSubmissionMemory(submission.memory)}</div>
                  <div>Submitted: {formatSubmissionTimestamp(submission.createdAt)}</div>
                  <div className="app-muted">
                    Coding time: {submission.codingDurationSeconds == null
                      ? "unavailable"
                      : formatCodingDuration(submission.codingDurationSeconds)}
                  </div>
                </button>
                {expanded && (
                  <div id={codePanelId} className="app-inset rounded-lg p-4 mb-3">
                    <h3 className="font-semibold mb-2">Submitted code (read-only)</h3>
                    <pre className="text-xs overflow-auto max-h-80 whitespace-pre-wrap font-mono app-code rounded p-3">
                      {submission.code || ""}
                    </pre>
                  </div>
                )}
              </article>
            );
          })}
        </div>
      )}
      {hasNext && (
        <button
          type="button"
          className="mt-3 text-blue-600 dark:text-blue-400 hover:underline text-sm"
          onClick={() => setPage((current) => current + 1)}
          disabled={loading}
        >
          {loading ? "Loading..." : "Load more"}
        </button>
      )}
    </div>
  );
};

export default ProblemSubmissionHistory;
