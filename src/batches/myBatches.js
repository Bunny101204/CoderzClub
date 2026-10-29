export function myBatchesView(data) {
  const coordinated = Array.isArray(data?.batches) ? data.batches : [];
  const enrolledPage = data?.enrolled && typeof data.enrolled === "object" ? data.enrolled : {};
  const enrolled = Array.isArray(enrolledPage.items) ? enrolledPage.items : [];
  return {
    enrolled,
    coordinated,
    currentPage: Number.isInteger(enrolledPage.currentPage) ? enrolledPage.currentPage : 0,
    totalPages: Number.isInteger(enrolledPage.totalPages) ? enrolledPage.totalPages : 0,
    showEnrolledSection: enrolled.length > 0 || coordinated.length === 0,
    showCoordinatorSection: coordinated.length > 0,
    empty: enrolled.length === 0 && coordinated.length === 0,
  };
}

export function practiceProblemLabel(problem) {
  const title = problem?.title || "Practice problem";
  if (problem?.numericId == null || problem.numericId === "") {
    return title;
  }
  return `${problem.numericId}. ${title}`;
}

export function practiceProblemPath(problem) {
  if (!problem?.problemId) {
    return null;
  }
  return `/problem/${problem.problemId}`;
}
