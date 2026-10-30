import { lazy, Suspense } from "react";
import {
  BrowserRouter as Router,
  Routes,
  Route,
  Navigate,
} 
from "react-router-dom";
import "./App.css";

// Static imports for above-the-fold components
import LandingPage from "./Components/LandingPage";
import Header from "./Components/Header";
import AuthPage from "./Components/AuthPage";
import ErrorBoundary from "./Components/ErrorBoundary";
import SiteFooter from "./Components/SiteFooter";
import { useAuth, AuthProvider } from "./context/AuthContext";
import { ThemeProvider } from "./context/ThemeContext";
import AccessDenied from "./Components/AccessDenied";
import NotFoundPage from "./Components/NotFoundPage";
import LegacyProblemRedirect from "./routing/LegacyProblemRedirect";
import DocumentMeta from "./seo/DocumentMeta";

// Lazy load route-specific components
const Judge0CodeEditor = lazy(() => import("./Components/Judge0CodeEditor"));
const HomePage = lazy(() => import("./Components/HomePage"));
const ProblemPageNew = lazy(() => import("./Components/ProblemPageNew"));
const AdminDashboard = lazy(() => import("./Components/AdminDashboard"));
const AdminBatchPage = lazy(() => import("./Components/AdminBatchPage"));
const MyBatchesPage = lazy(() => import("./Components/MyBatchesPage"));
const StudentBatchPage = lazy(() => import("./Components/StudentBatchPage"));
const AdminStudentsPage = lazy(() => import("./Components/AdminStudentsPage"));
const AddProblem = lazy(() => import("./Components/AddProblem"));
const AddProblemNew = lazy(() => import("./Components/AddProblemNew"));
const AddBundle = lazy(() => import("./Components/AddBundle"));
const BundleDashboard = lazy(() => import("./Components/BundleDashboard"));
const BundleProblems = lazy(() => import("./Components/BundleProblems"));
const ManageBundleProblems = lazy(() => import("./Components/ManageBundleProblems"));
const SubscriptionPlans = lazy(() => import("./Components/SubscriptionPlans"));
const Leaderboard = lazy(() => import("./Components/Leaderboard"));
const UserStats = lazy(() => import("./Components/UserStats"));
const Profile = lazy(() => import("./Components/Profile"));
const PrivacyPage = lazy(() => import("./Components/PrivacyPage"));
const TermsPage = lazy(() => import("./Components/TermsPage"));
const ContestList = lazy(() => import("./Components/contests/ContestList"));
const ContestDetail = lazy(() => import("./Components/contests/ContestDetail"));
const ContestProblemPage = lazy(() => import("./Components/contests/ContestProblemPage"));
const ContestForm = lazy(() => import("./Components/admin/contests/ContestForm"));

// Fallback component for Suspense
function LoadingFallback() {
  return (
    <div className="app-shell flex items-center justify-center">
      <div className="text-lg">Loading...</div>
    </div>
  );
}

function AdminRoute({ user }) {
  const isAdmin = user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin");
  if (!isAdmin) {
    return <AccessDenied />;
  }
  return <AdminDashboard />;
}

function AppContent() {
  const { isAuthenticated, user, loading } = useAuth();

  if (loading) {
    return (
      <div className="app-shell flex items-center justify-center">
        <div className="text-xl">Loading...</div>
      </div>
    );
  }

  // App no longer fetches the full problem list globally; individual pages fetch as needed.

  return (
    <div className="app-shell">
      <DocumentMeta />
      <Header />
      <Routes>
        <Route
          path="/about"
          element={<LandingPage />}
        />
        <Route
          path="/landing"
          element={<LandingPage />}
        />
        {!isAuthenticated ? (
          <>
            <Route
              path="/"
              element={<LandingPage />}
            />
            <Route
              path="/auth"
              element={<AuthPage />}
            />
          </>
        ) : (
          <>
            <Route
              path="/"
              element={<Navigate to="/home" replace />}
            />
            <Route
              path="/home"
              element={<Suspense fallback={<LoadingFallback />}><HomePage /></Suspense>}
            />
            <Route
              path="/auth"
              element={<Navigate to="/home" replace />}
            />
          </>
        )}
        <Route
          path="/editor"
          element={<Suspense fallback={<LoadingFallback />}><Judge0CodeEditor /></Suspense>}
        />
        <Route
          path="/problem/:id"
          element={<Suspense fallback={<LoadingFallback />}><ProblemPageNew /></Suspense>}
        />
        <Route
          path="/problem-old/:id"
          element={<LegacyProblemRedirect />}
        />
        <Route
          path="/bundles"
          element={<Suspense fallback={<LoadingFallback />}><BundleDashboard /></Suspense>}
        />
        <Route
          path="/bundle/:id"
          element={<Suspense fallback={<LoadingFallback />}><BundleProblems /></Suspense>}
        />
        <Route
          path="/subscription"
          element={<Suspense fallback={<LoadingFallback />}><SubscriptionPlans /></Suspense>}
        />
        <Route
          path="/leaderboard"
          element={<Suspense fallback={<LoadingFallback />}><Leaderboard /></Suspense>}
        />
        <Route
          path="/stats"
          element={<Suspense fallback={<LoadingFallback />}><UserStats /></Suspense>}
        />
        <Route
          path="/profile"
          element={<Suspense fallback={<LoadingFallback />}><Profile isOpen={true} onClose={() => {}} asPage={true} /></Suspense>}
        />
        <Route
          path="/contests"
          element={
            isAuthenticated ? (
              <Suspense fallback={<LoadingFallback />}><ContestList /></Suspense>
            ) : (
              <Navigate to="/auth" replace />
            )
          }
        />
        <Route
          path="/contests/:id"
          element={
            isAuthenticated ? (
              <Suspense fallback={<LoadingFallback />}><ContestDetail /></Suspense>
            ) : (
              <Navigate to="/auth" replace />
            )
          }
        />
        <Route
          path="/contests/:contestId/problems/:problemId"
          element={
            isAuthenticated ? (
              <Suspense fallback={<LoadingFallback />}><ContestProblemPage /></Suspense>
            ) : (
              <Navigate to="/auth" replace />
            )
          }
        />
        <Route
          path="/admin"
          element={<Suspense fallback={<LoadingFallback />}><AdminRoute user={user} /></Suspense>}
        />
        <Route
          path="/admin/contests/new"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><ContestForm /></Suspense>
            ) : (
              <AccessDenied />
            )
          }
        />
        <Route
          path="/admin/contests/:id"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><ContestForm /></Suspense>
            ) : (
              <AccessDenied />
            )
          }
        />
        <Route
          path="/admin/students"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><AdminStudentsPage /></Suspense>
            ) : (
              <AccessDenied />
            )
          }
        />
        <Route
          path="/my/batches"
          element={
            user ? (
              <Suspense fallback={<LoadingFallback />}><MyBatchesPage /></Suspense>
            ) : (
              <Navigate to="/auth" replace />
            )
          }
        />
        <Route
          path="/my/batches/enrolled/:id"
          element={
            user ? (
              <Suspense fallback={<LoadingFallback />}><StudentBatchPage /></Suspense>
            ) : (
              <Navigate to="/auth" replace />
            )
          }
        />
        <Route
          path="/my/batches/:id"
          element={
            user ? (
              <Suspense fallback={<LoadingFallback />}><AdminBatchPage /></Suspense>
            ) : (
              <Navigate to="/auth" replace />
            )
          }
        />
        <Route
          path="/admin/batches/:id"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><AdminBatchPage /></Suspense>
            ) : (
              <AccessDenied />
            )
          }
        />
        <Route
          path="/admin/edit-problem/:id"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><AddProblemNew /></Suspense>
            ) : (
              <AccessDenied />
            )
          }
        />
        <Route
          path="/admin/add-problem"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><AddProblemNew /></Suspense>
            ) : (
              <AccessDenied />
            )
          }
        />
        <Route
          path="/admin/add-problem-old"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><AddProblem /></Suspense>
            ) : (
              <AccessDenied />
            )
          }
        />
        <Route
          path="/admin/add-bundle"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><AddBundle /></Suspense>
            ) : (
              <AccessDenied />
            )
          }
        />
        <Route
          path="/admin/edit-bundle/:id"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><AddBundle /></Suspense>
            ) : (
              <AccessDenied />
            )
          }
        />
        <Route
          path="/admin/manage-bundle/:id"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><ManageBundleProblems /></Suspense>
            ) : (
              <AccessDenied />
            )
          }
        />
        <Route
          path="/privacy"
          element={<Suspense fallback={<LoadingFallback />}><PrivacyPage /></Suspense>}
        />
        <Route
          path="/terms"
          element={<Suspense fallback={<LoadingFallback />}><TermsPage /></Suspense>}
        />
        {/* Catch-all for unknown routes */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
      <SiteFooter />
    </div>
  );
}

function App() {
  return (
    <ErrorBoundary>
      <AuthProvider>
        <ThemeProvider>
        <Router>
          <AppContent />
        </Router>
        </ThemeProvider>
      </AuthProvider>
    </ErrorBoundary>
  );
}

export default App;
