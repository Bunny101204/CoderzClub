import { useEffect, useState } from "react";

function authHeaders(json = false) {
  const token = localStorage.getItem("jwtToken") || localStorage.getItem("token");
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (json) headers["Content-Type"] = "application/json";
  return headers;
}

const BundleAccessPanel = ({ bundleId }) => {
  const [visibility, setVisibility] = useState("PUBLIC");
  const [users, setUsers] = useState([]);
  const [batches, setBatches] = useState([]);
  const [userSearch, setUserSearch] = useState("");
  const [userHits, setUserHits] = useState([]);
  const [batchSearch, setBatchSearch] = useState("");
  const [batchHits, setBatchHits] = useState([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const loadAccess = async () => {
    const response = await fetch(`/api/admin/bundles/${bundleId}/access`, { headers: authHeaders() });
    if (!response.ok) throw new Error("Failed to load access");
    const data = await response.json();
    setVisibility(data.visibility || "PUBLIC");
    setUsers(data.users || []);
    setBatches(data.batches || []);
  };

  useEffect(() => {
    if (!bundleId) return;
    loadAccess().catch((err) => setError(err.message));
  }, [bundleId]);

  const saveVisibility = async (next) => {
    setBusy(true);
    setError("");
    try {
      const response = await fetch(`/api/admin/bundles/${bundleId}/visibility`, {
        method: "PUT",
        headers: authHeaders(true),
        body: JSON.stringify({ visibility: next })
      });
      if (!response.ok) throw new Error("Could not update visibility");
      setVisibility(next);
      await loadAccess();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const grant = async (subjectType, subjectId) => {
    setBusy(true);
    setError("");
    try {
      const response = await fetch(`/api/admin/bundles/${bundleId}/grants`, {
        method: "POST",
        headers: authHeaders(true),
        body: JSON.stringify({ subjectType, subjectId })
      });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.error || "Could not add grant");
      }
      setUserHits([]);
      setBatchHits([]);
      setUserSearch("");
      setBatchSearch("");
      await loadAccess();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const revoke = async (grantId) => {
    setBusy(true);
    setError("");
    try {
      const response = await fetch(`/api/admin/bundles/${bundleId}/grants/${grantId}`, {
        method: "DELETE",
        headers: authHeaders()
      });
      if (!response.ok) throw new Error("Could not revoke grant");
      await loadAccess();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const searchUsers = async (value) => {
    setUserSearch(value);
    if (!value.trim()) {
      setUserHits([]);
      return;
    }
    const params = new URLSearchParams({ search: value.trim(), page: "0", size: "10" });
    const response = await fetch(`/api/admin/batches/users?${params}`, { headers: authHeaders() });
    if (response.ok) {
      const data = await response.json();
      setUserHits(data.users || []);
    }
  };

  const searchBatches = async (value) => {
    setBatchSearch(value);
    if (!value.trim()) {
      setBatchHits([]);
      return;
    }
    const params = new URLSearchParams({ search: value.trim(), page: "0", size: "10" });
    const response = await fetch(`/api/admin/batches?${params}`, { headers: authHeaders() });
    if (response.ok) {
      const data = await response.json();
      setBatchHits(data.batches || []);
    }
  };

  return (
    <div className="app-surface rounded-xl p-6 space-y-6">
      <div>
        <h3 className="text-xl font-bold mb-2">Who can see this bundle?</h3>
        <p className="app-muted text-sm mb-4">
          Visibility: {visibility === "RESTRICTED" ? "Restricted" : "Public"}
        </p>
        <div className="flex gap-2">
          <button
            type="button"
            disabled={busy}
            onClick={() => saveVisibility("PUBLIC")}
            className={`px-4 py-2 rounded ${visibility === "PUBLIC" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white"}`}
          >
            Public
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => saveVisibility("RESTRICTED")}
            className={`px-4 py-2 rounded ${visibility === "RESTRICTED" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white"}`}
          >
            Restricted
          </button>
        </div>
      </div>
      {error && <div className="text-red-600 dark:text-red-400 text-sm">{error}</div>}
      {visibility === "RESTRICTED" && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div>
            <h4 className="font-semibold mb-2">Direct users</h4>
            <input
              value={userSearch}
              onChange={(e) => searchUsers(e.target.value)}
              placeholder="Search users by username or email"
              className="w-full px-3 py-2 app-input rounded mb-3"
            />
            {userHits.length > 0 && (
              <div className="app-inset rounded p-3 mb-3">
                {userHits.map((user) => (
                  <div key={user.id} className="flex justify-between py-2 border-b border-gray-200 dark:border-gray-700 last:border-0">
                    <span>{user.username} <span className="app-muted">{user.email}</span></span>
                    <button className="text-green-700 dark:text-green-400" disabled={busy} onClick={() => grant("USER", user.id)}>Grant</button>
                  </div>
                ))}
              </div>
            )}
            {users.length === 0 ? (
              <p className="app-muted text-sm">No direct user grants.</p>
            ) : (
              <ul className="space-y-2">
                {users.map((user) => (
                  <li key={user.grantId} className="flex justify-between app-inset rounded p-3">
                    <span>{user.username} <span className="app-muted">{user.email}</span></span>
                    <button className="text-red-600 dark:text-red-400" disabled={busy} onClick={() => revoke(user.grantId)}>Revoke</button>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div>
            <h4 className="font-semibold mb-2">Batches</h4>
            <input
              value={batchSearch}
              onChange={(e) => searchBatches(e.target.value)}
              placeholder="Search batches by name"
              className="w-full px-3 py-2 app-input rounded mb-3"
            />
            {batchHits.length > 0 && (
              <div className="app-inset rounded p-3 mb-3">
                {batchHits.map((batch) => (
                  <div key={batch.id} className="flex justify-between py-2 border-b border-gray-200 dark:border-gray-700 last:border-0">
                    <span>{batch.name} <span className="app-muted">{batch.active ? "Active" : "Archived"}</span></span>
                    <button className="text-green-700 dark:text-green-400" disabled={busy} onClick={() => grant("BATCH", batch.id)}>Grant</button>
                  </div>
                ))}
              </div>
            )}
            {batches.length === 0 ? (
              <p className="app-muted text-sm">No batch grants.</p>
            ) : (
              <ul className="space-y-2">
                {batches.map((batch) => (
                  <li key={batch.grantId} className="flex justify-between app-inset rounded p-3">
                    <span>{batch.name} <span className="app-muted">{batch.active ? "Active" : "Archived"}</span></span>
                    <button className="text-red-600 dark:text-red-400" disabled={busy} onClick={() => revoke(batch.grantId)}>Revoke</button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default BundleAccessPanel;
