import React, { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { displayAssignedProblemId, batchStatusLabel, batchStatusSymbol } from "../admin/batchProgress";

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
  const [userSearch, setUserSearch] = useState("");
  const [userHits, setUserHits] = useState([]);
  const [problemSearch, setProblemSearch] = useState("");
  const [problemHits, setProblemHits] = useState([]);
  const [studentFilter, setStudentFilter] = useState("");
  const [reportPage, setReportPage] = useState(0);
  const [busy, setBusy] = useState(false);
  const reportSeq = useRef(0);
  const userSearchSeq = useRef(0);
  const problemSearchSeq = useRef(0);

  const loadDetail = async () => {
    const response = await fetch(`/api/admin/batches/${id}`, { headers: authHeaders() });
    if (!response.ok) throw new Error("Failed to load batch");
    setDetail(await response.json());
  };

  const loadMembers = async () => {
    const response = await fetch(`/api/admin/batches/${id}/members?page=0&size=50`, { headers: authHeaders() });
    if (response.ok) {
      const data = await response.json();
      setMembers(data.members || []);
    }
  };

  const loadAssignments = async () => {
    const response = await fetch(`/api/admin/batches/${id}/assignments?page=0&size=80`, { headers: authHeaders() });
    if (response.ok) {
      const data = await response.json();
      setAssignments(data.assignments || []);
    }
  };

  const loadReport = async (page = 0, student = studentFilter) => {
    const seq = ++reportSeq.current;
    const params = new URLSearchParams({ page: String(page), size: "20" });
    if (student.trim()) params.set("student", student.trim());
    const response = await fetch(`/api/admin/batches/${id}/report?${params}`, { headers: authHeaders() });
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
      return;
    }
    const seq = ++userSearchSeq.current;
    const response = await fetch(`/api/admin/batches/users?search=${encodeURIComponent(value.trim())}&size=20`, {
      headers: authHeaders()
    });
    if (seq !== userSearchSeq.current) return;
    if (response.ok) {
      const data = await response.json();
      setUserHits(data.users || []);
    }
  };

  const searchProblems = async (value) => {
    setProblemSearch(value);
    if (!value.trim()) {
      problemSearchSeq.current += 1;
      setProblemHits([]);
      return;
    }
    const seq = ++problemSearchSeq.current;
    const response = await fetch(`/api/problems?search=${encodeURIComponent(value.trim())}&size=20`);
    if (seq !== problemSearchSeq.current) return;
    if (response.ok) {
      const data = await response.json();
      setProblemHits(data.problems || []);
    }
  };

  const addMember = async (userId) => {
    if (!detail?.active || busy) return;
    setBusy(true);
    setError("");
    const response = await fetch(`/api/admin/batches/${id}/members`, {
      method: "POST",
      headers: authHeaders(true),
      body: JSON.stringify({ userIds: [userId] })
    });
    setBusy(false);
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(data.error || "Could not add user");
      return;
    }
    await loadMembers();
    await loadDetail();
  };

  const removeMember = async (userId, username) => {
    if (!window.confirm(`Remove ${username} from this batch? Submissions will not be deleted.`)) return;
    await fetch(`/api/admin/batches/${id}/members/${userId}`, { method: "DELETE", headers: authHeaders() });
    await loadMembers();
    await loadDetail();
  };

  const assignProblem = async (problemId) => {
    if (!detail?.active || busy) return;
    setBusy(true);
    setError("");
    const response = await fetch(`/api/admin/batches/${id}/assignments`, {
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
    await fetch(`/api/admin/batches/${id}/assignments/${problemId}`, { method: "DELETE", headers: authHeaders() });
    await loadAssignments();
    await loadDetail();
  };

  const archive = async () => {
    if (!window.confirm("Archive this batch? Users, problems, and submissions stay intact.")) return;
    const response = await fetch(`/api/admin/batches/${id}/archive`, { method: "POST", headers: authHeaders() });
    if (response.ok) setDetail(await response.json());
  };

  const exportCsv = async () => {
    setError("");
    const params = new URLSearchParams();
    if (studentFilter.trim()) params.set("student", studentFilter.trim());
    const response = await fetch(`/api/admin/batches/${id}/report.csv?${params}`, { headers: authHeaders() });
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
            <p className="text-sm text-gray-500 dark:text-gray-500 mt-1">
              {detail.active ? "Active" : "Archived"} · {detail.memberCount} students · {detail.assignmentCount} problems
            </p>
          </div>
          {detail.active && (
            <button onClick={archive} className="px-4 py-2 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white">Archive batch</button>
          )}
        </div>
        {!detail.active && (
          <div className="mb-4 text-yellow-800 dark:text-yellow-300">This batch is archived. Reports remain available; membership and assignments cannot be changed.</div>
        )}
        {error && <div className="mb-4 text-red-600 dark:text-red-400">{error}</div>}
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
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <div className="app-surface rounded p-4">Students: {detail.memberCount}</div>
            <div className="app-surface rounded p-4">Assigned problems: {detail.assignmentCount}</div>
            <div className="app-surface rounded p-4">Status: {detail.active ? "Active" : "Archived"}</div>
          </div>
        )}

        {tab === "members" && (
          <div>
            <input
              value={userSearch}
              onChange={(e) => searchUsers(e.target.value)}
              placeholder="Search users by username or email"
              className="w-full max-w-md px-3 py-2 app-input rounded mb-4"
            />
            {userHits.length > 0 && (
              <div className="app-surface rounded p-3 mb-4">
                {userHits.map((user) => (
                  <div key={user.id} className="flex justify-between py-2 border-b border-gray-200 dark:border-gray-700 last:border-0">
                    <span>{user.username} <span className="app-muted">{user.email}</span></span>
                    <button className="text-green-700 dark:text-green-400" disabled={!detail.active || busy} onClick={() => addMember(user.id)}>Add</button>
                  </div>
                ))}
              </div>
            )}
            <div className="app-surface rounded p-4 overflow-x-auto">
              {members.length === 0 ? "No members yet." : (
                <table className="w-full text-left">
                  <thead><tr><th className="py-2">Username</th><th>Email</th><th></th></tr></thead>
                  <tbody>
                    {members.map((user) => (
                      <tr key={user.id} className="border-t border-gray-200 dark:border-gray-700">
                        <td className="py-2">{user.username}</td>
                        <td>{user.email}</td>
                        <td>
                          <button className="text-red-600 dark:text-red-400" disabled={!detail.active} onClick={() => removeMember(user.id, user.username)}>Remove</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
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
            {problemHits.length > 0 && (
              <div className="app-surface rounded p-3 mb-4">
                {problemHits.map((problem) => (
                  <div key={problem.id} className="flex justify-between py-2 border-b border-gray-200 dark:border-gray-700 last:border-0">
                    <span>
                      <span className="font-mono mr-2">{displayAssignedProblemId(problem)}</span>
                      {problem.title} <span className="app-muted">{problem.difficulty}</span>
                    </span>
                    <button className="text-green-700 dark:text-green-400" disabled={!detail.active || busy} onClick={() => assignProblem(problem.id)}>Assign</button>
                  </div>
                ))}
              </div>
            )}
            <div className="app-surface rounded p-4 overflow-x-auto">
              {assignments.length === 0 ? "No assignments yet." : (
                <table className="w-full text-left">
                  <thead><tr><th className="py-2">ID</th><th>Title</th><th>Difficulty</th><th>Tags</th><th></th></tr></thead>
                  <tbody>
                    {assignments.map((problem) => (
                      <tr key={problem.problemId} className="border-t border-gray-200 dark:border-gray-700">
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
                              {batchStatusSymbol(cell.status)}
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
