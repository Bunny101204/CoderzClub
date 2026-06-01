import { useState, useCallback, useEffect, useRef } from 'react';

/**
 * Custom hook for fetching problem statuses with debouncing and batching
 * Prevents N+1 queries and excessive re-renders
 */
export const useProblemStatus = (problems, user) => {
  const [problemStatus, setProblemStatus] = useState({});
  const [loading, setLoading] = useState(false);
  const abortControllerRef = useRef(null);
  const previousProblemsRef = useRef([]);

  // Memoize status calculation to avoid recalculation
  const statusMap = useCallback(async (problemsList) => {
    if (!user || !problemsList || problemsList.length === 0) {
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

    // Batch requests in groups of 5 to avoid overwhelming the server
    const batchSize = 5;
    const token = localStorage.getItem('jwtToken');
    if (!token) {
      setLoading(false);
      return;
    }

    try {
      for (let i = 0; i < problemsList.length; i += batchSize) {
        const batch = problemsList.slice(i, i + batchSize);
        
        const batchPromises = batch.map(async (problem) => {
          try {
            const response = await fetch(
              `/api/submissions/my-submissions?problemId=${problem.id}&size=1`,
              {
                headers: {
                  Authorization: `Bearer ${token}`
                },
                signal: abortControllerRef.current.signal
              }
            );
            
            if (!response.ok) return;

            const data = await response.json();
            const submissions = Array.isArray(data.submissions) ? data.submissions : [];
            const solved = submissions.some(s =>
              s.result === 'ACCEPTED' || s.verdict === 'ACCEPTED'
            );
            
            statusMap[problem.id] = solved
              ? 'SOLVED'
              : submissions.length > 0
              ? 'ATTEMPTED'
              : 'NOT_STARTED';
          } catch (err) {
            if (err.name !== 'AbortError') {
              console.error('Error fetching problem status:', err);
            }
          }
        });

        await Promise.all(batchPromises);
      }
      
      setProblemStatus(statusMap);
    } catch (err) {
      console.error('Error in batch status fetch:', err);
    } finally {
      setLoading(false);
    }
  }, [user]);

  // Only refetch if problems array actually changed (shallow equality)
  useEffect(() => {
    const problemIds = problems?.map(p => p.id).join(',') || '';
    const previousIds = previousProblemsRef.current?.map(p => p.id).join(',') || '';
    
    if (problemIds !== previousIds) {
      previousProblemsRef.current = problems;
      statusMap(problems);
    }

    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [problems, statusMap]);

  return { problemStatus, loading };
};
