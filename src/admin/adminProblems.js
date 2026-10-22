export const SEARCH_DEBOUNCE_MS = 400;
export const MISSING_NUMERIC_ID_LABEL = "—";

export function displayProblemId(problem) {
  if (problem == null || problem.numericId == null || problem.numericId === "") {
    return MISSING_NUMERIC_ID_LABEL;
  }
  return String(problem.numericId);
}

export function internalProblemId(problem) {
  return problem == null || problem.id == null ? null : String(problem.id);
}

export function editProblemPath(problem) {
  const id = internalProblemId(problem);
  return id == null ? null : `/admin/edit-problem/${id}`;
}

export function nextPageForFilterChange() {
  return 1;
}

export function createRequestSequence() {
  let current = 0;
  return {
    next() {
      current += 1;
      return current;
    },
    isCurrent(seq) {
      return seq === current;
    }
  };
}

export function debounce(fn, wait = SEARCH_DEBOUNCE_MS) {
  let timer;
  const wrapped = (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), wait);
  };
  wrapped.cancel = () => clearTimeout(timer);
  return wrapped;
}
