let activeSession = null;

export function setActiveDraftSession(session) {
  activeSession = session;
  return () => {
    if (activeSession === session) activeSession = null;
  };
}

export function flushActiveDraftBoundary(options = { keepalive: true }) {
  if (!activeSession) return Promise.resolve({ skipped: true });
  return activeSession.syncBoundary(options);
}
