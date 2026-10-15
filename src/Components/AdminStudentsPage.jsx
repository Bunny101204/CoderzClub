import { useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch } from "../apiClient";

function authHeaders(json = false) {
  const token = localStorage.getItem("jwtToken") || localStorage.getItem("token");
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (json) headers["Content-Type"] = "application/json";
  return headers;
}

export default function AdminStudentsPage() {
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [profile, setProfile] = useState(null);
  const [form, setForm] = useState({ bio: "", location: "", website: "" });
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(false);

  const searchStudents = async (nextPage = 0, query = search) => {
    setLoading(true);
    setError("");
    const params = new URLSearchParams({ page: String(nextPage), size: "20" });
    if (query.trim()) params.set("search", query.trim());
    const response = await apiFetch(`/api/admin/students?${params}`, { headers: authHeaders() });
    setLoading(false);
    if (!response.ok) {
      setError(response.status === 403 ? "You do not have permission to manage students." : "Could not load students.");
      return;
    }
    setPage(nextPage);
    setResult(await response.json());
  };

  const openStudent = async (userId) => {
    setError("");
    setNotice("");
    const response = await apiFetch(`/api/admin/students/${userId}`, { headers: authHeaders() });
    if (!response.ok) {
      setError("Could not load this student.");
      return;
    }
    const data = await response.json();
    setProfile(data);
    setForm({ bio: data.bio || "", location: data.location || "", website: data.website || "" });
  };

  const saveProfile = async (event) => {
    event.preventDefault();
    if (!profile) return;
    setError("");
    const response = await apiFetch(`/api/admin/students/${profile.id}`, {
      method: "PATCH",
      headers: authHeaders(true),
      body: JSON.stringify(form),
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      setError(data.error || "Could not update this student.");
      return;
    }
    setProfile(data);
    setNotice("Student profile saved.");
  };

  const setActive = async (active) => {
    if (!profile) return;
    const verb = active ? "activate" : "deactivate";
    if (!window.confirm(`${active ? "Activate" : "Deactivate"} ${profile.username}? Historical submissions stay intact.`)) return;
    setError("");
    const response = await apiFetch(`/api/admin/students/${profile.id}/${verb}`, {
      method: "POST",
      headers: authHeaders(),
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      setError(data.error || `Could not ${verb} this account.`);
      return;
    }
    setProfile(data);
    setNotice(active ? "Account activated." : "Account deactivated. Existing sessions can no longer be used.");
  };

  return (
    <div className="app-shell p-8">
      <div className="max-w-6xl mx-auto">
        <Link to="/admin" className="text-blue-600 dark:text-blue-400 hover:underline">← Admin Dashboard</Link>
        <h1 className="text-3xl font-bold mt-4 mb-4">Students</h1>
        {error && <div className="mb-4 text-red-600 dark:text-red-400" role="alert">{error}</div>}
        {notice && <div className="mb-4 text-green-700 dark:text-green-300" role="status">{notice}</div>}
        <form className="flex flex-col sm:flex-row gap-2 mb-4" onSubmit={(event) => { event.preventDefault(); searchStudents(0); }}>
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search username or email" aria-label="Search students" className="px-3 py-2 app-input rounded w-full sm:max-w-md" />
          <button type="submit" className="px-4 py-2 rounded bg-blue-600 text-white">Search</button>
        </form>
        {loading && <p className="app-muted mb-4">Loading students...</p>}
        {result && (result.users || []).length === 0 && <p className="mb-4">{search.trim() ? "Search returned no results." : "No students found."}</p>}
        {result && (result.users || []).length > 0 && (
          <div className="app-surface rounded overflow-x-auto mb-6">
            <table className="w-full text-left">
              <thead>
                <tr><th className="py-2 px-3">Username</th><th className="py-2 px-3">Email</th><th className="py-2 px-3">Status</th><th className="py-2 px-3">Verified</th><th></th></tr>
              </thead>
              <tbody>
                {result.users.map((student) => (
                  <tr key={student.id} className="border-t border-gray-200 dark:border-gray-700">
                    <td className="py-2 px-3">{student.username}</td>
                    <td className="py-2 px-3">{student.email}</td>
                    <td className="py-2 px-3">{student.accountStatus}</td>
                    <td className="py-2 px-3">{student.emailVerified ? "Yes" : "No"}</td>
                    <td className="py-2 px-3"><button type="button" className="text-blue-600 dark:text-blue-400" onClick={() => openStudent(student.id)}>Open</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {result && result.totalPages > 1 && (
          <div className="flex gap-2 mb-6">
            <button type="button" disabled={page === 0} onClick={() => searchStudents(page - 1)} className="px-3 py-1 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white disabled:opacity-50">Prev</button>
            <button type="button" disabled={page + 1 >= result.totalPages} onClick={() => searchStudents(page + 1)} className="px-3 py-1 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white disabled:opacity-50">Next</button>
          </div>
        )}
        {profile && (
          <div className="app-surface rounded p-4 grid gap-4">
            <div>
              <h2 className="text-xl font-semibold">{profile.username}</h2>
              <p className="text-sm app-muted">{profile.email} · {profile.role} · {profile.accountStatus} · {profile.emailVerified ? "verified" : "unverified"}</p>
            </div>
            <form className="grid gap-3 max-w-xl" onSubmit={saveProfile}>
              <label className="text-sm" htmlFor="student-bio">Bio</label>
              <textarea id="student-bio" value={form.bio} onChange={(e) => setForm({ ...form, bio: e.target.value })} className="px-3 py-2 app-input rounded min-h-20" />
              <label className="text-sm" htmlFor="student-location">Location</label>
              <input id="student-location" value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} className="px-3 py-2 app-input rounded" />
              <label className="text-sm" htmlFor="student-website">Website</label>
              <input id="student-website" value={form.website} onChange={(e) => setForm({ ...form, website: e.target.value })} className="px-3 py-2 app-input rounded" />
              <button type="submit" className="px-4 py-2 rounded bg-blue-600 text-white w-fit">Save profile</button>
            </form>
            <div className="flex gap-2">
              {profile.accountStatus === "INACTIVE" ? (
                <button type="button" onClick={() => setActive(true)} className="px-4 py-2 rounded bg-green-600 text-white">Activate account</button>
              ) : (
                <button type="button" onClick={() => setActive(false)} className="px-4 py-2 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white">Deactivate account</button>
              )}
            </div>
            <div>
              <h3 className="font-semibold mb-2">Batch memberships</h3>
              {(profile.batches || []).length === 0 ? <p>This student is not in a batch.</p> : (
                <ul className="space-y-1">
                  {profile.batches.map((batch) => (
                    <li key={batch.id}>
                      <Link to={`/admin/batches/${batch.id}`} className="text-blue-600 dark:text-blue-400 hover:underline">{batch.name}</Link>
                      <span className="ml-2 text-xs">{batch.active ? "Active" : "Archived"}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
