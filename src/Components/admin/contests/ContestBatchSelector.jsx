import { useEffect, useRef, useState } from "react";
import { api, apiErrorMessage } from "../../../apiClient";
import { SEARCH_DEBOUNCE_MS, createRequestSequence } from "../../../admin/adminProblems";

export default function ContestBatchSelector({ selected, onChange, disabled }) {
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
    api.adminBatches.list({ page: 0, size: 20, search: debounced || undefined })
      .then((response) => {
        if (!seq.current.isCurrent(request)) {
          return;
        }
        setHits(Array.isArray(response.data?.batches) ? response.data.batches : []);
        setError("");
      })
      .catch((err) => {
        if (!seq.current.isCurrent(request)) {
          return;
        }
        setHits([]);
        setError(apiErrorMessage(err, "Could not load batches"));
      })
      .finally(() => {
        if (seq.current.isCurrent(request)) {
          setLoading(false);
        }
      });
  }, [debounced]);

  const selectedIds = new Set((selected || []).map((batch) => batch.id));
  const hasInactive = (selected || []).some((batch) => batch.active === false);

  const toggle = (batch) => {
    if (disabled) {
      return;
    }
    if (selectedIds.has(batch.id)) {
      onChange(selected.filter((item) => item.id !== batch.id));
      return;
    }
    onChange([...selected, { id: batch.id, name: batch.name, active: batch.active }]);
  };

  return (
    <div>
      <label className="block text-sm font-medium mb-1" htmlFor="contest-batch-search">Batches</label>
      <p className="text-sm app-muted mb-2">Select one or more batches. Inactive batches can be saved in a draft but prevent publishing.</p>
      {(selected || []).length > 0 && (
        <ul className="mb-3 space-y-1">
          {selected.map((batch) => (
            <li key={batch.id} className="flex items-center justify-between gap-2 app-inset rounded px-3 py-2">
              <span>
                {batch.name || batch.id}
                {batch.active === false && (
                  <span className="ml-2 text-xs font-semibold text-amber-700 dark:text-amber-300">Inactive</span>
                )}
              </span>
              <button
                type="button"
                className="text-red-600 dark:text-red-400 text-sm hover:underline disabled:opacity-50"
                onClick={() => onChange(selected.filter((item) => item.id !== batch.id))}
                disabled={disabled}
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}
      {hasInactive && (
        <div className="mb-3 text-sm text-amber-700 dark:text-amber-300">
          An inactive batch is selected. Publishing will fail until every target batch is active.
        </div>
      )}
      <input
        id="contest-batch-search"
        type="search"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        disabled={disabled}
        placeholder="Search batches by name"
        className="px-3 py-2 app-input rounded w-full mb-2"
      />
      {error && <div className="text-sm text-red-600 dark:text-red-400 mb-2">{error}</div>}
      {loading ? (
        <div className="text-sm app-muted">Loading batches...</div>
      ) : hits.length === 0 ? (
        <div className="text-sm app-muted">No batches found.</div>
      ) : (
        <ul className="max-h-56 overflow-auto app-surface rounded border border-gray-200 dark:border-gray-700">
          {hits.map((batch) => (
            <li key={batch.id} className="border-t first:border-t-0 border-gray-200 dark:border-gray-700">
              <label className="flex items-center gap-2 px-3 py-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={selectedIds.has(batch.id)}
                  onChange={() => toggle(batch)}
                  disabled={disabled}
                />
                <span className="flex-1">{batch.name}</span>
                <span className={`text-xs ${batch.active ? "text-green-700 dark:text-green-300" : "text-amber-700 dark:text-amber-300"}`}>
                  {batch.active ? "Active" : "Inactive"}
                </span>
              </label>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
