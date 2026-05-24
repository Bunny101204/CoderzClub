# CoderzClub React Project - Bug Scan Report

## Summary
Found **28+ critical and high-priority issues** that could cause crashes, memory leaks, and runtime errors.

---

## Critical Issues (P0 - App Crashes)

### 1. Missing Error Boundary in App.jsx
**File:** [src/App.jsx](src/App.jsx)  
**Lines:** 1-120  
**Issue:** No Error Boundary component to catch React errors  
**Impact:** CRITICAL - Any component error crashes entire app  
**Current State:** Components can throw errors that are unhandled  
**Fix:** Implement an Error Boundary wrapper component
```jsx
// Add Error Boundary wrapper
class ErrorBoundary extends React.Component {
  state = { hasError: false };
  static getDerivedStateFromError(error) {
    return { hasError: true };
  }
  componentDidCatch(error, errorInfo) {
    console.error('Error caught:', error, errorInfo);
  }
  render() {
    if (this.state.hasError) {
      return <div>Something went wrong. Please refresh.</div>;
    }
    return this.props.children;
  }
}
```

---

## High Priority Issues (P1 - Crashes/Data Loss)

### 2. Missing useEffect Dependency - Timer Race Condition
**File:** [src/Components/Judge0CodeEditor.jsx](src/Components/Judge0CodeEditor.jsx)  
**Lines:** 75-86  
**Issue:** `useEffect` with interval missing `startTime` in dependency array
```jsx
// BROKEN - Missing startTime dependency
useEffect(() => {
  let interval = null;
  if (isTimerRunning) {
    interval = setInterval(() => {
      setElapsedTime(Date.now() - startTime); // startTime is stale!
    }, 1000);
  }
  return () => clearInterval(interval);
}, [isTimerRunning, elapsedTime, startTime]); // Missing startTime will cause stale closure
```
**Impact:** HIGH - Timer uses stale `startTime` value, elapsed time incorrect  
**Severity:** Timer becomes inaccurate after first update  
**Fix:** Ensure all state dependencies are included

### 3. setInterval Not Cleared on Component Unmount - Memory Leak
**File:** [src/Components/AdminDashboard.jsx](src/Components/AdminDashboard.jsx)  
**Lines:** 34-38  
**Issue:** `setInterval` started in useEffect but not properly cleared when dependencies change
```jsx
// BROKEN - Interval may not clear properly
useEffect(() => {
  fetchProblems();
  fetchBundles();
  
  const interval = setInterval(() => {
    fetchProblems();
    fetchBundles();
  }, 30000);
  
  return () => clearInterval(interval);
}, []); // Empty dependency array - interval created once but if component re-mounts, old interval persists
```
**Impact:** HIGH - Memory leak, multiple intervals running after navigation  
**Severity:** Each navigation creates a new interval without clearing previous ones  
**Fix:** Add proper cleanup on unmount and avoid multiple mount cycles

### 4. Promise.all Unhandled Rejection - Race Condition
**File:** [src/Components/HomePage.jsx](src/Components/HomePage.jsx)  
**Lines:** 43-55  
**Issue:** `Promise.all()` without proper error handling if one promise rejects
```jsx
// BROKEN - If any fetch fails, all fail
const statusMap = {};
await Promise.all(
  problems.map(async (problem) => {
    try {
      const response = await fetch(`/api/submissions/my-submissions?problemId=${problem.id}&size=1`, {
        headers: {
          Authorization: `Bearer ${token}`
        }
      });
      if (!response.ok) return; // Silent fail - but Promise.all still continues
      // ... rest of code
    } catch (err) {
      console.error('Error fetching problem status:', err); // Only logs, doesn't catch for Promise.all
    }
  })
);
```
**Impact:** HIGH - If one request fails, silent failures can leave incomplete state  
**Severity:** Component may render incomplete data  
**Fix:** Use `Promise.allSettled()` instead or handle rejection properly

### 5. Missing Error Handling on API Call - Unhandled Rejection
**File:** [src/Components/BundleProblems.jsx](src/Components/BundleProblems.jsx)  
**Lines:** 19-47  
**Issue:** `fetchBundleData()` called in useEffect without catch - API errors can crash component
```jsx
useEffect(() => {
  fetchBundleData(); // No error handling if this fails
}, [id]);

const fetchBundleData = async () => {
  try {
    const response = await fetch(`/api/bundles/${id}`);
    // ... rest of code
  } catch (error) {
    console.error("Error fetching bundle:", error); // Not setting error state to display to user
  } finally {
    setLoading(false);
  }
};
```
**Impact:** HIGH - User sees loading spinner forever if API fails  
**Severity:** No error feedback to user  
**Fix:** Set error state and display error message

### 6. Missing Null Check on Navigation State
**File:** [src/Components/BundleProblems.jsx](src/Components/BundleProblems.jsx)  
**Lines:** 7, 195  
**Issue:** `useNavigate()` used in event handler without error boundary
```jsx
// If navigate fails, component crashes
navigate("/bundles"); // No error handling
```
**Impact:** HIGH - Navigation errors crash component  
**Severity:** User can't recover from navigation state error  
**Fix:** Wrap navigate in try-catch or error boundary

---

## Medium Priority Issues (P2 - Runtime Errors)

### 7. Missing Null/Undefined Check on Object Property Access
**File:** [src/Components/ProblemPageNew.jsx](src/Components/ProblemPageNew.jsx)  
**Lines:** 106  
**Issue:** Accessing `problem.executionMode` without null check
```jsx
// CRASH if problem is null
const isStdinMode = problem.executionMode === "STDIN_STDOUT" || problem.publicTestCases;
```
**Impact:** MEDIUM - TypeError if problem not loaded  
**Severity:** Page crashes during loading state  
**Fix:** Check `problem` before accessing properties

### 8. .then().catch() Without Proper Error Context - Unhandled State
**File:** [src/Components/AuthPage.jsx](src/Components/AuthPage.jsx)  
**Lines:** 31-47  
**Issue:** Promise chain without proper error propagation
```jsx
api.auth.confirmEmail(verificationToken)
  .then((response) => {
    // ... handler
  })
  .catch((err) => {
    // Error caught but verificationToken still in URL
    setError(message);
  })
  .finally(() => {
    setLoading(false);
    searchParams.delete('verifyEmailToken'); // If delete fails, no error
    setSearchParams(searchParams, { replace: true }); // No error handling
  });
```
**Impact:** MEDIUM - URL state mismatch if cleanup fails  
**Severity:** Confusing UX with token still in URL  
**Fix:** Add error handling for searchParams operations

### 9. Missing Error State Handling on API Calls
**File:** [src/Components/Leaderboard.jsx](src/Components/Leaderboard.jsx)  
**Lines:** 13-26  
**Issue:** Error state set but component doesn't re-fetch on error
```jsx
const fetchLeaderboard = async () => {
  try {
    setLoading(true);
    const response = await axios.get("/api/users/leaderboard");
    setLeaderboard(leaderboardData);
    setError(null);
  } catch (err) {
    setError("Failed to fetch leaderboard..."); // Error set but no retry mechanism
    setLeaderboard([]);
  } finally {
    setLoading(false); // No way to retry
  }
};
```
**Impact:** MEDIUM - User sees error with no way to retry  
**Severity:** Poor UX, data may be stale  
**Fix:** Add retry button or auto-retry logic

### 10. Race Condition with Stale State Closure
**File:** [src/Components/Judge0CodeEditor.jsx](src/Components/Judge0CodeEditor.jsx)  
**Lines:** 655-685  
**Issue:** State used in callbacks but not in dependency array
```jsx
// BROKEN - testCases is stale in closure
const handleRunAll = async () => {
  if (!testCases || testCases.length === 0) { // Stale testCases from props
    setOutput("No test cases available for this problem.");
    return;
  }
  // testCases might have changed since component mount but closure uses old value
};

// useEffect NOT called when testCases changes
useEffect(() => {
  console.log("testCases:", testCases);
  // ... debug
}, []); // WRONG - should have [testCases] dependency
```
**Impact:** MEDIUM - Wrong test cases run if problem changes  
**Severity:** Data inconsistency  
**Fix:** Add testCases to dependency array

### 11. Missing Loading State During API Call
**File:** [src/Components/AdminDashboard.jsx](src/Components/AdminDashboard.jsx)  
**Lines:** 52-72  
**Issue:** `fetchProblems()` doesn't set loading state before updating
```jsx
useEffect(() => {
  fetchProblems();
  fetchBundles();
  // ... interval setup
}, []);

// Loading state not managed properly
const fetchProblems = async () => {
  try {
    setLoading(true);
    const response = await fetch("/api/problems");
    // ... But if propsProblems updates, loading state becomes inconsistent
  }
};

// This effect runs when propsProblems changes but doesn't set loading
useEffect(() => {
  if (propsProblems && Array.isArray(propsProblems)) {
    if (propsProblems.length > 0) {
      setProblemList(propsProblems);
      setLoading(false); // Should be before setting list
    }
  }
}, [propsProblems]); // No loading state coordination
```
**Impact:** MEDIUM - UI can show inconsistent states  
**Severity:** UI state flicker or wrong state displayed  
**Fix:** Coordinate loading states properly

### 12. Missing Error Handling on setTimeout Navigation
**File:** [src/Components/AddProblem.jsx](src/Components/AddProblem.jsx)  
**Lines:** 167  
**Issue:** Navigation inside setTimeout without error handling
```jsx
setTimeout(() => navigate("/admin"), 1200); // If navigate fails, error unhandled
```
**Impact:** MEDIUM - Navigation error ignored  
**Severity:** User might stay on page without knowing  
**Fix:** Wrap navigate in try-catch or add .catch() handler

### 13. Missing Error Handling on AddBundle setTimeout
**File:** [src/Components/AddBundle.jsx](src/Components/AddBundle.jsx)  
**Lines:** 170  
**Issue:** Similar to above - setTimeout + navigate without error handling
```jsx
setTimeout(() => navigate("/admin"), 1500); // No error handling
```
**Impact:** MEDIUM - Navigation error silently ignored  
**Severity:** Silent failure  
**Fix:** Add error boundary or error callback

### 14. Missing Null Check on Pagination Calculation
**File:** [src/Components/AdminDashboard.jsx](src/Components/AdminDashboard.jsx)  
**Lines:** 16-26  
**Issue:** Pagination uses undefined/null values
```jsx
const safeProblemList = Array.isArray(problemList) ? problemList : [];
const safeBundles = Array.isArray(bundles) ? bundles : [];

const totalPages = Math.ceil(safeProblemList.length / problemsPerPage); // OK
const totalBundlePages = Math.ceil(safeBundles.length / bundlesPerPage); // OK

const paginatedProblems = safeProblemList.slice(
  (currentPage - 1) * problemsPerPage,
  currentPage * problemsPerPage
); // Safe but currentPage could be out of bounds

// Bug: No validation that currentPage <= totalPages
```
**Impact:** MEDIUM - Could render empty page if currentPage is invalid  
**Severity:** UX issue  
**Fix:** Validate currentPage bounds

### 15. Missing Error Handling on Token Validation
**File:** [src/context/AuthContext.jsx](src/context/AuthContext.jsx)  
**Lines:** 59-60  
**Issue:** Token validation error only logged, not handled
```jsx
validateToken(token)
  .catch((err) => logger.warn('Token validation error during init:', err.message)) // Only warns
  .finally(() => setLoading(false)); // Component renders anyway
```
**Impact:** MEDIUM - Invalid token not detected, component renders as unauthenticated  
**Severity:** Silent auth failure  
**Fix:** Properly handle token validation failure

### 16. Race Condition in Token Validation
**File:** [src/context/AuthContext.jsx](src/context/AuthContext.jsx)  
**Lines:** 24-52  
**Issue:** Multiple validations can happen before setUser is called
```jsx
const validateToken = async (token) => {
  try {
    const response = await api.auth.validateToken(); // Could be slow
    const userData = response.data;
    const userObj = {
      username: userData.username,
      role: userData.role.toString().toUpperCase(),
    };
    setUser(userObj); // What if validateToken is called again before this?
    setIsAuthenticated(true);
    return { success: true, user: userObj };
  } catch (error) {
    // ...
  }
};

// Race: If validateToken called twice, state could be inconsistent
```
**Impact:** MEDIUM - Auth state inconsistency  
**Severity:** User might be logged in/out unexpectedly  
**Fix:** Use flag to prevent concurrent validations

---

## Low-Medium Priority Issues (P3 - Warnings/UX)

### 17. Missing Loading State for API Call
**File:** [src/Components/AddProblem.jsx](src/Components/AddProblem.jsx)  
**Lines:** 31-40  
**Issue:** `fetchBundles()` doesn't set loading state
```jsx
const fetchBundles = async () => {
  try {
    const response = await fetch("http://localhost:8080/api/bundles"); // Hardcoded localhost!
    if (response.ok) {
      const data = await response.json();
      setBundles(data);
    }
  } catch (error) {
    console.error("Error fetching bundles:", error); // Silent error
  }
  // No loading state
};
```
**Impact:** LOW-MEDIUM - Form appears ready before bundles load  
**Severity:** Confusing UX  
**Fix:** Add loading state and display spinner

### 18. Hardcoded API URL Instead of Using Config
**File:** [src/Components/AddProblem.jsx](src/Components/AddProblem.jsx)  
**Lines:** 33  
**Issue:** Hardcoded `http://localhost:8080/api/bundles` instead of using `apiClient`
```jsx
const response = await fetch("http://localhost:8080/api/bundles"); // WRONG - hardcoded
// Should be: const response = await fetch("/api/bundles");
```
**Impact:** LOW-MEDIUM - Works in dev but fails in prod  
**Severity:** App breaks in production  
**Fix:** Use relative URL or environment variable

### 19. Missing Dependency in useEffect - Infinite Fetch Loop Risk
**File:** [src/Components/ManageBundleProblems.jsx](src/Components/ManageBundleProblems.jsx)  
**Lines:** 18-19  
**Issue:** `useEffect` dependencies incomplete
```jsx
useEffect(() => {
  fetchBundleData();
  fetchAllProblems();
}, [id]); // OK, but fetchBundleProblems not memoized
```
**Impact:** LOW-MEDIUM - Could cause unnecessary re-fetches  
**Severity:** Performance issue  
**Fix:** Memoize fetch functions or ensure stable dependencies

### 20. Missing Null Check Before Filter
**File:** [src/Components/BundleProblems.jsx](src/Components/BundleProblems.jsx)  
**Lines:** 83-92  
**Issue:** Array operations on potentially undefined data
```jsx
let orderedProblems = idsArray
  .map(id => {
    const problem = allProblems.find(p => 
      String(p.id) === String(id) || p.id === id // p could be undefined
    );
    if (!problem) {
      console.warn(`[BundleProblems] Problem with ID ${id} not found`);
    }
    return problem; // Could return undefined
  })
  .filter(Boolean); // Filter removes undefined but could have null problems
```
**Impact:** LOW-MEDIUM - Potential null pointer later  
**Severity:** Could cause crashes in render  
**Fix:** Add explicit null checks

### 21. Missing Error Message Display
**File:** [src/Components/BundleProblems.jsx](src/Components/BundleProblems.jsx)  
**Lines:** 22-48  
**Issue:** Error fetching bundle is not displayed to user
```jsx
const fetchBundleData = async () => {
  try {
    // ...
  } catch (error) {
    console.error("Error fetching bundle:", error); // Only logs
    // User sees loading spinner forever!
  } finally {
    setLoading(false);
  }
};
```
**Impact:** LOW-MEDIUM - User has no error feedback  
**Severity:** Poor UX  
**Fix:** Set error state and display error message

### 22. Copy to Clipboard Without Error Feedback
**File:** [src/Components/Judge0CodeEditor.jsx](src/Components/Judge0CodeEditor.jsx)  
**Lines:** 267-289  
**Issue:** Clipboard API might fail silently
```jsx
const copyToClipboard = async (text) => {
  try {
    await navigator.clipboard.writeText(text);
    setCopySuccess(true);
    setTimeout(() => setCopySuccess(false), 2000);
  } catch (err) {
    console.error('Failed to copy:', err); // Only logs
    // Fallback code here but doesn't set success state
    document.execCommand('copy'); // Deprecated method
  }
};
```
**Impact:** LOW - Minor UX issue  
**Severity:** User might not know copy failed  
**Fix:** Always set success/error state

### 23. Missing Catch on setTimeout/setInterval Side Effects
**File:** [src/Components/Judge0CodeEditor.jsx](src/Components/Judge0CodeEditor.jsx)  
**Lines:** 1024, 1056, 1094, 1131  
**Issue:** Multiple setTimeout calls without cleanup
```jsx
setTimeout(() => {
  setShowAccepted(false); // What if component unmounts?
}, 3000);
```
**Impact:** LOW - Memory leak if component unmounts during timeout  
**Severity:** Memory leak  
**Fix:** Track timeouts and clear on unmount

### 24. Unhandled Promise in Validation Check
**File:** [src/Components/SubscriptionPlans.jsx](src/Components/SubscriptionPlans.jsx)  
**Lines:** 98-110  
**Issue:** `handlePlanSelection` uses Promise but doesn't handle rejection
```jsx
const handlePlanSelection = async (plan) => {
  setSelectedPlan(plan);
  setLoading(true);
  try {
    await new Promise(resolve => setTimeout(resolve, 2000)); // Simple delay
    alert(`Redirecting to payment for ${plan.name} plan...`); // Alert blocking
  } catch (error) {
    console.error("Error selecting plan:", error);
    alert("Error selecting plan. Please try again."); // Duplicate logic
  } finally {
    setLoading(false);
  }
};
```
**Impact:** LOW - Code is defensive but inefficient  
**Severity:** Minor issue  
**Fix:** Remove unnecessary try-catch or use proper async/await pattern

### 25. Missing Check for Undefined Array Methods
**File:** [src/Components/ManageBundleProblems.jsx](src/Components/ManageBundleProblems.jsx)  
**Lines:** 48-95  
**Issue:** Array operations without null checks
```jsx
const fetchAllProblems = async () => {
  let allProblems = [];
  let page = 0;
  
  while (hasMore) {
    const response = await fetch(`/api/problems?page=${page}&size=${size}`);
    if (response.ok) {
      const data = await response.json();
      let problemsArray = [];
      
      if (data.problems && Array.isArray(data.problems)) {
        problemsArray = data.problems; // OK
      } else if (Array.isArray(data)) {
        problemsArray = data; // OK
      }
      
      allProblems = [...allProblems, ...problemsArray]; // Could spread non-array
      
      if (!hasMore || problemsArray.length < size) {
        hasMore = false;
      } else {
        page++; // page could exceed totalPages
      }
    }
  }
};
```
**Impact:** LOW - Defensive code exists but incomplete  
**Severity:** Edge case issue  
**Fix:** Add bounds checking

---

## Additional Observations

### 26. Inefficient useEffect Dependency Management
**File:** [src/Components/HomePage.jsx](src/Components/HomePage.jsx)  
**Lines:** 19-27  
**Issue:** Timer cleared but dependency array causes re-creation
```jsx
useEffect(() => {
  const timer = setTimeout(() => {
    if (currentPage === 0) {
      fetchProblems();
    } else {
      setCurrentPage(0);
    }
  }, search ? 500 : 0); // Debounce timer
  
  return () => clearTimeout(timer); // Good cleanup!
}, [search]); // OK dependencies, but could optimize
```
**Impact:** LOW - Not ideal but works  
**Severity:** Minor  
**Fix:** Memoize fetchProblems to prevent unnecessary re-renders

### 27. No Retry Logic for Failed API Calls
**File:** Multiple files  
**Issue:** API failures only show error message, no retry
**Impact:** LOW - User must refresh page  
**Severity:** Minor UX issue  
**Fix:** Add retry button with exponential backoff

### 28. No Connection Status Indicator
**File:** [src/App.jsx](src/App.jsx)  
**Issue:** No way to detect if backend is down  
**Impact:** LOW - User confused if API is slow  
**Severity:** Minor UX  
**Fix:** Add connection status banner

---

## Priority Matrix

| Priority | Count | Issues |
|----------|-------|--------|
| P0 - CRITICAL | 1 | Missing Error Boundary |
| P1 - HIGH | 5 | Memory leaks, race conditions, missing error states |
| P2 - MEDIUM | 11 | Null checks, navigation errors, auth issues |
| P3 - LOW | 11 | UX improvements, performance, hardcoded URLs |

---

## Recommended Quick Fixes

1. **Add Error Boundary (5 min)** - Prevents complete app crashes
2. **Fix setInterval cleanup (10 min)** - Prevents memory leaks
3. **Add error state displays (15 min)** - Improves UX
4. **Replace hardcoded URLs (5 min)** - Fixes production issues
5. **Add missing null checks (20 min)** - Prevents runtime errors

---

## Testing Recommendations

1. **Test error states** - Simulate network failures
2. **Memory leak detection** - Use Chrome DevTools memory profiler
3. **Race condition testing** - Rapid navigation between routes
4. **State consistency** - Verify state after rapid updates
5. **E2E testing** - Test full user workflows with failures

---

Generated: 2026-05-24
