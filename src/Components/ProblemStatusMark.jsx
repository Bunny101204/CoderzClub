import { CheckIcon, StatusDotIcon } from "../icons/AppIcons.jsx";
import { statusPresentation } from "../progress/problemStatusView.js";

export default function ProblemStatusMark({ status, className = "" }) {
  const presentation = statusPresentation(status);
  return (
    <span className={`inline-flex items-center justify-center min-h-[1.25rem] min-w-[1.25rem] ${className}`}>
      <span className="sr-only">{presentation.label}</span>
      {presentation.kind === "check" ? (
        <CheckIcon className="h-4 w-4 text-green-600 dark:text-green-400" />
      ) : null}
      {presentation.kind === "dot" ? (
        <StatusDotIcon className="h-2.5 w-2.5 text-yellow-600 dark:text-yellow-400" />
      ) : null}
    </span>
  );
}
