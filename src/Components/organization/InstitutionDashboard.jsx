import React, { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../apiClient";
import { useOrganization } from "../../context/OrganizationContext";
import { customDomainReadinessNotes } from "../../organization/branding.js";

const ROLE_OPTIONS = ["ORG_ADMIN", "INSTRUCTOR", "STUDENT"];

export default function InstitutionDashboard() {
  const {
    activeOrganization,
    activeOrganizationId,
    branding,
    canAdministerActive,
    canManageBatches,
    cacheEpoch,
    refreshOrganizations,
  } = useOrganization();
  const [members, setMembers] = useState([]);
  const [batches, setBatches] = useState([]);
  const [contests, setContests] = useState([]);
  const [error, setError] = useState("");
  const [memberUserId, setMemberUserId] = useState("");
  const [memberRole, setMemberRole] = useState("STUDENT");
  const [batchName, setBatchName] = useState("");
  const [enrollBatchId, setEnrollBatchId] = useState("");
  const [enrollUserId, setEnrollUserId] = useState("");
  const [enrollUsername, setEnrollUsername] = useState("");
  const [removeStudentUserId, setRemoveStudentUserId] = useState("");
  const [coordinatorUserId, setCoordinatorUserId] = useState("");
  const [notice, setNotice] = useState("");
  const [reportPreview, setReportPreview] = useState(null);
  const [settings, setSettings] = useState({
    name: "",
    displayName: "",
    logoUrl: "",
    faviconUrl: "",
    primaryColor: "",
    secondaryColor: "",
    landingTagline: "",
    emailFromName: "",
  });
  const [brandPreview, setBrandPreview] = useState(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!activeOrganization) {
      return;
    }
    setSettings({
      name: activeOrganization.name || "",
      displayName: activeOrganization.branding?.displayName || "",
      logoUrl: activeOrganization.branding?.logoUrl || "",
      faviconUrl: activeOrganization.branding?.faviconUrl || "",
      primaryColor: activeOrganization.branding?.primaryColor || "",
      secondaryColor: activeOrganization.branding?.secondaryColor || "",
      landingTagline: activeOrganization.branding?.landingTagline || "",
      emailFromName: activeOrganization.branding?.emailFromName || "",
    });
  }, [activeOrganization, cacheEpoch]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!activeOrganizationId) {
        setMembers([]);
        setBatches([]);
        setContests([]);
        return;
      }
      setError("");
      try {
        const tasks = [];
        if (canAdministerActive) {
          tasks.push(api.organizations.listMembers(activeOrganizationId).then((res) => {
            if (!cancelled) setMembers(res.data?.members || []);
          }));
        }
        if (canManageBatches) {
          tasks.push(api.adminBatches.list({ page: 0, size: 20 }).then((res) => {
            if (!cancelled) setBatches(res.data?.items || res.data?.batches || []);
          }));
          tasks.push(api.adminContests.list({ page: 0, size: 20 }).then((res) => {
            if (!cancelled) setContests(res.data?.items || res.data?.contests || []);
          }));
        }
        await Promise.all(tasks);
      } catch (err) {
        if (!cancelled) {
          setError(apiErrorMessage(err) || "Unable to load institution data.");
        }
      }
    }
    load();
    return () => { cancelled = true; };
  }, [activeOrganizationId, canAdministerActive, canManageBatches, cacheEpoch]);

  const domainNotes = useMemo(() => customDomainReadinessNotes(), []);

  if (!activeOrganizationId) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-10">
        <h1 className="text-2xl font-semibold text-gray-900 dark:text-white">Institution</h1>
        <p className="mt-2 text-gray-600 dark:text-gray-300">
          You are not a member of an institution yet. Public CoderzClub practice remains available.
        </p>
      </div>
    );
  }

  async function refreshMembers() {
    const refreshed = await api.organizations.listMembers(activeOrganizationId);
    setMembers(refreshed.data?.members || []);
  }

  async function addMember(event) {
    event.preventDefault();
    if (!canAdministerActive || !memberUserId.trim()) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await api.organizations.addMember(activeOrganizationId, {
        userId: memberUserId.trim(),
        roles: [memberRole],
      });
      setMemberUserId("");
      await refreshMembers();
      setNotice("Member added.");
    } catch (err) {
      setError(apiErrorMessage(err) || "Could not add member.");
    } finally {
      setSaving(false);
    }
  }

  async function updateMemberRole(userId, role) {
    if (!canAdministerActive || !userId) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await api.organizations.updateMemberRoles(activeOrganizationId, userId, { roles: [role] });
      await refreshMembers();
      setNotice("Member roles updated.");
    } catch (err) {
      setError(apiErrorMessage(err) || "Could not update member roles.");
    } finally {
      setSaving(false);
    }
  }

  async function removeMember(userId) {
    if (!canAdministerActive || !userId) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await api.organizations.removeMember(activeOrganizationId, userId);
      await refreshMembers();
      setNotice("Member removed.");
    } catch (err) {
      setError(apiErrorMessage(err) || "Could not remove member.");
    } finally {
      setSaving(false);
    }
  }

  async function createBatch(event) {
    event.preventDefault();
    if (!canAdministerActive || !batchName.trim()) return;
    setSaving(true);
    setError("");
    try {
      const created = await api.adminBatches.create({ name: batchName.trim() });
      setBatchName("");
      const newId = created.data?.id;
      if (newId) {
        setEnrollBatchId(newId);
      }
      const refreshed = await api.adminBatches.list({ page: 0, size: 20 });
      setBatches(refreshed.data?.items || refreshed.data?.batches || []);
    } catch (err) {
      setError(apiErrorMessage(err) || "Could not create batch.");
    } finally {
      setSaving(false);
    }
  }

  async function enrollStudent(event) {
    event.preventDefault();
    if (!canAdministerActive || !enrollBatchId) return;
    if (!enrollUserId.trim() && !enrollUsername.trim()) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      if (enrollUserId.trim()) {
        await api.adminBatches.addMembers(enrollBatchId, { userIds: [enrollUserId.trim()] });
        setEnrollUserId("");
      }
      if (enrollUsername.trim()) {
        await api.adminBatches.importMembers(enrollBatchId, { usernames: [enrollUsername.trim()] });
        setEnrollUsername("");
      }
      setNotice("Student enrollment updated.");
    } catch (err) {
      setError(apiErrorMessage(err) || "Could not enroll student in batch.");
    } finally {
      setSaving(false);
    }
  }

  async function removeStudent(event) {
    event.preventDefault();
    if (!canAdministerActive || !enrollBatchId || !removeStudentUserId.trim()) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await api.adminBatches.removeMembers(enrollBatchId, { userIds: [removeStudentUserId.trim()] });
      setRemoveStudentUserId("");
      setNotice("Student removed from batch.");
    } catch (err) {
      setError(apiErrorMessage(err) || "Could not remove student from batch.");
    } finally {
      setSaving(false);
    }
  }

  async function grantCoordinator(event) {
    event.preventDefault();
    if (!canAdministerActive || !enrollBatchId || !coordinatorUserId.trim()) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await api.adminBatches.grantCoordinator(enrollBatchId, { userId: coordinatorUserId.trim() });
      setCoordinatorUserId("");
      setNotice("Batch coordinator granted.");
    } catch (err) {
      setError(apiErrorMessage(err) || "Could not grant coordinator.");
    } finally {
      setSaving(false);
    }
  }

  async function loadBatchReport(batchId) {
    if (!canManageBatches || !batchId) return;
    setSaving(true);
    setError("");
    setReportPreview(null);
    try {
      const res = await api.adminBatches.report(batchId);
      setReportPreview({ batchId, data: res.data });
    } catch (err) {
      setError(apiErrorMessage(err) || "Could not load batch report.");
    } finally {
      setSaving(false);
    }
  }

  async function exportBatchReportCsv(batchId) {
    if (!canManageBatches || !batchId) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const res = await api.adminBatches.reportCsv(batchId);
      const blob = res.data instanceof Blob ? res.data : new Blob([res.data], { type: "text/csv" });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `batch-${batchId}-report.csv`;
      anchor.click();
      URL.revokeObjectURL(url);
      setNotice("Batch report CSV downloaded.");
    } catch (err) {
      setError(apiErrorMessage(err) || "Could not export batch report.");
    } finally {
      setSaving(false);
    }
  }

  async function saveSettings(event) {
    event.preventDefault();
    if (!canAdministerActive) return;
    setSaving(true);
    setError("");
    try {
      await api.organizations.update(activeOrganizationId, {
        name: settings.name,
        branding: {
          displayName: settings.displayName,
          logoUrl: settings.logoUrl,
          faviconUrl: settings.faviconUrl,
          primaryColor: settings.primaryColor,
          secondaryColor: settings.secondaryColor,
          landingTagline: settings.landingTagline,
          emailFromName: settings.emailFromName,
        },
      });
      await refreshOrganizations();
      setNotice("Institution branding saved.");
    } catch (err) {
      setError(apiErrorMessage(err) || "Could not save institution settings.");
    } finally {
      setSaving(false);
    }
  }

  async function previewBranding() {
    if (!canAdministerActive) return;
    setSaving(true);
    setError("");
    try {
      const res = await api.organizations.previewBranding(activeOrganizationId);
      setBrandPreview(res.data);
      setNotice("Branding preview loaded.");
    } catch (err) {
      setError(apiErrorMessage(err) || "Could not load branding preview.");
    } finally {
      setSaving(false);
    }
  }

  async function resetBranding() {
    if (!canAdministerActive) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await api.organizations.resetBranding(activeOrganizationId);
      await refreshOrganizations();
      setBrandPreview(null);
      setNotice("Branding reset to CoderzClub defaults.");
    } catch (err) {
      setError(apiErrorMessage(err) || "Could not reset branding.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="max-w-6xl mx-auto px-4 py-8 space-y-10" data-testid="institution-dashboard">
      <header className="space-y-2">
        <p className="text-sm uppercase tracking-wide text-gray-500 dark:text-gray-400">Institution</p>
        <h1
          className="text-3xl font-semibold"
          style={{ color: "var(--org-brand-primary, #2563eb)" }}
          data-testid="institution-brand-name"
        >
          {branding.displayName}
        </h1>
        <p className="text-gray-600 dark:text-gray-300">
          Active organization context is enforced by the server. Switching institutions refreshes
          permissions, navigation, and cached institution data.
        </p>
        {branding.logoUrl ? (
          <img src={branding.logoUrl} alt="" className="h-12 w-auto object-contain" />
        ) : null}
      </header>

      {error ? (
        <div className="rounded-md border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-800 dark:border-red-700 dark:bg-red-950/40 dark:text-red-100" role="alert">
          {error}
        </div>
      ) : null}
      {notice ? (
        <div className="rounded-md border border-green-300 bg-green-50 px-3 py-2 text-sm text-green-900 dark:border-green-700 dark:bg-green-950/40 dark:text-green-100" data-testid="institution-notice">
          {notice}
        </div>
      ) : null}

      {canAdministerActive ? (
        <section className="space-y-4" aria-labelledby="institution-members">
          <h2 id="institution-members" className="text-xl font-semibold text-gray-900 dark:text-white">Members</h2>
          <form onSubmit={addMember} className="flex flex-col sm:flex-row gap-2">
            <input
              className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900"
              placeholder="User id"
              value={memberUserId}
              onChange={(event) => setMemberUserId(event.target.value)}
              aria-label="Member user id"
            />
            <select
              className="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900"
              value={memberRole}
              onChange={(event) => setMemberRole(event.target.value)}
              aria-label="Member role"
            >
              {ROLE_OPTIONS.map((role) => (
                <option key={role} value={role}>{role}</option>
              ))}
            </select>
            <button
              type="submit"
              disabled={saving}
              className="rounded-md px-4 py-2 text-sm font-medium text-white"
              style={{ backgroundColor: "var(--org-brand-primary, #2563eb)" }}
            >
              Add member
            </button>
          </form>
          <ul className="divide-y divide-gray-200 dark:divide-gray-700 border border-gray-200 dark:border-gray-700 rounded-md" data-testid="institution-members-list">
            {members.map((member) => (
              <li key={member.id || member.userId} className="px-3 py-2 text-sm flex flex-wrap items-center justify-between gap-3">
                <span>{member.username || member.userId}</span>
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-gray-500">{(member.roles || []).join(", ")}</span>
                  <select
                    className="rounded-md border border-gray-300 px-2 py-1 text-xs dark:border-gray-600 dark:bg-gray-900"
                    aria-label={`Role for ${member.username || member.userId}`}
                    value={(member.roles || []).includes("ORG_OWNER") ? "ORG_OWNER" : (member.roles || [])[0] || "STUDENT"}
                    onChange={(event) => {
                      if (event.target.value === "ORG_OWNER") return;
                      updateMemberRole(member.userId, event.target.value);
                    }}
                    disabled={saving || (member.roles || []).includes("ORG_OWNER")}
                  >
                    {ROLE_OPTIONS.map((role) => (
                      <option key={role} value={role}>{role}</option>
                    ))}
                  </select>
                  <button
                    type="button"
                    className="text-xs underline text-red-700 dark:text-red-300"
                    disabled={saving || (member.roles || []).includes("ORG_OWNER")}
                    onClick={() => removeMember(member.userId)}
                  >
                    Remove
                  </button>
                </div>
              </li>
            ))}
            {!members.length ? <li className="px-3 py-2 text-sm text-gray-500">No members loaded.</li> : null}
          </ul>
        </section>
      ) : null}

      {canManageBatches ? (
        <section className="space-y-4" aria-labelledby="institution-batches">
          <div className="flex items-center justify-between gap-3">
            <h2 id="institution-batches" className="text-xl font-semibold text-gray-900 dark:text-white">Batches</h2>
            <Link to="/my/batches" className="text-sm underline">Open batch tools</Link>
          </div>
          {canAdministerActive ? (
            <>
              <form onSubmit={createBatch} className="flex flex-col sm:flex-row gap-2">
                <input
                  className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900"
                  placeholder="New batch name"
                  value={batchName}
                  onChange={(event) => setBatchName(event.target.value)}
                  aria-label="New batch name"
                />
                <button
                  type="submit"
                  disabled={saving}
                  className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white dark:bg-gray-100 dark:text-gray-900"
                >
                  Create batch
                </button>
              </form>
              <form onSubmit={enrollStudent} className="flex flex-col sm:flex-row gap-2" data-testid="institution-enroll-form">
                <select
                  className="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900"
                  value={enrollBatchId}
                  onChange={(event) => setEnrollBatchId(event.target.value)}
                  aria-label="Batch for enrollment"
                >
                  <option value="">Select batch</option>
                  {batches.map((batch) => (
                    <option key={batch.id} value={batch.id}>{batch.name}</option>
                  ))}
                </select>
                <input
                  className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900"
                  placeholder="Student user id"
                  value={enrollUserId}
                  onChange={(event) => setEnrollUserId(event.target.value)}
                  aria-label="Student user id"
                />
                <input
                  className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900"
                  placeholder="Or username"
                  value={enrollUsername}
                  onChange={(event) => setEnrollUsername(event.target.value)}
                  aria-label="Student username"
                />
                <button
                  type="submit"
                  disabled={saving || !enrollBatchId}
                  className="rounded-md px-4 py-2 text-sm font-medium text-white"
                  style={{ backgroundColor: "var(--org-brand-primary, #2563eb)" }}
                >
                  Enroll student
                </button>
              </form>
              <form onSubmit={removeStudent} className="flex flex-col sm:flex-row gap-2" data-testid="institution-remove-student-form">
                <input
                  className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900"
                  placeholder="Student user id to remove"
                  value={removeStudentUserId}
                  onChange={(event) => setRemoveStudentUserId(event.target.value)}
                  aria-label="Student user id to remove"
                />
                <button
                  type="submit"
                  disabled={saving || !enrollBatchId || !removeStudentUserId.trim()}
                  className="rounded-md border border-red-600 px-4 py-2 text-sm font-medium text-red-700 dark:text-red-300"
                >
                  Remove student
                </button>
              </form>
              <form onSubmit={grantCoordinator} className="flex flex-col sm:flex-row gap-2" data-testid="institution-coordinator-form">
                <input
                  className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900"
                  placeholder="Instructor user id for coordinator grant"
                  value={coordinatorUserId}
                  onChange={(event) => setCoordinatorUserId(event.target.value)}
                  aria-label="Coordinator user id"
                />
                <button
                  type="submit"
                  disabled={saving || !enrollBatchId || !coordinatorUserId.trim()}
                  className="rounded-md bg-gray-800 px-4 py-2 text-sm font-medium text-white dark:bg-gray-200 dark:text-gray-900"
                >
                  Grant coordinator
                </button>
              </form>
            </>
          ) : null}
          <ul className="space-y-2">
            {batches.map((batch) => (
              <li key={batch.id} className="rounded-md border border-gray-200 dark:border-gray-700 px-3 py-2 text-sm flex flex-wrap items-center justify-between gap-2">
                <Link
                  to={`/admin/batches/${batch.id}`}
                  className="hover:underline"
                >
                  {batch.name}
                </Link>
                <div className="flex flex-wrap gap-3">
                  {canAdministerActive ? (
                    <Link
                      to={`/institution/contests/new?batchId=${encodeURIComponent(batch.id)}`}
                      className="text-xs underline"
                      data-testid={`batch-contest-${batch.id}`}
                    >
                      Contest for batch
                    </Link>
                  ) : null}
                  <button
                    type="button"
                    className="text-xs underline"
                    onClick={() => loadBatchReport(batch.id)}
                    data-testid={`batch-report-${batch.id}`}
                  >
                    View report
                  </button>
                  <button
                    type="button"
                    className="text-xs underline"
                    onClick={() => exportBatchReportCsv(batch.id)}
                    data-testid={`batch-report-csv-${batch.id}`}
                  >
                    Export CSV
                  </button>
                </div>
              </li>
            ))}
            {!batches.length ? <li className="text-sm text-gray-500">No batches in this institution yet.</li> : null}
          </ul>
          {reportPreview ? (
            <pre
              className="overflow-auto rounded-md border border-gray-200 bg-gray-50 p-3 text-xs dark:border-gray-700 dark:bg-gray-900"
              data-testid="institution-batch-report"
            >
              {JSON.stringify(reportPreview.data, null, 2)}
            </pre>
          ) : null}
        </section>
      ) : null}

      {canManageBatches ? (
        <section className="space-y-4" aria-labelledby="institution-contests">
          <div className="flex items-center justify-between gap-3">
            <h2 id="institution-contests" className="text-xl font-semibold text-gray-900 dark:text-white">Contests</h2>
            {canAdministerActive ? (
              <Link to="/institution/contests/new" className="text-sm underline">Schedule contest</Link>
            ) : null}
          </div>
          <ul className="space-y-2">
            {contests.map((contest) => (
              <li key={contest.id}>
                <div className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-gray-200 px-3 py-2 text-sm dark:border-gray-700">
                  <Link to={`/contests/${contest.id}`} className="hover:underline">
                    {contest.title || contest.name}
                  </Link>
                  {canAdministerActive ? (
                    <Link
                      to={`/institution/contests/${contest.id}`}
                      className="text-xs underline"
                      data-testid={`manage-contest-${contest.id}`}
                    >
                      Manage
                    </Link>
                  ) : null}
                </div>
              </li>
            ))}
            {!contests.length ? <li className="text-sm text-gray-500">No contests scheduled for this institution.</li> : null}
          </ul>
        </section>
      ) : (
        <section className="space-y-2">
          <h2 className="text-xl font-semibold text-gray-900 dark:text-white">Student workspace</h2>
          <p className="text-sm text-gray-600 dark:text-gray-300">
            Use Contests and My batches for institution-specific practice. Rankings and reports remain
            limited to contests and batches you are eligible for.
          </p>
          <div className="flex gap-3 text-sm">
            <Link className="underline" to="/contests">Contests</Link>
            <Link className="underline" to="/my/batches">My batches</Link>
          </div>
        </section>
      )}

      {canAdministerActive ? (
        <section className="space-y-4" aria-labelledby="institution-settings">
          <h2 id="institution-settings" className="text-xl font-semibold text-gray-900 dark:text-white">Settings & branding</h2>
          <form onSubmit={saveSettings} className="grid gap-3 sm:grid-cols-2">
            {[
              ["name", "Institution name"],
              ["displayName", "Display name"],
              ["landingTagline", "Landing tagline"],
              ["emailFromName", "Email from name"],
              ["logoUrl", "Logo URL (https image, no SVG)"],
              ["faviconUrl", "Favicon URL (https image, no SVG)"],
              ["primaryColor", "Primary color (#hex)"],
              ["secondaryColor", "Secondary color (#hex)"],
            ].map(([key, label]) => (
              <label key={key} className="text-sm space-y-1">
                <span className="text-gray-600 dark:text-gray-300">{label}</span>
                <input
                  className="w-full rounded-md border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-900"
                  value={settings[key]}
                  onChange={(event) => setSettings((prev) => ({ ...prev, [key]: event.target.value }))}
                />
              </label>
            ))}
            <div className="sm:col-span-2 flex flex-wrap gap-3">
              <button
                type="submit"
                disabled={saving}
                className="rounded-md px-4 py-2 text-sm font-medium text-white"
                style={{ backgroundColor: "var(--org-brand-primary, #2563eb)" }}
              >
                Save settings
              </button>
              <button
                type="button"
                disabled={saving}
                onClick={previewBranding}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm dark:border-gray-600"
                data-testid="institution-branding-preview"
              >
                Preview branding
              </button>
              <button
                type="button"
                disabled={saving}
                onClick={resetBranding}
                className="rounded-md border border-red-600 px-4 py-2 text-sm text-red-700 dark:text-red-300"
                data-testid="institution-branding-reset"
              >
                Reset to defaults
              </button>
            </div>
          </form>
          {brandPreview ? (
            <pre
              className="overflow-auto rounded-md border border-gray-200 bg-gray-50 p-3 text-xs dark:border-gray-700 dark:bg-gray-900"
              data-testid="institution-branding-preview-json"
            >
              {JSON.stringify(brandPreview, null, 2)}
            </pre>
          ) : null}
          <div className="rounded-md border border-amber-300 bg-amber-50 px-3 py-3 text-sm text-amber-950 dark:border-amber-700 dark:bg-amber-950/30 dark:text-amber-100">
            <p className="font-medium">Custom domains are not enabled yet</p>
            <ul className="mt-2 list-disc pl-5 space-y-1">
              {domainNotes.map((note) => <li key={note}>{note}</li>)}
            </ul>
          </div>
        </section>
      ) : null}
    </div>
  );
}
