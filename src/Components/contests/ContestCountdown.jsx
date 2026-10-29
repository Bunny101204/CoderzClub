import { useContestCountdown } from "../../hooks/useContestCountdown";
import { countdownPresentation } from "../../contests/contestBoundary";

export default function ContestCountdown({ contest, onBoundary, pending = false, exhausted = false }) {
  const { text, label, target, remaining } = useContestCountdown(contest, onBoundary);
  const presentation = countdownPresentation({ contest, remaining, text, label, target, pending, exhausted });
  if (!presentation) {
    return null;
  }
  return (
    <div className="text-sm">
      <span className="app-muted">{presentation.label}: </span>
      <span className="font-semibold">{presentation.text}</span>
    </div>
  );
}
