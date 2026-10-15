import React, { useEffect, useRef, useState } from "react";
import { apiFetch } from "../apiClient";
import { Link, useParams } from "react-router-dom";
import { displayAssignedProblemId, batchStatusLabel } from "../admin/batchProgress";
import ProblemStatusMark from "./ProblemStatusMark";

function authHeaders(json = false) {
  const token = localStorage.getItem("jwtToken") || localStorage.getItem("token");
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (json) headers["Content-Type"] = "application/json";
  return headers;
}

const AdminBatchPage = () => {
  const { id } = useParams();
  const [tab, setTab] = useState("overview");
  const [detail, setDetail] = useState(null);
  const [members, setMembers] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [report, setReport] = useState(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [userSearch, setUserSearch] = useState("");
  const [userHits, setUserHits] = useState([]);
  const [userSearchState, setUserSearchState] = useState("idle");
  const [selectedAdds, setSelectedAdds] = useState([]);
  const [selectedMembers, setSelectedMembers] = useState([]);
  const [memberPage, setMemberPage] = useState(0);
  const [memberPages, setMemberPages] = useState(0);
  const [memberFilter, setMemberFilter] = useState("");
  const [problemSearch, setProblemSearch] = useState("");
  const [problemHits, setProblemHits] = useState([]);
  const [problemSearchState, setProblemSearchState] = useState("idle");
  const [selectedProblems, setSelectedProblems] = useState([]);
  const [selectedAssignments, setSelectedAssignments] = useState([]);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [studentFilter, setStudentFilter] = useState("");
  const [reportPage, setReportPage] = useState(0);
  const [busy, setBusy] = useState(false);
  const reportSeq = useRef(0);
  const userSearchSeq = useRef(0);
  const problemSearchSeq = useRef(0);

  const loadDetail = async () => {
    const response = await apiFetch(`/api/admin/batches/${id}`, { headers: authHeaders() });
    if (!response.ok) throw new Error(response.status === 403 ? "You do not have permission to manage batches." : "Failed to load batch");
    const data = await response.json();
    setDetail(data);
    setEditName(data.name || "");
    setEditDescription(data.description || "");
  };

  const loadMembers = async (page = memberPage, search = memberFilter) => {
    const params = new URLSearchParams({ page: String(page), size: "20" });
    if (search.trim()) params.set("search", search.trim());
    const response = await apiFetch(`/api/admin/batches/${id}/members?${params}`, { headers: authHeaders() });
    if (!response.ok) {
      setError(response.status === 403 ? "You do not have permission to view members." : "Failed to load members");
      return;
    }
    const data = await response.json();
    setMembers(data.members || []);
    setMemberPages(data.totalPages || 0);
    setSelectedMembers([]);
  };

  const loadAssignments = async () => {
    const response = await apiFetch(`/api/admin/batches/${id}/assignments?page=0&size=80`, { headers: authHeaders() });
    if (response.ok) {
      const data = await response.json();
      setAssignments(data.assignments || []);
    }
  };

  const loadReport = async (page = 0, student = studentFilter) => {
    const seq = ++reportSeq.current;
    const params = new URLSearchParams({ page: String(page), size: "20" });
    if (student.trim()) params.set("student", student.trim());
    const response = await apiFetch(`/api/admin/batches/${id}/report?${params}`, { headers: authHeaders() });
    if (seq !== reportSeq.current) return;
    if (response.ok) setReport(await response.json());
  };

  useEffect(() => {
    loadDetail().catch((err) => setError(err.message));
    loadMembers();
    loadAssignments();
  }, [id]);

  useEffect(() => {
    if (tab === "report") loadReport(reportPage);
  }, [tab, reportPage, id]);

  const searchUsers = async (value) => {
    setUserSearch(value);
    if (!value.trim()) {
      userSearchSeq.current += 1;
      setUserHits([]);
      setUserSearchState("idle");
      return;
    }
    const seq = ++userSearchSeq.current;
    setUserSearchState("loading");
    const response = await apiFetch(`/api/admin/batches/users?search=${encodeURIComponent(value.trim())}&page=0&size=20`, {
      headers: authHeaders()
    });
    if (seq !== userSearchSeq.current) return;
    if (!response.ok) {
      setUserSearchState("error");
      setError(response.status === 403 ? "You do not have permission to search students." : "Student search failed");
      return;
    }
    const data = await response.json();
    setUserHits(data.users || []);
    setUserSearchState("done");
  };

  const searchProblems = async (value) => {
    setProblemSearch(value);
    if (!value.trim()) {
      problemSearchSeq.current += 1;
      setProblemHits([]);
      setProblemSearchState("idle");
      return;
    }
    const seq = ++problemSearchSeq.current;
    setProblemSearchState("loading");
    const response = await apiFetch(`/api/problems?search=${encodeURIComponent(value.trim())}&size=20`);
    if (seq !== problemSearchSeq.current) return;
    if (!response.ok) {
      setProblemSearchState("error");
      setError("Problem search failed");
      return;
    }
    const data = await response.json();
    setProblemHits(data.problems || []);
    setProblemSearchState("done");
  };

  const addSelectedMembers = async () => {
    if (!selectedAdds.length) return;
    await addMembers(selectedAdds);
  };

  const addMembers = async (userIds) => {
    if (!detail?.active || busy || !userIds.length) return;
    setBusy(true);
    setError("");
    setNotice("");
    const response = await apiFetch(`/api/admin/batches/${id}/members`, {
      method: "POST",
      headers: authHeaders(true),
      body: JSON.stringify({ userIds })
    });
    setBusy(false);
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(data.error || "Could not add students");
      return;
    }
    setNotice(userIds.length === 1 ? "Student added." : `${userIds.length} students added.`);
    setSelectedAdds([]);
    await loadMembers(0, memberFilter);
    setMemberPage(0);
    await loadDetail();
  };

  const removeMember = async (userId, username) => {
    if (!window.confirm(`Remove ${username} from this batch? Submissions will not be deleted.`)) return;
    setBusy(true);
    setError("");
    const response = await apiFetch(`/api/admin/batches/${id}/members/${userId}`, { method: "DELETE", headers: authHeaders() });
    setBusy(false);
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(data.error || "Could not remove student");
      return;
    }
    setNotice("Student removed. Past submissions remain.");
    await loadMembers(memberPage, memberFilter);
    await loadDetail();
  };

  const removeSelectedMembers = async () => {
    if (!selectedMembers.length) return;
    if (!window.confirm(`Remove ${selectedMembers.length} selected students? Submissions will not be deleted.`)) return;
    setBusy(true);
    setError("");
    const response = await apiFetch(`/api/admin/batches/${id}/members/remove`, {
      method: "POST",
      headers: authHeaders(true),
      body: JSON.stringify({ userIds: selectedMembers })
    });
    setBusy(false);
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(data.error || "Could not remove selected students");
      return;
    }
    setNotice("Selected students removed. Past submissions remain.");
    await loadMembers(memberPage, memberFilter);
    await loadDetail();
  };

  const assignProblem = async (problemId) => {
    if (!detail?.active || busy) return;
    setBusy(true);
    setError("");
    const response = await apiFetch(`/api/admin/batches/${id}/assignments`, {
      method: "POST",
      headers: authHeaders(true),
      body: JSON.stringify({ problemIds: [problemId] })
    });
    setBusy(false);
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(data.error || "Could not assign problem");
      return;
    }
    await loadAssignments();
    await loadDetail();
  };

  const unassign = async (problemId, title) => {
    if (!window.confirm(`Unassign ${title || "this problem"}? The problem itself will not be deleted.`)) return;
    await apiFetch(`/api/admin/batches/${id}/assignments/${problemId}`, { method: "DELETE", headers: authHeaders() });
    await loadAssignments();
    await loadDetail();
  };

  const assignSelectedProblems = async () => {
    if (!detail?.active || busy || !selectedProblems.length) return;
    setBusy(true);
    setError("");
    const response = await apiFetch(`/api/admin/batches/${id}/assignments`, {
      method: "POST",
      headers: authHeaders(true),
      body: JSON.stringify({ problemIds: selectedProblems })
    });
    setBusy(false);
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(data.error || "Could not assign problems");
      return;
    }
    setNotice("Problems assigned.");
    setSelectedProblems([]);
    await loadAssignments();
    await loadDetail();
  };

  const unassignSelected = async () => {
    if (!selectedAssignments.length) return;
    if (!window.confirm(`Unassign ${selectedAssignments.length} problems? Completed work stays on the problems.`)) return;
    setBusy(true);
    setError("");
    const response = await apiFetch(`/api/admin/batches/${id}/assignments/remove`, {
      method: "POST",
      headers: authHeaders(true),
      body: JSON.stringify({ problemIds: selectedAssignments })
    });
    setBusy(false);
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(data.error || "Could not unassign problems");
      return;
    }
    setNotice("Problems unassigned. Completed submissions remain.");
    setSelectedAssignments([]);
    await loadAssignments();
    await loadDetail();
  };

  const saveDetails = async () => {
    setBusy(true);
    setError("");
    const response = await apiFetch(`/api/admin/batches/${id}`, {
      method: "PATCH",
      headers: authHeaders(true),
      body: JSON.stringify({ name: editName, description: editDescription })
    });
    setBusy(false);
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(data.error || "Could not update the batch");
      return;
    }
    setDetail(await response.json());
    setNotice("Batch details saved.");
  };

  const archive = async () => {
    if (!window.confirm("Archive this batch? Users, problems, and submissions stay intact. New membership and assignment changes will stop.")) return;
    const response = await apiFetch(`/api/admin/batches/${id}/archive`, { method: "POST", headers: authHeaders() });
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(data.error || "Could not archive the batch");
      return;
    }
    setDetail(await response.json());
    setNotice("Batch archived. Historical reports remain available.");
  };

  const reactivate = async () => {
    if (!window.confirm("Reactivate this batch? Existing members become eligible again. Membership history is not recreated.")) return;
    const response = await apiFetch(`/api/admin/batches/${id}/reactivate`, { method: "POST", headers: authHeaders() });
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(data.error || "Could not reactivate the batch");
      return;
    }
    setDetail(await response.json());
    setNotice("Batch reactivated.");
  };

  const exportCsv = async () => {
    setError("");
    const params = new URLSearchParams();
    if (studentFilter.trim()) params.set("student", studentFilter.trim());
    const response = await apiFetch(`/api/admin/batches/${id}/report.csv?${params}`, { headers: authHeaders() });
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(data.error || "CSV export is limited to 500 students. Filter or split the batch.");
      return;
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "batch-report.csv";
    link.click();
    URL.revokeObjectURL(url);
  };

  if (!detail) {
    return <div className="app-shell p-8">{error || "Loading batch..."}</div>;
  }

  const summary = report?.summary || {};

  return (
    <div className="app-shell p-8">
      <div className="max-w-7xl mx-auto">
        <Link to="/admin" className="text-blue-600 dark:text-blue-400 hover:underline">← Admin Dashboard</Link>
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mt-4 mb-6">
          <div>
            <h1 className="text-3xl font-bold">{detail.name}</h1>
            <p className="app-muted">{detail.description || "No description"}</p>
            <p className="text-sm mt-2 flex flex-wrap gap-2 items-center">
              <span className={`px-2 py-0.5 rounded text-xs font-semibold ${detail.active ? "bg-green-100 text-green-800" : "bg-yellow-100 text-yellow-900"}`}>
                {detail.active ? "Active" : "Archived"}
              </span>
              <span>{detail.memberCount} students</span>
              <span>{detail.assignmentCount} problems</span>
            </p>
          </div>
          <div className="flex gap-2">
            {detail.active ? (
              <button type="button" onClick={archive} className="px-4 py-2 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white">Archive batch</button>
            ) : (
              <button type="button" onClick={reactivate} className="px-4 py-2 rounded bg-green-600 text-white">Reactivate batch</button>
            )}
          </div>
        </div>
        {!detail.active && (
          <div className="mb-4 text-yellow-800 dark:text-yellow-300" role="status">This batch is archived. Reports remain available; membership and assignments cannot be changed.</div>
        )}
        {error && <div className="mb-4 text-red-600 dark:text-red-400" role="alert">{error}</div>}
        {notice && <div className="mb-4 text-green-700 dark:text-green-300" role="status">{notice}</div>}
        <div className="flex gap-4 border-b border-gray-200 dark:border-gray-700 mb-6 overflow-x-auto">
          {["overview", "members", "assignments", "report"].map((item) => (
            <button
              key={item}
              onClick={() => setTab(item)}
              className={`px-4 py-2 capitalize ${tab === item ? "text-blue-600 dark:text-blue-400 border-b-2 border-blue-600 dark:border-blue-400" : "app-muted"}`}
            >
              {item}
            </button>
          ))}
        </div>

        {tab === "overview" && (
          <form className="app-surface rounded p-4 grid gap-3 max-w-xl" onSubmit={(event) => { event.preventDefault(); saveDetails(); }}>
            <label className="text-sm font-medium" htmlFor="batch-name">Name</label>
            <input id="batch-name" value={editName} onChange={(e) => setEditName(e.target.value)} className="px-3 py-2 app-input rounded" required />
            <label className="text-sm font-medium" htmlFor="batch-description">Description</label>
            <textarea id="batch-description" value={editDescription} onChange={(e) => setEditDescription(e.target.value)} className="px-3 py-2 app-input rounded min-h-24" />
            <button type="submit" disabled={busy} className="px-4 py-2 rounded bg-blue-600 text-white disabled:opacity-50 w-fit">Save details</button>
          </form>
        )}

        {tab === "members" && (
          <div>
            <div className="flex flex-col sm:flex-row gap-2 mb-3">
              <input
                value={userSearch}
                onChange={(e) => searchUsers(e.target.value)}
                placeholder="Search registered students"
                aria-label="Search students to add"
                className="w-full max-w-md px-3 py-2 app-input rounded"
              />
              <button type="button" className="px-4 py-2 rounded bg-green-600 text-white disabled:opacity-50" disabled={!detail.active || busy || !selectedAdds.length} onClick={addSelectedMembers}>
                Add Students
              </button>
            </div>
            {userSearchState === "loading" && <p className="text-sm app-muted mb-3">Searching students...</p>}
            {userSearchState === "done" && userHits.length === 0 && <p className="text-sm mb-3">Search returned no results.</p>}
            {userHits.length > 0 && (
              <div className="app-surface rounded p-3 mb-4">
                {userHits.map((user) => (
                  <label key={user.id} className="flex items-center justify-between gap-3 py-2 border-b border-gray-200 dark:border-gray-700 last:border-0">
                    <span className="flex items-center gap-2">
                      <input type="checkbox" checked={selectedAdds.includes(user.id)} onChange={() => setSelectedAdds((current) => current.includes(user.id) ? current.filter((item) => item !== user.id) : [...current, user.id])} />
                      <span>{user.username} <span className="app-muted">{user.email}</span></span>
                    </span>
                    <span className="text-xs">{user.accountStatus || "ACTIVE"}{user.emailVerified ? "" : " · unverified"}</span>
                  </label>
                ))}
              </div>
            )}
            <div className="flex flex-col sm:flex-row gap-2 mb-3">
              <input
                value={memberFilter}
                onChange={(e) => setMemberFilter(e.target.value)}
                placeholder="Filter current members"
                aria-label="Filter current members"
                className="w-full max-w-md px-3 py-2 app-input rounded"
              />
              <button type="button" className="px-4 py-2 rounded bg-blue-600 text-white" onClick={() => { setMemberPage(0); loadMembers(0, memberFilter); }}>Search members</button>
              <button type="button" className="px-4 py-2 rounded bg-red-600 text-white disabled:opacity-50" disabled={!detail.active || busy || !selectedMembers.length} onClick={removeSelectedMembers}>Remove selected</button>
            </div>
            <div className="app-surface rounded p-4 overflow-x-auto">
              {members.length === 0 ? (memberFilter.trim() ? "Search returned no results." : "No members yet.") : (
                <table className="w-full text-left">
                  <thead><tr><th className="py-2"></th><th>Username</th><th>Email</th><th>Status</th><th></th></tr></thead>
                  <tbody>
                    {members.map((user) => (
                      <tr key={user.id} className="border-t border-gray-200 dark:border-gray-700">
                        <td className="py-2">
                          <input type="checkbox" aria-label={`Select ${user.username}`} checked={selectedMembers.includes(user.id)} onChange={() => setSelectedMembers((current) => current.includes(user.id) ? current.filter((item) => item !== user.id) : [...current, user.id])} />
                        </td>
                        <td>{user.username}</td>
                        <td>{user.email}</td>
                        <td>{user.accountStatus || "ACTIVE"}{user.emailVerified === false ? " · unverified" : ""}</td>
                        <td>
                          <button type="button" className="text-red-600 dark:text-red-400" disabled={!detail.active || busy} onClick={() => removeMember(user.id, user.username)}>Remove</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
            {memberPages > 1 && (
              <div className="flex gap-2 mt-4">
                <button type="button" disabled={memberPage === 0} onClick={() => { const next = Math.max(0, memberPage - 1); setMemberPage(next); loadMembers(next, memberFilter); }} className="px-3 py-1 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white disabled:opacity-50">Prev</button>
                <button type="button" disabled={memberPage + 1 >= memberPages} onClick={() => { const next = memberPage + 1; setMemberPage(next); loadMembers(next, memberFilter); }} className="px-3 py-1 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white disabled:opacity-50">Next</button>
              </div>
            )}
          </div>
        )}

        {tab === "assignments" && (
          <div>
            <input
              value={problemSearch}
              onChange={(e) => searchProblems(e.target.value)}
              placeholder="Search problems by numeric ID or title"
              className="w-full max-w-md px-3 py-2 app-input rounded mb-4"
            />
            {problemSearchState === "loading" && <p className="text-sm app-muted mb-3">Searching problems...</p>}
            {problemSearchState === "done" && problemHits.length === 0 && <p className="text-sm mb-3">Search returned no results.</p>}
            {problemHits.length > 0 && (
              <div className="app-surface rounded p-3 mb-4">
                <button type="button" className="mb-2 px-3 py-1 rounded bg-green-600 text-white disabled:opacity-50" disabled={!detail.active || busy || !selectedProblems.length} onClick={assignSelectedProblems}>Assign selected</button>
                {problemHits.map((problem) => (
                  <label key={problem.id} className="flex justify-between gap-3 py-2 border-b border-gray-200 dark:border-gray-700 last:border-0">
                    <span className="flex items-center gap-2">
                      <input type="checkbox" checked={selectedProblems.includes(problem.id)} onChange={() => setSelectedProblems((current) => current.includes(problem.id) ? current.filter((item) => item !== problem.id) : [...current, problem.id])} />
                      <span>
                        <span className="font-mono mr-2">{displayAssignedProblemId(problem)}</span>
                        {problem.title} <span className="app-muted">{problem.difficulty}</span>
                      </span>
                    </span>
                  </label>
                ))}
              </div>
            )}
            <button type="button" className="mb-3 px-3 py-1 rounded bg-red-600 text-white disabled:opacity-50" disabled={!detail.active || busy || !selectedAssignments.length} onClick={unassignSelected}>Unassign selected</button>
            <div className="app-surface rounded p-4 overflow-x-auto">
              {assignments.length === 0 ? "No assignments yet." : (
                <table className="w-full text-left">
                  <thead><tr><th className="py-2"></th><th>ID</th><th>Title</th><th>Difficulty</th><th>Tags</th><th></th></tr></thead>
                  <tbody>
                    {assignments.map((problem) => (
                      <tr key={problem.problemId} className="border-t border-gray-200 dark:border-gray-700">
                        <td className="py-2">
                          <input type="checkbox" aria-label={`Select ${problem.title || "problem"}`} checked={selectedAssignments.includes(problem.problemId)} onChange={() => setSelectedAssignments((current) => current.includes(problem.problemId) ? current.filter((item) => item !== problem.problemId) : [...current, problem.problemId])} />
                        </td>
                        <td className="py-2 font-mono">{displayAssignedProblemId(problem)}</td>
                        <td>{problem.title}</td>
                        <td>{problem.difficulty}</td>
                        <td>{(problem.tags || []).join(", ")}</td>
                        <td>
                          <button className="text-red-600 dark:text-red-400" disabled={!detail.active} onClick={() => unassign(problem.problemId, problem.title)}>Unassign</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        )}

        {tab === "report" && (
          <div>
            <div className="flex flex-col sm:flex-row gap-3 mb-4">
              <input
                value={studentFilter}
                onChange={(e) => setStudentFilter(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") { setReportPage(0); loadReport(0, e.target.value); } }}
                placeholder="Find student"
                className="px-3 py-2 app-input rounded"
              />
              <button className="px-4 py-2 bg-blue-600 text-white rounded" onClick={() => { setReportPage(0); loadReport(0); }}>Search</button>
              <button className="px-4 py-2 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white" onClick={exportCsv}>Export CSV</button>
            </div>
            {report && (
              <>
                <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3 mb-4">
                  <div className="app-surface rounded p-3">Students {summary.studentCount}</div>
                  <div className="app-surface rounded p-3">Problems {summary.assignedProblemCount}</div>
                  <div className="app-surface rounded p-3">Solved {summary.solvedCells}</div>
                  <div className="app-surface rounded p-3">Attempted {summary.attemptedCells}</div>
                  <div className="app-surface rounded p-3">Unsolved {summary.unsolvedCells}</div>
                  <div className="app-surface rounded p-3">Completion {summary.completionPercent}%</div>
                </div>
                <div className="md:hidden space-y-3">
                  {(report.students || []).map((student) => (
                    <div key={student.userId} className="app-surface rounded p-3">
                      <div className="font-semibold">{student.username}</div>
                      <div className="text-sm app-muted">
                        {student.solvedCount} solved · {student.attemptedCount} attempted · {student.unsolvedCount} unsolved · {student.completionPercent}%
                      </div>
                    </div>
                  ))}
                </div>
                <div className="hidden md:block overflow-x-auto app-surface rounded p-2">
                  <table className="text-left min-w-full">
                    <thead>
                      <tr>
                        <th className="py-2 px-3 sticky left-0 bg-white dark:bg-gray-800">Student</th>
                        {(report.problems || []).map((problem) => (
                          <th key={problem.problemId} className="py-2 px-3 font-mono" title={problem.title}>
                            {displayAssignedProblemId(problem)}
                          </th>
                        ))}
                        <th className="py-2 px-3">Solved</th>
                        <th className="py-2 px-3">%</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(report.students || []).map((student) => (
                        <tr key={student.userId} className="border-t border-gray-200 dark:border-gray-700">
                          <td className="py-2 px-3 sticky left-0 bg-white dark:bg-gray-800">{student.username}</td>
                          {(student.cells || []).map((cell) => (
                            <td key={cell.problemId} className="py-2 px-3" title={batchStatusLabel(cell.status)} aria-label={batchStatusLabel(cell.status)}>
                              <ProblemStatusMark status={cell.status} />
                            </td>
                          ))}
                          <td className="py-2 px-3">{student.solvedCount}</td>
                          <td className="py-2 px-3">{student.completionPercent}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {report.totalPages > 1 && (
                  <div className="flex gap-2 mt-4">
                    <button disabled={reportPage === 0} onClick={() => setReportPage((p) => Math.max(0, p - 1))} className="px-3 py-1 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white disabled:opacity-50">Prev</button>
                    <button disabled={reportPage + 1 >= report.totalPages} onClick={() => setReportPage((p) => p + 1)} className="px-3 py-1 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white disabled:opacity-50">Next</button>
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default AdminBatchPage;
