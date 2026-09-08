import { useState, useCallback, useRef, useEffect } from 'react';

/**
 * Custom hook for caching API responses to prevent redundant requests
 * @param {string} cacheKey - Unique key for caching
 * @param {number} ttl - Time to live for cache in milliseconds (default: 5min)
 * @returns {object} - { data, loading, error, fetch }
 */
export const useCachedApi = (cacheKey, ttl = 5 * 60 * 1000) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  
  const cache = useRef(new Map());
  const timers = useRef(new Map());

  const fetch = useCallback(async (url, options = {}) => {
    // Check if data is in cache and still valid
    if (cache.current.has(cacheKey)) {
      const cachedData = cache.current.get(cacheKey);
      setData(cachedData);
      return cachedData;
    }

    setLoading(true);
    setError(null);

    try {
      const response = await fetch(url, options);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const result = await response.json();
      
      // Cache the result
      cache.current.set(cacheKey, result);
      setData(result);

      // Clear old timer and set new one
      if (timers.current.has(cacheKey)) {
        clearTimeout(timers.current.get(cacheKey));
      }
      
      const timer = setTimeout(() => {
        cache.current.delete(cacheKey);
        timers.current.delete(cacheKey);
      }, ttl);
      
      timers.current.set(cacheKey, timer);

      return result;
    } catch (err) {
      setError(err);
      console.error('Error fetching data:', err);
      return null;
    } finally {
      setLoading(false);
    }
  }, [cacheKey, ttl]);

  // Cleanup timers on unmount
  useEffect(() => {
    return () => {
      if (timers.current.has(cacheKey)) {
        clearTimeout(timers.current.get(cacheKey));
        timers.current.delete(cacheKey);
      }
    };
  }, [cacheKey]);

  const clearCache = useCallback(() => {
    cache.current.delete(cacheKey);
    if (timers.current.has(cacheKey)) {
      clearTimeout(timers.current.get(cacheKey));
      timers.current.delete(cacheKey);
    }
    setData(null);
  }, [cacheKey]);

  return { data, loading, error, fetch, clearCache };
};
