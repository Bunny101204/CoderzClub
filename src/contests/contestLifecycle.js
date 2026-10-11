export function canEditScheduleAndSet(status) {
  return status === "DRAFT" || status === "UPCOMING" || !status;
}

export function canEditLiveLimited(status) {
  return status === "LIVE";
}

export function isReadOnly(status) {
  return status === "ENDED";
}

export function canPublish(status, published) {
  return !published && (status === "DRAFT" || status === "UPCOMING");
}

export function canUnpublish(status) {
  return status === "UPCOMING";
}

export function canDelete(status) {
  return status === "DRAFT" || status === "UPCOMING" || !status;
}

export function compactContestBody(body) {
  const compact = {};
  Object.entries(body || {}).forEach(([key, value]) => {
    if (key === "published") {
      return;
    }
    if (value === undefined || value === null) {
      return;
    }
    compact[key] = value;
  });
  return compact;
}

export function buildContestWriteBody(form) {
  const audienceType = form.audienceType === "ALL_USERS" ? "ALL_USERS" : "BATCHES";
  return compactContestBody({
    title: (form.title || "").trim(),
    description: form.description == null ? "" : form.description,
    startAt: form.startAt,
    endAt: form.endAt,
    audienceType,
    batchIds: audienceType === "ALL_USERS" ? [] : (form.batchIds || []),
    problemIds: form.problemIds || [],
  });
}

export function buildContestPatchBody(form, status) {
  if (status === "LIVE") {
    return compactContestBody({
      title: (form.title || "").trim(),
      description: form.description == null ? "" : form.description,
      endAt: form.endAt,
    });
  }
  return buildContestWriteBody(form);
}

export function validateContestForm(form) {
  if (!form.title || !form.title.trim()) {
    return "title is required";
  }
  if (!form.startAt) {
    return "start time is required";
  }
  if (!form.endAt) {
    return "end time is required";
  }
  const start = new Date(form.startAt);
  const end = new Date(form.endAt);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    return "start and end must be valid times";
  }
  if (!(end.getTime() > start.getTime())) {
    return "end must be after start";
  }
  if (form.audienceType !== "ALL_USERS" && (!form.batchIds || form.batchIds.length === 0)) {
    return "at least one batch is required";
  }
  return null;
}
