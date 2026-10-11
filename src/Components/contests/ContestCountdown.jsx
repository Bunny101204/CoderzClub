import { useContestCountdown } from "../../hooks/useContestCountdown";

export default function ContestCountdown({ contest, onBoundary }) {
  const { text, label, target } = useContestCountdown(contest, onBoundary);
  if (!target || !text) {
    return null;
  }
  return (
    <div className="text-sm">
      <span className="app-muted">{label}: </span>
      <span className="font-semibold">{text}</span>
    </div>
  );
}
