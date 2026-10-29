import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch } from "../apiClient";
import { myBatchesView, practiceProblemLabel, practiceProblemPath } from "../batches/myBatches";

function authHeaders() {
  const token = localStorage.getItem("jwtToken") || localStorage.getItem("token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

const MyBatchesPage = () => {
  const [payload, setPayload] = useState(null);
  const [state, setState] = useState("loading");
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    const load = async (page = 0) => {
      const response = await apiFetch(`/api/my/batches?page=${page}&size=20`, { headers: authHeaders() });
      if (cancelled) return;
      if (!response.ok) {
        setState("error");
        setError(response.status === 401 ? "Sign in again to view your batches." : "Could not load your batches.");
        return;
      }
      setPayload(await response.json());
      setState("ready");
    };
    load().catch(() => {
      if (!cancelled) {
        setState("error");
        setError("Could not load your batches.");
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const view = myBatchesView(payload);

  const changePage = async (page) => {
    setState("loading");
    try {
      const response = await apiFetch(`/api/my/batches?page=${page}&size=20`, { headers: authHeaders() });
      if (!response.ok) {
        setState("error");
        setError("Could not load your batches.");
        return;
      }
      setPayload(await response.json());
      setState("ready");
    } catch {
      setState("error");
      setError("Could not load your batches.");
    }
  };

  return (
    <div className="app-shell p-4 sm:p-8">
      <div className="max-w-3xl mx-auto">
        <h1 className="text-3xl font-bold mb-2">My batches</h1>
        <p className="app-muted mb-6">Classes you are enrolled in, and batches you coordinate.</p>
        {state === "loading" && <p>Loading batches...</p>}
        {state === "error" && <p className="text-red-600 dark:text-red-400" role="alert">{error}</p>}
        {state === "ready" && view.empty && (
          <p className="app-surface rounded p-4">You are not enrolled in a batch yet.</p>
        )}
        {state === "ready" && view.showEnrolledSection && !view.empty && (
          <section className="mb-8" aria-labelledby="enrolled-batches-heading">
            <h2 id="enrolled-batches-heading" className="text-xl font-semibold mb-3">Your batches</h2>
            <ul className="space-y-3">
              {view.enrolled.map((batch) => (
                <li key={batch.batchId} className="app-surface rounded p-4">
                  <Link to={`/my/batches/enrolled/${batch.batchId}`} className="text-lg font-semibold text-blue-600 dark:text-blue-400 hover:underline">
                    {batch.name}
                  </Link>
                  <p className="app-muted text-sm">{batch.description || "No description"}</p>
                  <p className="text-sm mt-1">
                    {batch.active ? "Enrolled" : "Archived"}
                    {` · ${batch.assignmentCount || 0} practice problem${batch.assignmentCount === 1 ? "" : "s"}`}
                  </p>
                  {Array.isArray(batch.problems) && batch.problems.length > 0 && (
                    <ul className="mt-2 text-sm space-y-1">
                      {batch.problems.map((problem) => {
                        const path = practiceProblemPath(problem);
                        const label = practiceProblemLabel(problem);
                        return (
                          <li key={problem.problemId || label}>
                            {path ? (
                              <Link to={path} className="text-blue-600 dark:text-blue-400 hover:underline">{label}</Link>
                            ) : label}
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </li>
              ))}
            </ul>
            {view.totalPages > 1 && (
              <div className="flex gap-2 mt-3">
                <button type="button" className="px-3 py-1 rounded app-surface" disabled={view.currentPage <= 0} onClick={() => changePage(view.currentPage - 1)}>Previous</button>
                <button type="button" className="px-3 py-1 rounded app-surface" disabled={view.currentPage + 1 >= view.totalPages} onClick={() => changePage(view.currentPage + 1)}>Next</button>
              </div>
            )}
          </section>
        )}
        {state === "ready" && view.showCoordinatorSection && (
          <section aria-labelledby="coordinator-batches-heading">
            <h2 id="coordinator-batches-heading" className="text-xl font-semibold mb-3">Batches you coordinate</h2>
            <ul className="space-y-3">
              {view.coordinated.map((batch) => (
                <li key={batch.batchId} className="app-surface rounded p-4">
                  <Link to={`/my/batches/${batch.batchId}`} className="text-lg font-semibold text-blue-600 dark:text-blue-400 hover:underline">
                    {batch.name}
                  </Link>
                  <p className="app-muted text-sm">{batch.description || "No description"}</p>
                  <p className="text-sm mt-1">
                    {batch.active ? "Active" : "Archived"}
                    {batch.institution ? ` · ${batch.institution}` : ""}
                  </p>
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>
    </div>
  );
};

export default MyBatchesPage;
