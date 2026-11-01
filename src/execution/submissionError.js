// Admission errors are not compiler diagnostics. Never render server exception text.
export function submissionErrorMessage(error) {
  const status = Number(error?.response?.status);
  if (status === 401) return "Please sign in again before submitting.";
  if (status === 403 || status === 404) return "This problem is not available for submission.";
  if (status === 429) return "Please wait before submitting again.";
  if (status === 400 || status === 422) {
    return "Submission could not be accepted. Check your code and selected language, then try again.";
  }
  return "Submission service is temporarily unavailable. Retry checks the same attempt.";
}
