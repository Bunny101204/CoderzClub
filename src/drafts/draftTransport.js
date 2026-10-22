import { api, getApiBaseUrl, getAuthToken } from "../apiClient";
import { shouldKeepaliveSync } from "./codeDraft.js";

export function draftRequestUrl(identity) {
  const base = getApiBaseUrl() || "";
  const problem = encodeURIComponent(identity.problemId);
  const language = encodeURIComponent(identity.languageId);
  if (identity.contestId) {
    const contest = encodeURIComponent(identity.contestId);
    return `${base}/api/contests/${contest}/problems/${problem}/drafts/${language}`;
  }
  return `${base}/api/problems/${problem}/drafts/${language}`;
}

export function createDefaultDraftTransport() {
  return {
    get(identity) {
      const request = identity.contestId
        ? api.drafts.getContest(identity.contestId, identity.problemId, identity.languageId)
        : api.drafts.getPractice(identity.problemId, identity.languageId);
      return request.then((response) => response.data);
    },
    async put(identity, body) {
      if (!getAuthToken()) return { ok: false, skipped: true };
      try {
        const request = identity.contestId
          ? api.drafts.putContest(identity.contestId, identity.problemId, identity.languageId, body)
          : api.drafts.putPractice(identity.problemId, identity.languageId, body);
        const response = await request;
        return { ok: true, draft: response.data };
      } catch (error) {
        return {
          ok: false,
          status: error?.response?.status || 0,
          body: error?.response?.data || null,
        };
      }
    },
    putKeepalive(identity, body) {
      const token = getAuthToken();
      if (!token || typeof fetch !== "function") return false;
      const payload = JSON.stringify(body);
      if (!shouldKeepaliveSync(payload)) return false;
      fetch(draftRequestUrl(identity), {
        method: "PUT",
        keepalive: true,
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: payload,
      }).catch(() => {});
      return true;
    },
  };
}
