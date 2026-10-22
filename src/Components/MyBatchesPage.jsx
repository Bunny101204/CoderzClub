import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch } from "../apiClient";

function authHeaders() {
  const token = localStorage.getItem("jwtToken") || localStorage.getItem("token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

const MyBatchesPage = () => {
  const [batches, setBatches] = useState([]);
  const [state, setState] = useState("loading");
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      const response = await apiFetch("/api/my/batches", { headers: authHeaders() });
      if (cancelled) return;
      if (!response.ok) {
        setState("error");
        setError(response.status === 401 ? "Sign in again to view your batches." : "Could not load your batches.");
        return;
      }
      const data = await response.json();
      setBatches(data.batches || []);
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

  return (
    <div className="app-shell p-8">
      <div className="max-w-3xl mx-auto">
        <h1 className="text-3xl font-bold mb-2">My batches</h1>
        <p className="app-muted mb-6">Batches you coordinate. This list does not include other institutions.</p>
        {state === "loading" && <p>Loading batches...</p>}
        {state === "error" && <p className="text-red-600 dark:text-red-400" role="alert">{error}</p>}
        {state === "ready" && batches.length === 0 && (
          <p className="app-surface rounded p-4">No batches are assigned to you.</p>
        )}
        {state === "ready" && batches.length > 0 && (
          <ul className="space-y-3">
            {batches.map((batch) => (
              <li key={batch.batchId} className="app-surface rounded p-4">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div>
                    <Link to={`/my/batches/${batch.batchId}`} className="text-lg font-semibold text-blue-600 dark:text-blue-400 hover:underline">
                      {batch.name}
                    </Link>
                    <p className="app-muted text-sm">{batch.description || "No description"}</p>
                    <p className="text-sm mt-1">
                      {batch.active ? "Active" : "Archived"}
                      {batch.institution ? ` · ${batch.institution}` : ""}
                    </p>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};

export default MyBatchesPage;
