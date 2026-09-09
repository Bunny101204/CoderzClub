import { useState, useCallback, useEffect, useRef } from 'react';

/**
 * Custom hook for fetching problem statuses with debouncing and batching
 * Prevents N+1 queries and excessive re-renders
 */
export const useProblemStatus = (problems, user) => {
  const [problemStatus, setProblemStatus] = useState({});
  const [loading, setLoading] = useState(false);
  const abortControllerRef = useRef(null);

  // Memoize status calculation to avoid recalculation
  const statusMap = useCallback(async (problemsList) => {
    const safeProblems = Array.isArray(problemsList) ? problemsList.filter(Boolean) : [];
    if (!user || safeProblems.length === 0) {
      setProblemStatus({});
      return;
    }

    // Cancel previous request if still in progress
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    abortControllerRef.current = new AbortController();

    setLoading(true);
    const statusMap = {};

    const token = localStorage.getItem('jwtToken') || localStorage.getItem('token');
    if (!token) {
      setLoading(false);
      return;
    }

    try {
      const response = await fetch('/api/submissions/my-submissions?size=1000', {
        headers: { Authorization: `Bearer ${token}` },
        signal: abortControllerRef.current.signal
      });
      if (!response.ok) {
        throw new Error(`Status request failed with HTTP ${response.status}`);
      }

      const data = await response.json();
      const submissions = Array.isArray(data)
        ? data
        : Array.isArray(data.submissions)
        ? data.submissions
        : Array.isArray(data.content)
        ? data.content
        : Array.isArray(data.data?.submissions)
        ? data.data.submissions
        : [];
      const currentProblemIds = new Set(safeProblems.map(problem => String(problem.id)));

      submissions.forEach(submission => {
        const problemId = String(submission.problemId ?? submission.problem?.id ?? '');
        if (!currentProblemIds.has(problemId)) return;

        const result = String(
          submission.result ?? submission.verdict ?? submission.status
            ?? submission.finalResult ?? submission.jobStatus ?? ''
        ).trim().toUpperCase();
        const currentStatus = statusMap[problemId];
        if (['ACCEPTED', 'SOLVED', 'PASSED', 'SUCCESS', 'COMPLETED'].includes(result)) {
          statusMap[problemId] = 'SOLVED';
        } else if (currentStatus !== 'SOLVED') {
          statusMap[problemId] = 'ATTEMPTED';
        }
      });
      
      setProblemStatus(statusMap);
    } catch (err) {
      console.error('Error in batch status fetch:', err);
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    const refresh = () => statusMap(Array.isArray(problems) ? problems : []);
    refresh();

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') refresh();
    };
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', handleVisibilityChange);
    const refreshTimer = window.setInterval(refresh, 10000);

    return () => {
      window.removeEventListener('focus', refresh);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.clearInterval(refreshTimer);
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [problems, statusMap]);

  return { problemStatus, loading };
};
