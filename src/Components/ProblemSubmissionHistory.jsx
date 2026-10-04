import { useEffect, useState } from "react";
import { formatCodingDuration } from "../execution/codingDuration.js";

function formatMemory(bytes) {
  if (bytes == null) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

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
  const [selected, setSelected] = useState(null);

  useEffect(() => {
    setPage(0);
    setSubmissions([]);
    setSelected(null);
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
        const response = await fetch(`/api/submissions/my-submissions?${params}`, {
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
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead>
              <tr className="app-muted">
                <th className="py-2 pr-3">When</th>
                <th className="py-2 pr-3">Language</th>
                <th className="py-2 pr-3">Verdict</th>
                <th className="py-2 pr-3">Runtime</th>
                <th className="py-2 pr-3">Coding time</th>
                <th className="py-2 pr-3">Memory</th>
              </tr>
            </thead>
            <tbody>
              {submissions.map((submission) => {
                const verdict = submission.result || submission.verdict || "UNKNOWN";
                return (
                  <tr
                    key={submission.id}
                    className="border-t border-gray-200 dark:border-gray-700 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-900/60"
                    onClick={() => setSelected(submission)}
                  >
                    <td className="py-2 pr-3">
                      {submission.createdAt ? new Date(submission.createdAt).toLocaleString() : "—"}
                    </td>
                    <td className="py-2 pr-3">{submission.language || "—"}</td>
                    <td className={`py-2 pr-3 font-semibold ${verdictClass(verdict)}`}>{verdict}</td>
                    <td className="py-2 pr-3">{submission.runtime != null ? `${submission.runtime} ms` : "—"}</td>
                    <td className="py-2 pr-3">{formatCodingDuration(submission.codingDurationSeconds)}</td>
                    <td className="py-2 pr-3">{formatMemory(submission.memory)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
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
      {selected && (
        <div className="mt-4 app-inset rounded-lg p-4">
          <div className="flex items-center justify-between mb-2">
            <h3 className="font-semibold">Submitted code (read-only)</h3>
            <button type="button" className="text-sm app-muted" onClick={() => setSelected(null)}>
              Close
            </button>
          </div>
          <pre className="text-xs overflow-auto max-h-80 whitespace-pre-wrap font-mono app-code rounded p-3">
            {selected.code || ""}
          </pre>
        </div>
      )}
    </div>
  );
};

export default ProblemSubmissionHistory;
