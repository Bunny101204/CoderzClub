import { useState, useEffect, lazy, Suspense } from "react";
import {
  BrowserRouter as Router,
  Routes,
  Route,
  Navigate,
} 
from "react-router-dom";
import reactLogo from "./assets/react.svg";
import viteLogo from "/vite.svg";
import "./App.css";

// Static imports for above-the-fold components
import LandingPage from "./Components/LandingPage";
import Header from "./Components/Header";
import AuthPage from "./Components/AuthPage";
import ErrorBoundary from "./Components/ErrorBoundary";
import { useAuth, AuthProvider } from "./context/AuthContext";
import { api } from "./apiClient";

// Lazy load route-specific components
const Judge0CodeEditor = lazy(() => import("./Components/Judge0CodeEditor"));
const HomePage = lazy(() => import("./Components/HomePage"));
const ProblemPage = lazy(() => import("./Components/ProblemPage"));
const ProblemPageNew = lazy(() => import("./Components/ProblemPageNew"));
const AdminDashboard = lazy(() => import("./Components/AdminDashboard"));
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

// Fallback component for Suspense
function LoadingFallback() {
  return (
    <div className="min-h-screen bg-gray-900 flex items-center justify-center">
      <div className="text-white text-lg">Loading...</div>
    </div>
  );
}

function AdminRoute({ user }) {
  const isAdmin = user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin");

  if (!isAdmin) {
    return (
      <div className="min-h-screen bg-gray-900 text-white flex items-center justify-center">
        <div className="text-center">
          <h2 className="text-2xl font-bold mb-4">Access Denied</h2>
          <p className="text-gray-400 mb-4">You need admin privileges to access this page.</p>
          <p className="text-sm text-gray-500 mb-4">Your role: {user?.role || "Not set"}</p>
          <Navigate to="/home" replace />
        </div>
      </div>
    );
  }

  return <AdminDashboard />;
}

function AppContent() {
  const { isAuthenticated, user, loading } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="text-white text-xl">Loading...</div>
      </div>
    );
  }

  // App no longer fetches the full problem list globally; individual pages fetch as needed.

  return (
    <div className="min-h-screen bg-gray-900">
      {isAuthenticated && <Header />}
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
          element={<Suspense fallback={<LoadingFallback />}><ProblemPage /></Suspense>}
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
          path="/admin"
          element={<Suspense fallback={<LoadingFallback />}><AdminRoute user={user} /></Suspense>}
        />
        <Route
          path="/admin/add-problem"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><AddProblemNew /></Suspense>
            ) : (
              <Navigate to="/home" replace />
            )
          }
        />
        <Route
          path="/admin/add-problem-old"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><AddProblem /></Suspense>
            ) : (
              <Navigate to="/home" replace />
            )
          }
        />
        <Route
          path="/admin/add-bundle"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><AddBundle /></Suspense>
            ) : (
              <Navigate to="/home" replace />
            )
          }
        />
        <Route
          path="/admin/edit-bundle/:id"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><AddBundle /></Suspense>
            ) : (
              <Navigate to="/home" replace />
            )
          }
        />
        <Route
          path="/admin/manage-bundle/:id"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <Suspense fallback={<LoadingFallback />}><ManageBundleProblems /></Suspense>
            ) : (
              <Navigate to="/home" replace />
            )
          }
        />
        {/* Catch-all for unknown routes */}
        <Route path="*" element={<Navigate to={isAuthenticated ? "/home" : "/"} replace />} />
      </Routes>
    </div>
  );
}

function App() {
  return (
    <ErrorBoundary>
      <AuthProvider>
        <Router>
          <AppContent />
        </Router>
      </AuthProvider>
    </ErrorBoundary>
  );
}

export default App;
