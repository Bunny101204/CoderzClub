import React, { useEffect, useRef, useState } from "react";
import { apiFetch } from "../apiClient";
import { Link, useLocation, useParams } from "react-router-dom";
import { displayAssignedProblemId, batchStatusLabel } from "../admin/batchProgress";
import { formatJoined, isoDayBound, studentHitStatus, studentSearchMessage } from "../admin/batchStudentSearch";
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
  const location = useLocation();
  const coordinatorView = location.pathname.startsWith("/my/batches");
  const [tab, setTab] = useState("overview");
  const [dialog, setDialog] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);
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
  const [problemFilter, setProblemFilter] = useState("");
  const [activityFrom, setActivityFrom] = useState("");
  const [activityTo, setActivityTo] = useState("");
  const [importText, setImportText] = useState("");
  const [importResult, setImportResult] = useState(null);
  const [coordinators, setCoordinators] = useState([]);
  const [institution, setInstitution] = useState("");
  const [selectedCoordinatorId, setSelectedCoordinatorId] = useState("");
  const [membershipUser, setMembershipUser] = useState(null);
  const [memberSearchLimited, setMemberSearchLimited] = useState(false);
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
    if (data.viewerRole === "ADMIN") {
      loadCoordinators();
    }
  };

  const loadCoordinators = async () => {
    const response = await apiFetch(`/api/admin/batches/${id}/coordinators`, { headers: authHeaders() });
    if (!response.ok) return;
    const data = await response.json();
    setCoordinators(data.coordinators || []);
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
    setMemberSearchLimited(Boolean(data.searchLimited));
    setSelectedMembers([]);
  };

  const loadAssignments = async () => {
    const response = await apiFetch(`/api/admin/batches/${id}/assignments?page=0&size=80`, { headers: authHeaders() });
    if (!response.ok) {
      setError(response.status === 403 ? "You do not have permission to view assignments." : "Failed to load assignments");
      return;
    }
    const data = await response.json();
    setAssignments(data.assignments || []);
  };

  const loadReport = async (page = 0, student = studentFilter, filters = {}) => {
    const seq = ++reportSeq.current;
    const params = new URLSearchParams({ page: String(page), size: "20" });
    if (student.trim()) params.set("student", student.trim());
    const selectedProblem = filters.problemId ?? problemFilter;
    const fromDay = filters.from ?? activityFrom;
    const toDay = filters.to ?? activityTo;
    if (selectedProblem) params.set("problemId", selectedProblem);
    const from = isoDayBound(fromDay, false);
    const to = isoDayBound(toDay, true);
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const response = await apiFetch(`/api/admin/batches/${id}/report?${params}`, { headers: authHeaders() });
    if (seq !== reportSeq.current) return;
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setReport(null);
      setError(data.error || "Report failed to load.");
      return;
    }
    setReport(await response.json());
  };

  useEffect(() => {
    loadDetail().catch((err) => setError(err.message));
    loadMembers();
    loadAssignments();
    loadReport(0, "");
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
    const response = await apiFetch(`/api/admin/batches/users?search=${encodeURIComponent(value.trim())}&batchId=${encodeURIComponent(id)}&page=0&size=20`, {
      headers: authHeaders()
    });
    if (seq !== userSearchSeq.current) return;
    if (!response.ok) {
      setUserHits([]);
      setUserSearchState("error");
      return;
    }
    const data = await response.json();
    const users = data.users || [];
    setUserHits(users);
    setUserSearchState(users.length ? "done" : "empty");
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
      setProblemHits([]);
      setProblemSearchState("error");
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
    const data = await response.json();
    const added = data.members?.length || 0;
    const already = data.alreadyMemberIds?.length || 0;
    if (already && !added) {
      setNotice("Already in this batch. No duplicate membership was created.");
    } else if (already) {
      setNotice(`Added ${added}. Already in this batch: ${already}.`);
    } else {
      setNotice(added === 1 ? "Student added." : `${added} students added.`);
    }
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
    const response = await apiFetch(`/api/admin/batches/${id}/assignments/${problemId}`, { method: "DELETE", headers: authHeaders() });
    if (!response.ok) {
      setError("Could not remove that problem from the batch.");
      return;
    }
    setNotice("Problem removed from this batch.");
    await loadAssignments();
    await loadDetail();
  };

  const importStudents = async () => {
    const usernames = importText.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
    if (!usernames.length || busy) return;
    setBusy(true);
    setError("");
    setImportResult(null);
    const response = await apiFetch(`/api/admin/batches/${id}/members/import`, {
      method: "POST",
      headers: authHeaders(true),
      body: JSON.stringify({ usernames })
    });
    setBusy(false);
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(data.error || "Bulk import failed. No memberships were confirmed.");
      return;
    }
    const data = await response.json();
    setImportResult(data);
    setNotice(`Added ${data.added?.length || 0}. Already members: ${data.alreadyMembers?.length || 0}. Not found: ${data.notFound?.length || 0}.`);
    await loadMembers(0, memberFilter);
    await loadDetail();
  };

  const grantCoordinator = async () => {
    if (!selectedCoordinatorId || busy) return;
    setBusy(true);
    setError("");
    const response = await apiFetch(`/api/admin/batches/${id}/coordinators`, {
      method: "POST",
      headers: authHeaders(true),
      body: JSON.stringify({ userId: selectedCoordinatorId, institution })
    });
    setBusy(false);
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(data.error || "Could not add that coordinator.");
      return;
    }
    setInstitution("");
    setSelectedCoordinatorId("");
    setNotice("Coordinator added for this batch only.");
    await loadCoordinators();
  };

  const revokeCoordinator = async (userId, username) => {
    if (!window.confirm(`Remove ${username || "this coordinator"} from this batch? Their CoderzClub account stays.`)) return;
    const response = await apiFetch(`/api/admin/batches/${id}/coordinators/${userId}`, { method: "DELETE", headers: authHeaders() });
    if (!response.ok) {
      setError("Could not remove that coordinator.");
      return;
    }
    setNotice("Coordinator access removed for this batch.");
    await loadCoordinators();
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
      return false;
    }
    setDetail(await response.json());
    setNotice("Batch details saved.");
    return true;
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
    if (problemFilter) params.set("problemId", problemFilter);
    const from = isoDayBound(activityFrom, false);
    const to = isoDayBound(activityTo, true);
    if (from) params.set("from", from);
    if (to) params.set("to", to);
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
  const isAdmin = detail.viewerRole === "ADMIN";
  const openManage = (action) => {
    setMenuOpen(false);
    if (action === "students") { setTab("members"); setDialog("add"); return; }
    if (action === "assignments") { setTab("assignments"); setDialog(""); return; }
    if (action === "reports") { setTab("report"); setDialog(""); return; }
    if (action === "archive") { archive(); return; }
    setDialog(action);
  };

  return (
    <div className="app-shell p-8">
      <div className="max-w-7xl mx-auto">
        <Link to={coordinatorView ? "/my/batches" : "/admin"} className="text-blue-600 dark:text-blue-400 hover:underline">
          {coordinatorView ? "← My batches" : "← Admin Dashboard"}
        </Link>
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mt-4 mb-6">
          <div>
            <h1 className="text-3xl font-bold">{detail.name}</h1>
            <p className="app-muted">{detail.description || "No description"}</p>
            <p className="text-sm mt-2 flex flex-wrap gap-2 items-center">
              <span className={`px-2 py-0.5 rounded text-xs font-semibold ${detail.active ? "bg-green-100 text-green-800" : "bg-yellow-100 text-yellow-900"}`}>
                {detail.active ? "Active" : "Archived"}
              </span>
              <span>{detail.activeMemberCount == null ? "—" : detail.activeMemberCount} active students</span>
              <span>{detail.assignmentCount} assigned problems</span>
              <span>{summary.completedProblemCount ?? 0} completed problems</span>
            </p>
          </div>
          <div className="relative">
            <button type="button" aria-expanded={menuOpen} aria-haspopup="menu" className="px-4 py-2 rounded bg-blue-600 text-white" onClick={() => setMenuOpen((open) => !open)}>
              Manage batch
            </button>
            {menuOpen && (
              <div role="menu" className="absolute right-0 z-10 mt-2 w-56 app-surface rounded shadow-lg p-1">
                {isAdmin && <button type="button" role="menuitem" className="block w-full text-left px-3 py-2 rounded hover:bg-gray-100 dark:hover:bg-gray-700" onClick={() => openManage("edit")}>Edit batch</button>}
                <button type="button" role="menuitem" className="block w-full text-left px-3 py-2 rounded hover:bg-gray-100 dark:hover:bg-gray-700" onClick={() => openManage("students")}>Add students</button>
                {isAdmin && <button type="button" role="menuitem" className="block w-full text-left px-3 py-2 rounded hover:bg-gray-100 dark:hover:bg-gray-700" onClick={() => openManage("coordinators")}>Manage coordinators</button>}
                <button type="button" role="menuitem" className="block w-full text-left px-3 py-2 rounded hover:bg-gray-100 dark:hover:bg-gray-700" onClick={() => openManage("assignments")}>Assign problems</button>
                <button type="button" role="menuitem" className="block w-full text-left px-3 py-2 rounded hover:bg-gray-100 dark:hover:bg-gray-700" onClick={() => openManage("reports")}>View reports</button>
                {isAdmin && detail.active && <button type="button" role="menuitem" className="block w-full text-left px-3 py-2 rounded hover:bg-gray-100 dark:hover:bg-gray-700" onClick={() => openManage("archive")}>Archive batch</button>}
                {isAdmin && !detail.active && <button type="button" role="menuitem" className="block w-full text-left px-3 py-2 rounded hover:bg-gray-100 dark:hover:bg-gray-700" onClick={() => { setMenuOpen(false); reactivate(); }}>Reactivate batch</button>}
              </div>
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
          <div className="grid gap-4">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              <div className="app-surface rounded p-3">Active students {detail.activeMemberCount ?? "—"}</div>
              <div className="app-surface rounded p-3">Assigned problems {detail.assignmentCount ?? 0}</div>
              <div className="app-surface rounded p-3">Completed problems {summary.completedProblemCount ?? 0}</div>
              <div className="app-surface rounded p-3">Completion {summary.completionPercent ?? "0"}%</div>
            </div>
            <div className="app-surface rounded p-4">
              <h2 className="font-semibold mb-2">Recent student activity</h2>
              {(report?.recentActivity || []).length === 0 ? (
                <p className="app-muted">No submission activity for this batch yet.</p>
              ) : (
                <ul className="space-y-2">
                  {(report.recentActivity || []).map((item, index) => (
                    <li key={`${item.userId}-${item.createdAt}-${index}`}>
                      {item.username || "Student"} · {item.result || "submission"} · {formatJoined(item.createdAt)}
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <p className="text-sm app-muted">Completion counts an assigned problem when every current member has an accepted submission for it. Deadlines and custom ordering are not stored on batch assignments.</p>
          </div>
        )}

        {tab === "members" && (
          <div>
            <div className="flex flex-wrap gap-2 mb-3">
              <button type="button" className="px-4 py-2 rounded bg-green-600 text-white" disabled={!detail.active || busy} onClick={() => setDialog("add")}>Add students</button>
              <button type="button" className="px-4 py-2 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white" disabled={!detail.active || busy} onClick={() => setDialog("import")}>Bulk import</button>
              <button type="button" className="px-4 py-2 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white" disabled={selectedMembers.length !== 1} onClick={() => setMembershipUser(members.find((user) => user.id === selectedMembers[0]) || null)}>Edit membership</button>
              <button type="button" className="px-4 py-2 rounded bg-red-600 text-white disabled:opacity-50" disabled={!detail.active || busy || !selectedMembers.length} onClick={removeSelectedMembers}>Remove students</button>
            </div>
            <div className="flex flex-col sm:flex-row gap-2 mb-3">
              <input
                value={memberFilter}
                onChange={(e) => setMemberFilter(e.target.value)}
                placeholder="Filter current members"
                aria-label="Filter current members"
                className="w-full max-w-md px-3 py-2 app-input rounded"
              />
              <button type="button" className="px-4 py-2 rounded bg-blue-600 text-white" onClick={() => { setMemberPage(0); loadMembers(0, memberFilter); }}>Search members</button>
              {memberSearchLimited && <p className="text-sm text-amber-800 dark:text-amber-200">This batch is large, so member search only checks the first 100 name matches.</p>}
              <button type="button" className="px-4 py-2 rounded bg-red-600 text-white disabled:opacity-50" disabled={!detail.active || busy || !selectedMembers.length} onClick={removeSelectedMembers}>Remove selected</button>
            </div>
            <div className="app-surface rounded p-4 overflow-x-auto">
              {members.length === 0 ? (memberFilter.trim() ? "No members match that search." : "No members yet.") : (
                <table className="w-full text-left">
                  <thead><tr><th className="py-2"></th><th>Username</th><th>Status</th><th>Joined</th><th>Assigned</th><th>Solved</th><th></th></tr></thead>
                  <tbody>
                    {members.map((user) => (
                      <tr key={user.id} className="border-t border-gray-200 dark:border-gray-700">
                        <td className="py-2">
                          <input type="checkbox" aria-label={`Select ${user.username}`} checked={selectedMembers.includes(user.id)} onChange={() => setSelectedMembers((current) => current.includes(user.id) ? current.filter((item) => item !== user.id) : [...current, user.id])} />
                        </td>
                        <td>{user.username || "Unknown account"}</td>
                        <td>{user.accountStatus || "ACTIVE"}{user.emailVerified === false ? " · unverified" : ""}</td>
                        <td>{formatJoined(user.addedAt)}</td>
                        <td>{user.assignedProblemCount ?? 0}</td>
                        <td>{user.solvedCount ?? 0}</td>
                        <td className="space-x-3">
                          <button type="button" className="text-blue-600 dark:text-blue-400" onClick={() => setMembershipUser(user)}>Membership</button>
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
            {problemSearchState === "error" && <p className="text-sm text-red-600 dark:text-red-400 mb-3" role="alert">Problem search failed.</p>}
            {problemSearchState === "done" && problemHits.length === 0 && <p className="text-sm mb-3">No matching problems.</p>}
            {isAdmin && <p className="text-sm mb-3"><Link to="/admin/add-problem" className="text-blue-600 dark:text-blue-400 hover:underline">Create a problem</Link></p>}
            <p className="text-sm app-muted mb-3">Assignments keep the order they were added. Availability windows and deadlines are not part of a batch assignment.</p>
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
                  <thead><tr><th className="py-2"></th><th>ID</th><th>Title</th><th>Difficulty</th><th>Solved</th><th>Tags</th><th></th></tr></thead>
                  <tbody>
                    {assignments.map((problem) => (
                      <tr key={problem.problemId} className="border-t border-gray-200 dark:border-gray-700">
                        <td className="py-2">
                          <input type="checkbox" aria-label={`Select ${problem.title || "problem"}`} checked={selectedAssignments.includes(problem.problemId)} onChange={() => setSelectedAssignments((current) => current.includes(problem.problemId) ? current.filter((item) => item !== problem.problemId) : [...current, problem.problemId])} />
                        </td>
                        <td className="py-2 font-mono">{displayAssignedProblemId(problem)}</td>
                        <td>{problem.title}</td>
                        <td>{problem.difficulty}</td>
                        <td>{problem.solvedCount == null ? "—" : `${problem.solvedCount}/${problem.memberCount ?? 0}`}</td>
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
            <div className="flex flex-col lg:flex-row flex-wrap gap-3 mb-4">
              <input
                value={studentFilter}
                onChange={(e) => setStudentFilter(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") { setReportPage(0); loadReport(0, e.target.value); } }}
                placeholder="Find student"
                aria-label="Filter report by student"
                className="px-3 py-2 app-input rounded"
              />
              <select aria-label="Filter report by problem" value={problemFilter} onChange={(e) => setProblemFilter(e.target.value)} className="px-3 py-2 app-input rounded">
                <option value="">All problems</option>
                {assignments.map((problem) => (
                  <option key={problem.problemId} value={problem.problemId}>{displayAssignedProblemId(problem)} {problem.title || ""}</option>
                ))}
              </select>
              <input type="date" aria-label="Activity from" value={activityFrom} onChange={(e) => setActivityFrom(e.target.value)} className="px-3 py-2 app-input rounded" />
              <input type="date" aria-label="Activity to" value={activityTo} onChange={(e) => setActivityTo(e.target.value)} className="px-3 py-2 app-input rounded" />
              <button className="px-4 py-2 bg-blue-600 text-white rounded" onClick={() => { setReportPage(0); loadReport(0); }}>Apply filters</button>
              <button className="px-4 py-2 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white" onClick={exportCsv}>Export CSV</button>
              <p className="text-sm app-muted self-center">CSV includes at most 500 students. A report scans at most 20,000 submissions; narrow it by problem or date if that limit is reached.</p>
            </div>
            {report && (report.students || []).length === 0 && (
              <p className="app-surface rounded p-4 mb-4">No matching student activity. Totals above still come from persisted submissions.</p>
            )}
            {report && (
              <>
                <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3 mb-4">
                  <div className="app-surface rounded p-3">Students {summary.studentCount}</div>
                  <div className="app-surface rounded p-3">Problems {summary.assignedProblemCount}</div>
                  <div className="app-surface rounded p-3">Solved {summary.solvedCells}</div>
                  <div className="app-surface rounded p-3">Attempted {summary.attemptedCells}</div>
                  <div className="app-surface rounded p-3">Unsolved {summary.unsolvedCells}</div>
                  <div className="app-surface rounded p-3">Submissions {summary.submissionCount ?? 0}</div>
                  <div className="app-surface rounded p-3">Accepted {summary.acceptedSubmissionCount ?? 0}</div>
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
        {dialog === "add" && (
          <div role="dialog" aria-modal="true" aria-labelledby="add-students-title" className="fixed inset-0 z-20 bg-black/40 flex items-end sm:items-center justify-center p-4">
            <div className="app-surface rounded p-4 w-full max-w-lg max-h-[80vh] overflow-y-auto">
              <h2 id="add-students-title" className="text-xl font-semibold mb-3">Add students</h2>
              <input value={userSearch} onChange={(e) => searchUsers(e.target.value)} placeholder="Username or email" aria-label="Search registered students" className="w-full px-3 py-2 app-input rounded mb-3" />
              {studentSearchMessage(userSearchState) && <p className={userSearchState === "error" ? "text-red-600 dark:text-red-400 mb-3" : "text-sm mb-3"} role={userSearchState === "error" ? "alert" : "status"}>{studentSearchMessage(userSearchState)}</p>}
              {userSearchState === "empty" && <p className="text-sm mb-3">New students create an account on the registration page. This batch does not send invitations.</p>}
              {userHits.map((user) => (
                <label key={user.id} className="flex items-center justify-between gap-3 py-2 border-b border-gray-200 dark:border-gray-700">
                  <span className="flex items-center gap-2">
                    <input type="checkbox" disabled={user.alreadyMember} checked={selectedAdds.includes(user.id)} onChange={() => setSelectedAdds((current) => current.includes(user.id) ? current.filter((item) => item !== user.id) : [...current, user.id])} />
                    <span>{user.username} <span className="app-muted">{user.email}</span></span>
                  </span>
                  <span className="text-xs">{studentHitStatus(user)}</span>
                </label>
              ))}
              {selectedAdds.length > 0 && <p className="text-sm mt-3">Selected: {selectedAdds.length}</p>}
              <div className="flex gap-2 mt-4">
                <button type="button" className="px-4 py-2 rounded bg-green-600 text-white disabled:opacity-50" disabled={!detail.active || busy || !selectedAdds.length} onClick={async () => { await addSelectedMembers(); setDialog(""); }}>Add selected</button>
                <button type="button" className="px-4 py-2 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white" onClick={() => setDialog("")}>Close</button>
              </div>
            </div>
          </div>
        )}
        {dialog === "import" && (
          <div role="dialog" aria-modal="true" aria-labelledby="import-students-title" className="fixed inset-0 z-20 bg-black/40 flex items-end sm:items-center justify-center p-4">
            <div className="app-surface rounded p-4 w-full max-w-lg">
              <h2 id="import-students-title" className="text-xl font-semibold mb-3">Bulk import</h2>
              <p className="text-sm app-muted mb-2">One username or email per line, up to 100. Unknown accounts are reported and not created.</p>
              <textarea value={importText} onChange={(e) => setImportText(e.target.value)} className="w-full min-h-32 px-3 py-2 app-input rounded" aria-label="Usernames to import" />
              {importResult && (
                <p className="text-sm mt-2">Added {importResult.added?.length || 0}. Already in batch {importResult.alreadyMembers?.length || 0}. Not found {importResult.notFound?.length || 0}.</p>
              )}
              <div className="flex gap-2 mt-4">
                <button type="button" className="px-4 py-2 rounded bg-green-600 text-white disabled:opacity-50" disabled={busy || !detail.active} onClick={importStudents}>Import</button>
                <button type="button" className="px-4 py-2 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white" onClick={() => setDialog("")}>Close</button>
              </div>
            </div>
          </div>
        )}
        {dialog === "edit" && isAdmin && (
          <div role="dialog" aria-modal="true" aria-labelledby="edit-batch-title" className="fixed inset-0 z-20 bg-black/40 flex items-end sm:items-center justify-center p-4">
              <form className="app-surface rounded p-4 w-full max-w-lg grid gap-3" onSubmit={async (event) => { event.preventDefault(); if (await saveDetails()) setDialog(""); }}>
              <h2 id="edit-batch-title" className="text-xl font-semibold">Edit batch</h2>
              <label className="text-sm font-medium" htmlFor="batch-name">Name</label>
              <input id="batch-name" value={editName} onChange={(e) => setEditName(e.target.value)} className="px-3 py-2 app-input rounded" required maxLength={120} />
              <label className="text-sm font-medium" htmlFor="batch-description">Description</label>
              <textarea id="batch-description" value={editDescription} onChange={(e) => setEditDescription(e.target.value)} className="px-3 py-2 app-input rounded min-h-24" maxLength={2000} />
              <div className="flex gap-2">
                <button type="submit" disabled={busy} className="px-4 py-2 rounded bg-blue-600 text-white disabled:opacity-50">Save</button>
                <button type="button" className="px-4 py-2 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white" onClick={() => setDialog("")}>Close</button>
              </div>
            </form>
          </div>
        )}
        {dialog === "coordinators" && isAdmin && (
          <div role="dialog" aria-modal="true" aria-labelledby="coordinators-title" className="fixed inset-0 z-20 bg-black/40 flex items-end sm:items-center justify-center p-4">
            <div className="app-surface rounded p-4 w-full max-w-lg max-h-[80vh] overflow-y-auto">
              <h2 id="coordinators-title" className="text-xl font-semibold mb-3">Batch coordinators</h2>
              <p className="text-sm app-muted mb-3">A coordinator can manage this batch only. This does not grant global admin access.</p>
              <input value={userSearch} onChange={(e) => searchUsers(e.target.value)} placeholder="Find a registered user" aria-label="Search coordinator candidates" className="w-full px-3 py-2 app-input rounded mb-2" />
              {studentSearchMessage(userSearchState) && <p className="text-sm mb-2">{studentSearchMessage(userSearchState)}</p>}
              {userHits.map((user) => (
                <label key={user.id} className="flex items-center gap-2 py-1">
                  <input type="radio" name="coordinator" checked={selectedCoordinatorId === user.id} onChange={() => setSelectedCoordinatorId(user.id)} />
                  <span>{user.username} <span className="app-muted">{user.email}</span></span>
                </label>
              ))}
              <label className="text-sm font-medium mt-3 block" htmlFor="institution">Institution</label>
              <input id="institution" value={institution} onChange={(e) => setInstitution(e.target.value)} maxLength={120} className="w-full px-3 py-2 app-input rounded mb-3" />
              <button type="button" className="px-4 py-2 rounded bg-green-600 text-white disabled:opacity-50" disabled={busy || !selectedCoordinatorId} onClick={grantCoordinator}>Grant access</button>
              <ul className="mt-4 space-y-2">
                {coordinators.length === 0 && <li className="app-muted">No coordinators for this batch.</li>}
                {coordinators.map((coordinator) => (
                  <li key={coordinator.userId} className="flex justify-between gap-2">
                    <span>{coordinator.username || coordinator.userId}{coordinator.institution ? ` · ${coordinator.institution}` : ""}</span>
                    <button type="button" className="text-red-600 dark:text-red-400" onClick={() => revokeCoordinator(coordinator.userId, coordinator.username)}>Revoke</button>
                  </li>
                ))}
              </ul>
              <button type="button" className="mt-4 px-4 py-2 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white" onClick={() => setDialog("")}>Close</button>
            </div>
          </div>
        )}
        {membershipUser && (
          <div role="dialog" aria-modal="true" aria-labelledby="membership-title" className="fixed inset-0 z-20 bg-black/40 flex items-end sm:items-center justify-center p-4">
            <div className="app-surface rounded p-4 w-full max-w-lg">
              <h2 id="membership-title" className="text-xl font-semibold mb-2">Membership</h2>
              <p>{membershipUser.username || "Unknown account"}</p>
              <p className="text-sm app-muted">Joined {formatJoined(membershipUser.addedAt)}. Status {membershipUser.accountStatus || "ACTIVE"}.</p>
              <p className="text-sm mt-2">Removing this student deletes only the batch membership. The CoderzClub account and past submissions stay.</p>
              <div className="flex gap-2 mt-4">
                <button type="button" className="px-4 py-2 rounded bg-red-600 text-white disabled:opacity-50" disabled={!detail.active || busy} onClick={() => { removeMember(membershipUser.id, membershipUser.username); setMembershipUser(null); }}>Remove from batch</button>
                <button type="button" className="px-4 py-2 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white" onClick={() => setMembershipUser(null)}>Close</button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default AdminBatchPage;
