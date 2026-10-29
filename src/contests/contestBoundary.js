export const BOUNDARY_RETRY_DELAYS_MS = [1500, 3000, 5000];

export function nextBoundaryRetry({ previousStatus, serverStatus, attempt }) {
  const expected = previousStatus === "UPCOMING" ? "LIVE" : previousStatus === "LIVE" ? "ENDED" : null;
  if (!expected) {
    return { action: "stop", reason: "no-boundary" };
  }
  if (serverStatus === expected || serverStatus === "ENDED") {
    return { action: "stop", reason: "settled" };
  }
  const index = Number.isInteger(attempt) && attempt >= 0 ? attempt : 0;
  if (index >= BOUNDARY_RETRY_DELAYS_MS.length) {
    return { action: "stop", reason: "exhausted" };
  }
  return { action: "retry", delayMs: BOUNDARY_RETRY_DELAYS_MS[index] };
}

export function countdownPresentation({ contest, remaining, text, label, target, pending, exhausted }) {
  if (!contest || !target) {
    return null;
  }
  if (remaining != null && remaining <= 0) {
    if (exhausted && !pending) {
      return { label, text: "Waiting for the server" };
    }
    return { label: label || "Status", text: "Checking status…" };
  }
  if (!text) {
    return null;
  }
  return { label, text };
}

export function createBoundaryRefresh({
  fetchContest,
  onContest,
  onPendingChange,
  onExhausted,
  onError,
  setTimeout,
  clearTimeout,
}) {
  let timer = null;
  let disposed = false;
  let inFlight = false;
  let generation = 0;

  function clearTimer() {
    if (timer != null) {
      clearTimeout(timer);
      timer = null;
    }
  }

  async function run(previousStatus, attempt, generationAtStart) {
    if (disposed || generationAtStart !== generation) {
      return;
    }
    inFlight = true;
    onPendingChange?.(true);
    let serverStatus = null;
    try {
      const contest = await fetchContest();
      if (disposed || generationAtStart !== generation) {
        return;
      }
      serverStatus = contest?.status ?? null;
      onContest?.(contest);
      onError?.(null);
    } catch (error) {
      if (disposed || generationAtStart !== generation) {
        return;
      }
      onError?.(error);
    } finally {
      if (generationAtStart === generation) {
        inFlight = false;
      }
    }
    if (disposed || generationAtStart !== generation) {
      return;
    }
    const decision = nextBoundaryRetry({ previousStatus, serverStatus, attempt });
    if (decision.action === "retry") {
      timer = setTimeout(() => {
        timer = null;
        return run(previousStatus, attempt + 1, generationAtStart);
      }, decision.delayMs);
      return;
    }
    onPendingChange?.(false);
    if (decision.reason === "exhausted") {
      onExhausted?.();
    }
  }

  return {
    start(previousStatus) {
      if (disposed || inFlight || timer != null) {
        return false;
      }
      const generationAtStart = generation;
      run(previousStatus, 0, generationAtStart);
      return true;
    },
    dispose() {
      disposed = true;
      generation += 1;
      clearTimer();
      inFlight = false;
      onPendingChange?.(false);
    },
  };
}
