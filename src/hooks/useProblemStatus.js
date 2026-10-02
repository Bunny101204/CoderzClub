import { useState, useCallback, useEffect, useRef } from 'react';
import { indexProgress, statusForProblem } from '../progress/userProgress';

export const useProblemStatus = (problems, user) => {
  const [problemStatus, setProblemStatus] = useState({});
  const [loading, setLoading] = useState(false);
  const abortControllerRef = useRef(null);

  const loadStatus = useCallback(async (problemsList) => {
    const safeProblems = Array.isArray(problemsList) ? problemsList.filter(Boolean) : [];
    if (!user || safeProblems.length === 0) {
      setProblemStatus({});
      return;
    }

    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    const controller = new AbortController();
    abortControllerRef.current = controller;

    setLoading(true);
    const token = localStorage.getItem('jwtToken') || localStorage.getItem('token');
    if (!token) {
      setLoading(false);
      return;
    }

    try {
      const response = await fetch('/api/submissions/my-progress', {
        headers: { Authorization: `Bearer ${token}` },
        signal: controller.signal
      });
      if (!response.ok) {
        throw new Error(`Status request failed with HTTP ${response.status}`);
      }
      const data = await response.json();
      const index = indexProgress(data.progress || []);
      const statusMap = {};
      safeProblems.forEach((problem) => {
        const status = statusForProblem(problem, index);
        if (status) {
          statusMap[String(problem.id)] = status;
          if (problem.numericId != null) {
            statusMap[String(problem.numericId)] = status;
          }
        }
      });
      setProblemStatus(statusMap);
    } catch (err) {
      if (err?.name !== 'AbortError') {
        console.error('Error in batch status fetch:', err);
      }
    } finally {
      if (abortControllerRef.current === controller) {
        setLoading(false);
        abortControllerRef.current = null;
      }
    }
  }, [user]);

  useEffect(() => {
    const refresh = () => loadStatus(Array.isArray(problems) ? problems : []);
    refresh();
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') refresh();
    };
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      window.removeEventListener('focus', refresh);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [problems, loadStatus]);

  return { problemStatus, loading };
};
