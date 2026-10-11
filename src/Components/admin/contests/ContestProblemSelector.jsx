import { useEffect, useRef, useState } from "react";
import { api, apiErrorMessage } from "../../../apiClient";
import { SEARCH_DEBOUNCE_MS, createRequestSequence, displayProblemId } from "../../../admin/adminProblems";

function availabilityOf(problem) {
  return problem?.availability === "CONTEST_ONLY" ? "CONTEST_ONLY" : "PUBLIC";
}

export default function ContestProblemSelector({ selected, onChange, disabled }) {
  const [search, setSearch] = useState("");
  const [debounced, setDebounced] = useState("");
  const [hits, setHits] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const seq = useRef(createRequestSequence());

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(search.trim()), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    const request = seq.current.next();
    setLoading(true);
    api.problems.getAll({ page: 0, size: 10, search: debounced || undefined })
      .then((response) => {
        if (!seq.current.isCurrent(request)) {
          return;
        }
        const data = response.data;
        setHits(Array.isArray(data?.problems) ? data.problems : (Array.isArray(data) ? data : []));
        setError("");
      })
      .catch((err) => {
        if (!seq.current.isCurrent(request)) {
          return;
        }
        setHits([]);
        setError(apiErrorMessage(err, "Could not search problems"));
      })
      .finally(() => {
        if (seq.current.isCurrent(request)) {
          setLoading(false);
        }
      });
  }, [debounced]);

  const selectedIds = new Set((selected || []).map((problem) => problem.id));

  const add = (problem) => {
    if (disabled || !problem?.id || selectedIds.has(problem.id)) {
      return;
    }
    onChange([...selected, {
      id: problem.id,
      numericId: problem.numericId,
      title: problem.title,
      difficulty: problem.difficulty,
      availability: availabilityOf(problem),
    }]);
  };

  const move = (index, delta) => {
    if (disabled) {
      return;
    }
    const next = [...selected];
    const target = index + delta;
    if (target < 0 || target >= next.length) {
      return;
    }
    const [item] = next.splice(index, 1);
    next.splice(target, 0, item);
    onChange(next);
  };

  return (
    <div>
      <label className="block text-sm font-medium mb-1" htmlFor="contest-problem-search">Problems</label>
      <p className="text-sm app-muted mb-2">Order is the contest order. Include PUBLIC practice problems or CONTEST_ONLY secrets.</p>
      {(selected || []).length > 0 && (
        <ol className="mb-3 space-y-1">
          {selected.map((problem, index) => (
            <li key={problem.id} className="flex items-center gap-2 app-inset rounded px-3 py-2">
              <span className="font-mono text-sm w-12">{displayProblemId(problem)}</span>
              <span className="flex-1 min-w-0 truncate">{problem.title || problem.id}</span>
              <span className="text-xs app-muted">{problem.difficulty || "N/A"}</span>
              <span className={`text-xs font-semibold ${availabilityOf(problem) === "CONTEST_ONLY" ? "text-purple-700 dark:text-purple-300" : "app-muted"}`}>
                {availabilityOf(problem)}
              </span>
              <button type="button" className="text-sm hover:underline disabled:opacity-50" onClick={() => move(index, -1)} disabled={disabled || index === 0} title="Move up">Up</button>
              <button type="button" className="text-sm hover:underline disabled:opacity-50" onClick={() => move(index, 1)} disabled={disabled || index === selected.length - 1} title="Move down">Down</button>
              <button type="button" className="text-red-600 dark:text-red-400 text-sm hover:underline disabled:opacity-50" onClick={() => onChange(selected.filter((item) => item.id !== problem.id))} disabled={disabled}>Remove</button>
            </li>
          ))}
        </ol>
      )}
      <input
        id="contest-problem-search"
        type="search"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        disabled={disabled}
        placeholder="Search by title or ID"
        className="px-3 py-2 app-input rounded w-full mb-2"
      />
      {error && <div className="text-sm text-red-600 dark:text-red-400 mb-2">{error}</div>}
      {loading ? (
        <div className="text-sm app-muted">Searching problems...</div>
      ) : hits.length === 0 ? (
        <div className="text-sm app-muted">No problems found.</div>
      ) : (
        <ul className="max-h-56 overflow-auto app-surface rounded border border-gray-200 dark:border-gray-700">
          {hits.map((problem) => {
            const added = selectedIds.has(problem.id);
            return (
              <li key={problem.id} className="flex items-center gap-2 px-3 py-2 border-t first:border-t-0 border-gray-200 dark:border-gray-700">
                <span className="font-mono text-sm w-12">{displayProblemId(problem)}</span>
                <span className="flex-1 min-w-0 truncate">{problem.title}</span>
                <span className="text-xs app-muted">{problem.difficulty || "N/A"}</span>
                <span className={`text-xs font-semibold ${availabilityOf(problem) === "CONTEST_ONLY" ? "text-purple-700 dark:text-purple-300" : "app-muted"}`}>
                  {availabilityOf(problem)}
                </span>
                <button
                  type="button"
                  className="text-blue-600 dark:text-blue-400 text-sm hover:underline disabled:opacity-50"
                  onClick={() => add(problem)}
                  disabled={disabled || added}
                >
                  {added ? "Added" : "Add"}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
