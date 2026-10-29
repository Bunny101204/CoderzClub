import React, { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { apiFetch } from "../apiClient";
import { practiceProblemLabel, practiceProblemPath } from "../batches/myBatches";

function authHeaders() {
  const token = localStorage.getItem("jwtToken") || localStorage.getItem("token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

const StudentBatchPage = () => {
  const { id } = useParams();
  const [batch, setBatch] = useState(null);
  const [state, setState] = useState("loading");
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    const load = async (page = 0) => {
      const response = await apiFetch(`/api/my/batches/${id}/practice?page=${page}&size=20`, { headers: authHeaders() });
      if (cancelled) return;
      if (response.status === 404) {
        setState("missing");
        setBatch(null);
        return;
      }
      if (!response.ok) {
        setState("error");
        setError(response.status === 401 ? "Sign in again to view this batch." : "Could not load this batch.");
        return;
      }
      setBatch(await response.json());
      setState("ready");
    };
    load().catch(() => {
      if (!cancelled) {
        setState("error");
        setError("Could not load this batch.");
      }
    });
    return () => {
      cancelled = true;
    };
  }, [id]);

  const changePage = async (page) => {
    setState("loading");
    try {
      const response = await apiFetch(`/api/my/batches/${id}/practice?page=${page}&size=20`, { headers: authHeaders() });
      if (!response.ok) {
        setState("error");
        setError("Could not load this batch.");
        return;
      }
      setBatch(await response.json());
      setState("ready");
    } catch {
      setState("error");
      setError("Could not load this batch.");
    }
  };

  return (
    <div className="app-shell p-4 sm:p-8">
      <div className="max-w-3xl mx-auto">
        <Link to="/my/batches" className="text-blue-600 dark:text-blue-400 hover:underline text-sm">Back to my batches</Link>
        {state === "loading" && <p className="mt-4">Loading batch...</p>}
        {state === "error" && <p className="mt-4 text-red-600 dark:text-red-400" role="alert">{error}</p>}
        {state === "missing" && <p className="mt-4 app-surface rounded p-4">This batch is not available to you.</p>}
        {state === "ready" && batch && (
          <>
            <h1 className="text-3xl font-bold mt-3 mb-2">{batch.name}</h1>
            <p className="app-muted mb-2">{batch.description || "No description"}</p>
            <p className="text-sm mb-6">{batch.active ? "You are enrolled in this batch." : "This batch is archived. You can still open its practice problems."}</p>
            <h2 className="text-xl font-semibold mb-3">Practice problems</h2>
            {(!batch.problems || batch.problems.length === 0) && (
              <p className="app-surface rounded p-4">No practice problems are assigned yet.</p>
            )}
            {Array.isArray(batch.problems) && batch.problems.length > 0 && (
              <ol className="app-surface rounded divide-y divide-gray-200 dark:divide-gray-700">
                {batch.problems.map((problem, index) => {
                  const path = practiceProblemPath(problem);
                  const label = practiceProblemLabel(problem);
                  return (
                    <li key={problem.problemId || index} className="px-4 py-3">
                      {path ? (
                        <Link to={path} className="text-blue-600 dark:text-blue-400 hover:underline">{label}</Link>
                      ) : label}
                      {problem.difficulty ? <span className="app-muted text-sm ml-2">{problem.difficulty}</span> : null}
                    </li>
                  );
                })}
              </ol>
            )}
            {batch.totalPages > 1 && (
              <div className="flex gap-2 mt-3">
                <button type="button" className="px-3 py-1 rounded app-surface" disabled={(batch.currentPage || 0) <= 0} onClick={() => changePage(batch.currentPage - 1)}>Previous</button>
                <button type="button" className="px-3 py-1 rounded app-surface" disabled={(batch.currentPage || 0) + 1 >= batch.totalPages} onClick={() => changePage(batch.currentPage + 1)}>Next</button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default StudentBatchPage;
