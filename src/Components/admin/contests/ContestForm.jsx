import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { api, apiErrorMessage } from "../../../apiClient";
import {
  buildContestPatchBody,
  buildContestWriteBody,
  canDelete,
  canEditLiveLimited,
  canEditScheduleAndSet,
  canPublish,
  canUnpublish,
  isReadOnly,
  validateContestForm,
} from "../../../contests/contestLifecycle";
import { browserTimeZone, datetimeLocalToIso, instantToDatetimeLocal } from "../../../contests/contestTime";
import ContestBatchSelector from "./ContestBatchSelector";
import ContestProblemSelector from "./ContestProblemSelector";
import ContestStatusBadge from "../../contests/ContestStatusBadge";

const emptyForm = {
  title: "",
  description: "",
  startLocal: "",
  endLocal: "",
  audienceType: "BATCHES",
  batches: [],
  problems: [],
};

async function loadSelectedBatches(batchIds) {
  if (!batchIds.length) {
    return [];
  }
  const listed = await api.adminBatches.list({ page: 0, size: 50 });
  const byId = new Map((listed.data?.batches || []).map((batch) => [batch.id, batch]));
  const missing = batchIds.filter((id) => !byId.has(id));
  await Promise.all(missing.map(async (id) => {
    try {
      const batch = await api.adminBatches.get(id);
      byId.set(id, batch.data);
    } catch {
      byId.set(id, { id, name: id });
    }
  }));
  return batchIds.map((id) => {
    const batch = byId.get(id) || { id, name: id };
    return { id: batch.id || id, name: batch.name || id, active: batch.active };
  });
}

export default function ContestForm() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const creating = !id;
  const [form, setForm] = useState(emptyForm);
  const [contest, setContest] = useState(null);
  const [loading, setLoading] = useState(!creating);
  const [saving, setSaving] = useState(false);
  const [acting, setActing] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const timeZone = browserTimeZone();
  const status = contest?.status;
  const scheduleLocked = !creating && !canEditScheduleAndSet(status);
  const liveLimited = canEditLiveLimited(status);
  const readOnly = isReadOnly(status);

  useEffect(() => {
    if (typeof location.state?.notice === "string" && location.state.notice) {
      setNotice(location.state.notice);
    }
  }, [location.state]);

  useEffect(() => {
    if (creating) {
      return undefined;
    }
    let cancelled = false;
    setLoading(true);
    api.adminContests.get(id)
      .then(async (response) => {
        if (cancelled) {
          return;
        }
        const data = response.data;
        setContest(data);
        const summaries = Array.isArray(data.problems) ? data.problems : [];
        const ids = Array.isArray(data.problemIds) && data.problemIds.length > 0
          ? data.problemIds
          : summaries.map((problem) => problem.id).filter(Boolean);
        let problems = summaries.map((problem) => ({
          id: problem.id,
          numericId: problem.numericId,
          title: problem.title,
          difficulty: problem.difficulty,
          availability: "PUBLIC",
        }));
        if (ids.length > 0) {
          try {
            const details = await api.problems.getByIds(ids);
            const byId = new Map((details.data || []).map((problem) => [problem.id, problem]));
            problems = ids.map((problemId) => {
              const full = byId.get(problemId);
              const summary = summaries.find((item) => item.id === problemId) || {};
              return {
                id: problemId,
                numericId: full?.numericId ?? summary.numericId,
                title: full?.title || summary.title || problemId,
                difficulty: full?.difficulty || summary.difficulty,
                availability: full?.availability === "CONTEST_ONLY" ? "CONTEST_ONLY" : "PUBLIC",
              };
            });
          } catch {
            // Keep summaries if bulk lookup fails; availability may default to PUBLIC.
          }
        }
        const batchIds = Array.isArray(data.batchIds) ? data.batchIds : [];
        let batches = batchIds.map((batchId) => ({ id: batchId, name: batchId }));
        try {
          batches = await loadSelectedBatches(batchIds);
        } catch {
          // Keep ID placeholders if the batch catalog cannot be loaded.
        }
        if (cancelled) {
          return;
        }
        setForm({
          title: data.title || "",
          description: data.description || "",
          startLocal: instantToDatetimeLocal(data.startAt),
          endLocal: instantToDatetimeLocal(data.endAt),
          audienceType: data.audienceType === "ALL_USERS" ? "ALL_USERS" : "BATCHES",
          batches,
          problems,
        });
      })
      .catch((err) => {
        if (!cancelled) {
          setError(apiErrorMessage(err, "Contest not found"));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [creating, id]);

  const payloadFromForm = () => ({
    title: form.title,
    description: form.description,
    startAt: datetimeLocalToIso(form.startLocal),
    endAt: datetimeLocalToIso(form.endLocal),
    audienceType: form.audienceType,
    batchIds: form.batches.map((batch) => batch.id),
    problemIds: form.problems.map((problem) => problem.id),
  });

  const save = async (event) => {
    event.preventDefault();
    setError("");
    setNotice("");
    const body = payloadFromForm();
    const clientError = validateContestForm(body);
    if (clientError) {
      setError(clientError);
      return;
    }
    if (saving || acting) {
      return;
    }
    setSaving(true);
    try {
      if (creating) {
        const created = await api.adminContests.create(buildContestWriteBody(body));
        navigate(`/admin/contests/${created.data.id}`, {
          replace: true,
          state: {
            notice: "Draft saved. This contest is unpublished. Use Publish when it is ready.",
          },
        });
        return;
      }
      const updated = await api.adminContests.update(id, buildContestPatchBody(body, status));
      setContest(updated.data);
      setNotice("Saved.");
    } catch (err) {
      setError(apiErrorMessage(err, "Could not save contest"));
    } finally {
      setSaving(false);
    }
  };

  const act = async (kind) => {
    if (!id || acting || saving) {
      return;
    }
    if (kind === "delete" && !window.confirm("Delete this contest? This cannot be undone.")) {
      return;
    }
    setActing(kind);
    setError("");
    setNotice("");
    try {
      if (kind === "publish") {
        const updated = await api.adminContests.publish(id);
        setContest(updated.data);
        setNotice("Published.");
      } else if (kind === "unpublish") {
        const updated = await api.adminContests.unpublish(id);
        setContest(updated.data);
        setNotice("Unpublished to draft.");
      } else if (kind === "delete") {
        await api.adminContests.delete(id);
        navigate("/admin");
      }
    } catch (err) {
      const fallback = kind === "publish"
        ? "Could not publish this contest"
        : kind === "unpublish"
          ? "Could not unpublish this contest"
          : "Action failed";
      setError(apiErrorMessage(err, fallback));
    } finally {
      setActing("");
    }
  };

  if (loading) {
    return <div className="app-shell p-8"><div className="max-w-4xl mx-auto app-muted">Loading contest...</div></div>;
  }

  return (
    <div className="app-shell p-8">
      <div className="max-w-4xl mx-auto">
        <div className="flex items-center justify-between gap-4 mb-6">
          <div>
            <Link to="/admin" className="text-blue-600 dark:text-blue-400 hover:underline text-sm">Back to admin</Link>
            <h1 className="text-3xl font-bold mt-2">{creating ? "Create contest" : "Contest"}</h1>
          </div>
          {contest && <ContestStatusBadge status={contest.status} />}
        </div>
        <p className="text-sm app-muted mb-4">Times shown in {timeZone}. Values are sent to the server as UTC.</p>
        {error && <div className="mb-4 text-sm text-red-600 dark:text-red-400" role="alert">{error}</div>}
        {notice && <div className="mb-4 text-sm text-green-700 dark:text-green-300">{notice}</div>}
        {contest && contest.audienceType === "BATCHES" && contest.eligibleMemberCount === 0 && (
          <div className="mb-4 text-sm text-yellow-800 dark:text-yellow-300" role="status">
            No eligible students. An active, verified student must belong to an active selected batch before this contest can be taken.
          </div>
        )}
        {liveLimited && (
          <div className="mb-4 text-sm app-muted">This contest is LIVE. Title, description, and end time can change. Audience, batches, problems, and start time cannot.</div>
        )}
        {readOnly && (
          <div className="mb-4 text-sm app-muted">Ended contests are historical and cannot be edited.</div>
        )}
        <form onSubmit={save} className="app-surface rounded-lg p-4 grid gap-4">
          <div>
            <label className="block text-sm font-medium mb-1" htmlFor="contest-title">Title</label>
            <input id="contest-title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} disabled={readOnly} className="px-3 py-2 app-input rounded w-full" required />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1" htmlFor="contest-description">Description</label>
            <textarea id="contest-description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} disabled={readOnly} className="px-3 py-2 app-input rounded w-full min-h-[120px]" />
          </div>
          <div className="grid sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1" htmlFor="contest-start">Start</label>
              <input id="contest-start" type="datetime-local" value={form.startLocal} onChange={(e) => setForm({ ...form, startLocal: e.target.value })} disabled={scheduleLocked || readOnly} className="px-3 py-2 app-input rounded w-full" required />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1" htmlFor="contest-end">End</label>
              <input id="contest-end" type="datetime-local" value={form.endLocal} onChange={(e) => setForm({ ...form, endLocal: e.target.value })} disabled={readOnly || (scheduleLocked && !liveLimited)} className="px-3 py-2 app-input rounded w-full" required />
            </div>
          </div>
          <fieldset disabled={scheduleLocked || readOnly}>
            <legend className="text-sm font-medium mb-2">Audience</legend>
            <div className="flex flex-col gap-2">
              <label className="flex items-center gap-2">
                <input type="radio" name="audience" checked={form.audienceType === "BATCHES"} onChange={() => setForm({ ...form, audienceType: "BATCHES" })} />
                Batches
              </label>
              <label className="flex items-center gap-2">
                <input type="radio" name="audience" checked={form.audienceType === "ALL_USERS"} onChange={() => setForm({ ...form, audienceType: "ALL_USERS" })} />
                All users
              </label>
            </div>
            {form.audienceType === "ALL_USERS" && (
              <p className="text-sm app-muted mt-2">All-user contests may be disabled by server configuration. Saving a draft is allowed; publishing may return “Global contests are disabled”.</p>
            )}
          </fieldset>
          {form.audienceType === "BATCHES" && (
            <ContestBatchSelector selected={form.batches} onChange={(batches) => setForm({ ...form, batches })} disabled={scheduleLocked || readOnly} />
          )}
          <ContestProblemSelector selected={form.problems} onChange={(problems) => setForm({ ...form, problems })} disabled={scheduleLocked || readOnly} />
          <div className="flex flex-wrap gap-3 pt-2">
            {!readOnly && (
              <div>
                <button type="submit" disabled={saving || !!acting} className="bg-green-600 hover:bg-green-700 text-white px-4 py-2 rounded disabled:opacity-50">
                  {saving ? "Saving..." : creating ? "Save draft" : "Save"}
                </button>
                {creating && (
                  <p className="text-sm app-muted mt-2">Save draft stores an unpublished contest. It is not published until you use Publish on the saved contest.</p>
                )}
              </div>
            )}
            {!creating && canPublish(status, contest?.published) && (
              <button type="button" disabled={saving || !!acting} onClick={() => act("publish")} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded disabled:opacity-50">
                {acting === "publish" ? "Publishing..." : "Publish"}
              </button>
            )}
            {!creating && canUnpublish(status) && (
              <button type="button" disabled={saving || !!acting} onClick={() => act("unpublish")} className="app-btn-secondary px-4 py-2 rounded disabled:opacity-50">
                {acting === "unpublish" ? "Unpublishing..." : "Unpublish"}
              </button>
            )}
            {!creating && canDelete(status) && (
              <button type="button" disabled={saving || !!acting} onClick={() => act("delete")} className="text-red-600 dark:text-red-400 px-4 py-2 rounded disabled:opacity-50">
                {acting === "delete" ? "Deleting..." : "Delete"}
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  );
}
