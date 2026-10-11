import { useCallback, useEffect, useRef, useState } from "react";
import { countdownLabel, countdownTarget, formatCountdown, msUntil } from "../contests/contestTime";

export function useContestCountdown(contest, onBoundary) {
  const [now, setNow] = useState(() => Date.now());
  const firedRef = useRef(false);
  const onBoundaryRef = useRef(onBoundary);
  onBoundaryRef.current = onBoundary;

  const target = countdownTarget(contest);
  const contestKey = `${contest?.id || ""}:${contest?.status || ""}:${target || ""}`;

  useEffect(() => {
    firedRef.current = false;
  }, [contestKey]);

  useEffect(() => {
    if (!target) {
      return undefined;
    }
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [target]);

  const remaining = msUntil(target, now);

  useEffect(() => {
    if (remaining == null || remaining > 0 || firedRef.current) {
      return;
    }
    firedRef.current = true;
    if (typeof onBoundaryRef.current === "function") {
      onBoundaryRef.current();
    }
  }, [remaining]);

  const text = remaining == null ? "" : formatCountdown(remaining);
  const label = countdownLabel(contest);
  const refresh = useCallback(() => setNow(Date.now()), []);

  return { remaining, text, label, target, refresh };
}
