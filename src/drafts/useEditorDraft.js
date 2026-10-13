import { useCallback, useEffect, useRef, useState } from "react";
import { createDraftSession } from "./editorDraftSession.js";
import { setActiveDraftSession } from "./draftBoundary.js";
import { createDefaultDraftTransport } from "./draftTransport.js";

export function useEditorDraft({
  enabled,
  userId,
  contestId,
  problemId,
  languageId,
  starterCode,
  currentCodeRef,
  onApply,
}) {
  const sessionRef = useRef(null);
  const onApplyRef = useRef(onApply);
  const activeRef = useRef(false);
  onApplyRef.current = onApply;
  const [status, setStatus] = useState("");
  const [conflict, setConflict] = useState(null);

  if (sessionRef.current == null && typeof window !== "undefined") {
    sessionRef.current = createDraftSession({
      storage: window.localStorage,
      transport: createDefaultDraftTransport(),
      onApply: (code) => onApplyRef.current?.(code),
      onView: (view) => {
        if (!activeRef.current) return;
        setStatus(view.status || "");
        setConflict(view.conflict || null);
      },
    });
  }

  useEffect(() => {
    const session = sessionRef.current;
    if (!session || !enabled || !userId || problemId == null || problemId === "") {
      return undefined;
    }
    activeRef.current = true;
    const unregister = setActiveDraftSession(session);
    let cancelled = false;
    session.open({
      userId,
      contestId: contestId || null,
      problemId,
      languageId,
      starterCode: starterCode || "",
      currentCode: currentCodeRef?.current,
    }).catch(() => {
      if (!cancelled) setStatus("Sync unavailable");
    });
    const onLifecycle = (event) => {
      if (event.type === "visibilitychange" && document.visibilityState !== "hidden") return;
      session.syncBoundary({ keepalive: event.type === "pagehide" });
    };
    window.addEventListener("pagehide", onLifecycle);
    document.addEventListener("visibilitychange", onLifecycle);
    return () => {
      cancelled = true;
      activeRef.current = false;
      window.removeEventListener("pagehide", onLifecycle);
      document.removeEventListener("visibilitychange", onLifecycle);
      unregister();
      session.syncBoundary({ keepalive: false });
    };
  }, [enabled, userId, contestId, problemId, languageId, starterCode, currentCodeRef]);

  const noteEdit = useCallback((code) => {
    sessionRef.current?.noteEdit(code);
  }, []);
  const useCloud = useCallback(() => {
    sessionRef.current?.useCloud();
  }, []);
  const keepLocal = useCallback(() => {
    sessionRef.current?.keepLocal();
  }, []);

  return { noteEdit, useCloud, keepLocal, status, conflict };
}
