# Quick Reference - Using Optimization Hooks

## 🎯 Quick Start for Developers

### Using useProblemStatus Hook

**Problem**: You have a list of problems and need to show which ones the user has solved/attempted.

**Solution**: Use the `useProblemStatus` hook instead of fetching statuses individually.

```javascript
import { useProblemStatus } from '../hooks/useProblemStatus';

const MyComponent = () => {
  const [problems, setProblems] = useState([]);
  const { user } = useAuth();
  
  // Automatically batches requests and handles loading
  const { problemStatus, loading } = useProblemStatus(problems, user);
  
  return (
    <div>
      {problems.map(problem => (
        <div key={problem.id}>
          <span>{problem.title}</span>
          {problemStatus[problem.id] === 'SOLVED' && <span>✓</span>}
          {problemStatus[problem.id] === 'ATTEMPTED' && <span>●</span>}
        </div>
      ))}
    </div>
  );
};
```

**Key Benefits**:
- ✅ Automatically batches requests (10 problems = 2 API calls, not 10)
- ✅ Prevents duplicate fetches (if problems array unchanged)
- ✅ Cleans up stale requests automatically
- ✅ No manual state management needed

**Avoid This (Old Pattern)**:
```javascript
// ✗ BAD: N+1 query pattern
useEffect(() => {
  const statuses = {};
  problems.forEach(problem => {
    fetch(`/api/submissions/${problem.id}`)  // 10 separate requests!
      .then(...)
      .then(res => statuses[problem.id] = res.status)
  });
}, [problems]);
```

---

### Using useCachedApi Hook

**Problem**: You're making the same API call multiple times (e.g., problem list on filter change).

**Solution**: Use the `useCachedApi` hook to cache responses.

```javascript
import { useCachedApi } from '../hooks/useCachedApi';

const HomePage = () => {
  const [page, setPage] = useState(0);
  const [difficulty, setDifficulty] = useState('');
  
  const cacheKey = `problems-${page}-${difficulty}`;
  const { data: problems, loading, fetch: fetchProblems } = useCachedApi(cacheKey);
  
  useEffect(() => {
    const url = `/api/problems?page=${page}&difficulty=${difficulty}`;
    fetchProblems(url);
  }, [page, difficulty]);
  
  return (
    <div>
      {loading ? 'Loading...' : problems?.map(p => <div key={p.id}>{p.title}</div>)}
    </div>
  );
};
```

**Cache Behavior**:
- First call: Hits API, caches for 5 minutes
- Second call (same params, within 5min): Uses cache immediately
- Cache expires after 5 minutes: Next call hits API again

**Override Cache TTL**:
```javascript
// Cache for 10 minutes instead of 5
const { data, fetch } = useCachedApi(cacheKey, 10 * 60 * 1000);
```

**Clear Cache Manually**:
```javascript
const { clearCache } = useCachedApi(cacheKey);
// ... later ...
clearCache();  // Removes from cache and state
```

---

## 🚀 Lazy Loading Components

**Problem**: Your app is 301KB and takes 4 seconds to load. Users wait.

**Solution**: Lazy load components that aren't needed immediately.

```javascript
// In App.jsx
import { lazy, Suspense } from 'react';

// Lazy load heavy components
const Judge0CodeEditor = lazy(() => import('./Components/Judge0CodeEditor'));
const AdminDashboard = lazy(() => import('./Components/AdminDashboard'));

function App() {
  return (
    <Routes>
      <Route 
        path="/editor" 
        element={
          <Suspense fallback={<LoadingFallback />}>
            <Judge0CodeEditor />
          </Suspense>
        }
      />
      {/* ... other routes ... */}
    </Routes>
  );
}
```

**Result**:
- Initial load: 301KB → 120KB (60% faster)
- Judge0CodeEditor only loads when user navigates to `/editor`
- Each lazy component loads in a separate bundle chunk

---

## 📊 Monitoring Performance

### In Browser Console

```javascript
// Measure initial load time
console.time('app-load');
// ... let app load ...
console.timeEnd('app-load');

// Check bundle sizes
// DevTools → Network tab → see all .js files and their sizes

// Check for N+1 requests
// DevTools → Network tab → filter "XHR" 
// Count the number of requests to same endpoint
```

### Expected Numbers

✅ **Good Performance**:
- Initial load: <2 seconds
- Problem status: <600ms
- API response: <100ms
- Memory: <100MB

⚠️ **Needs Optimization**:
- Initial load: >4 seconds
- Problem status: >1 second
- API response: >500ms
- Memory: >500MB

---

## 🐛 Debugging Common Issues

### Problem Status Not Updating

**Issue**: You added new problems but status doesn't show up.

**Fix**: Ensure `useProblemStatus` dependency is the problems array:
```javascript
// Correct - will re-fetch when problems change
const { problemStatus } = useProblemStatus(problems, user);

// Wrong - won't re-fetch
const { problemStatus } = useProblemStatus([], user);
```

### Cache Not Clearing

**Issue**: You modified a problem but old cache is showing.

**Fix**: Call `clearCache()` after mutations:
```javascript
const { fetch, clearCache } = useCachedApi(cacheKey);

const handleUpdateProblem = async (id, updates) => {
  await api.patch(`/api/problems/${id}`, updates);
  clearCache();  // Force refresh on next fetch
};
```

### Lazy Component Not Loading

**Issue**: Clicking route doesn't load the lazy component.

**Fix**: Ensure Suspense wrapper is in place:
```javascript
// Wrong - no Suspense
<Route path="/editor" element={<Judge0CodeEditor />} />

// Correct - has Suspense
<Route 
  path="/editor" 
  element={
    <Suspense fallback={<LoadingFallback />}>
      <Judge0CodeEditor />
    </Suspense>
  }
/>
```

---

## ✅ Checklist - Before Going to Production

- [ ] Frontend builds successfully: `npm run build`
- [ ] Backend compiles: `mvn clean compile`
- [ ] Run MongoDB indexes: See [OPTIMIZATION_SUMMARY.md](OPTIMIZATION_SUMMARY.md#step-3-database-indexes-critical-for-performance)
- [ ] Test in browser: Initial load time <2s
- [ ] Check Network tab: Main bundle is ~120KB (not 301KB)
- [ ] Test problem status: Shows up in <600ms (not 2-5s)
- [ ] Test admin page: Loads in <200ms (not hanging)
- [ ] Monitor in production: Set up alerts for slow API responses

---

## 📚 Full Documentation

For detailed technical explanations and implementation details:
- **[OPTIMIZATION_REPORT.md](OPTIMIZATION_REPORT.md)** - Complete technical guide
- **[OPTIMIZATION_SUMMARY.md](OPTIMIZATION_SUMMARY.md)** - Before/after metrics
- **[src/hooks/useCachedApi.js](src/hooks/useCachedApi.js)** - API caching implementation
- **[src/hooks/useProblemStatus.js](src/hooks/useProblemStatus.js)** - Problem status batching

---

## 💡 Tips & Best Practices

### ✅ DO:
- Use `useProblemStatus` when displaying problem statuses
- Use `useCachedApi` for frequently accessed endpoints
- Lazy load components that aren't immediately needed
- Monitor performance with DevTools
- Profile your code with React DevTools

### ✗ DON'T:
- Make individual API calls in a loop (N+1 pattern)
- Cache sensitive data indefinitely
- Lazy load components in critical path (homepage)
- Fetch same data in multiple useEffect hooks
- Ignore warnings in browser console

---

## 🆘 Getting Help

If something breaks:

1. Check browser console for errors
2. Check Network tab for failed requests
3. Look at backend logs: `mvn spring-boot:run`
4. Check if indexes were created: `db.collection.getIndexes()`
5. Refer to full documentation above

**Common Issues**:
- "Cannot lazy load" → Missing Suspense wrapper
- "Cache not clearing" → Call clearCache() after mutations
- "Indexes not working" → Run mongodb-indexes.js script
- "Build fails" → Check Node version: `node -v` (should be 16+)
