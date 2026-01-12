import { useState, useEffect } from "react";
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
import Judge0CodeEditor from "./Components/Judge0CodeEditor";
import HomePage from "./Components/HomePage";
import ProblemPage from "./Components/ProblemPage";
import ProblemPageNew from "./Components/ProblemPageNew";
import LoginPage from "./Components/LoginPage";
import { useAuth, AuthProvider } from "./context/AuthContext";
import Header from "./Components/Header";
import AuthPage from "./Components/AuthPage";
import AdminDashboard from "./Components/AdminDashboard";
import AddProblem from "./Components/AddProblem";
import AddProblemNew from "./Components/AddProblemNew";
import AddBundle from "./Components/AddBundle";
import BundleDashboard from "./Components/BundleDashboard";
import BundleProblems from "./Components/BundleProblems";
import ManageBundleProblems from "./Components/ManageBundleProblems";
import SubscriptionPlans from "./Components/SubscriptionPlans";
import Leaderboard from "./Components/Leaderboard";
import UserStats from "./Components/UserStats";
import Profile from "./Components/Profile";
import LandingPage from "./Components/LandingPage";

function AdminPage({ problems, setProblems, onLogout }) {
  return (
    <div className="min-h-screen bg-gray-900 text-white p-6">
      <h2 className="text-2xl font-bold mb-4">Admin Dashboard</h2>
      <p>Welcome, admin! (Problem management UI coming next.)</p>
      <button onClick={onLogout}>Logout</button>
    </div>
  );
}

// Admin Route Wrapper Component
function AdminRoute({ user, problems }) {
  const isAdmin = user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin");
  
  console.log("[AdminRoute] Checking access:", { 
    user: user?.username, 
    role: user?.role, 
    isAdmin 
  });
  
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
  
  return <AdminDashboard problems={problems} />;
}

function AppContent() {
  const { isAuthenticated, user, loading } = useAuth();
  const [problems, setProblems] = useState([]);
  const [problemsLoading, setProblemsLoading] = useState(false);
  const [problemsError, setProblemsError] = useState(null);

  useEffect(() => {
    async function fetchProblems() {
      setProblemsLoading(true);
      setProblemsError(null);
      try {
        const res = await fetch("/api/problems");
        if (!res.ok) throw new Error("Failed to fetch problems");
        const data = await res.json();
        setProblems(data);
      } catch (err) {
        setProblemsError(err.message);
      } finally {
        setProblemsLoading(false);
      }
    }
    if (isAuthenticated) {
      fetchProblems();
    } else {
      setProblems([]);
      setProblemsLoading(false);
      setProblemsError(null);
    }
  }, [isAuthenticated]);

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="text-white text-xl">Loading...</div>
      </div>
    );
  }

  if (problemsLoading) {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="text-white text-xl">Loading problems...</div>
      </div>
    );
  }
  if (problemsError) {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="text-red-400 text-xl">{problemsError}</div>
      </div>
    );
  }

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
              path="/home"
              element={<Navigate to="/" replace />}
            />
            <Route
              path="/auth"
              element={<AuthPage />}
            />
          </>
        ) : (
          <>
            <Route
              path="/home"
              element={<HomePage problems={problems} />}
            />
            <Route
              path="/"
              element={<Navigate to="/home" replace />}
            />
            <Route
              path="/auth"
              element={<Navigate to="/home" replace />}
            />
          </>
        )}
        <Route
          path="/editor"
          element={<Judge0CodeEditor />}
        />
        <Route
          path="/problem/:id"
          element={<ProblemPageNew problems={problems} />}
        />
        <Route
          path="/problem-old/:id"
          element={<ProblemPage problems={problems} />}
        />
        <Route
          path="/bundles"
          element={<BundleDashboard />}
        />
        <Route
          path="/bundle/:id"
          element={<BundleProblems />}
        />
        <Route
          path="/subscription"
          element={<SubscriptionPlans />}
        />
        <Route
          path="/leaderboard"
          element={<Leaderboard />}
        />
        <Route
          path="/stats"
          element={<UserStats />}
        />
        <Route
          path="/profile"
          element={<Profile isOpen={true} onClose={() => {}} asPage={true} />}
        />
        <Route
          path="/admin"
          element={<AdminRoute user={user} problems={problems} />}
        />
        <Route
          path="/admin/add-problem"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <AddProblemNew />
            ) : (
              <Navigate to="/home" replace />
            )
          }
        />
        <Route
          path="/admin/add-problem-old"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <AddProblem />
            ) : (
              <Navigate to="/home" replace />
            )
          }
        />
        <Route
          path="/admin/add-bundle"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <AddBundle />
            ) : (
              <Navigate to="/home" replace />
            )
          }
        />
        <Route
          path="/admin/edit-bundle/:id"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <AddBundle />
            ) : (
              <Navigate to="/home" replace />
            )
          }
        />
        <Route
          path="/admin/manage-bundle/:id"
          element={
            user && (user.role === "ADMIN" || user.role === "admin" || user.role === "Admin") ? (
              <ManageBundleProblems />
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
  const [isAdmin, setIsAdmin] = useState(() => {
    // Read from localStorage on first load
    return localStorage.getItem("isAdmin") === "true";
  });

  useEffect(() => {
    localStorage.setItem("isAdmin", isAdmin ? "true" : "false");
  }, [isAdmin]);

  const handleLogin = () => setIsAdmin(true);
  const handleLogout = () => setIsAdmin(false);

  return (
    <AuthProvider>
      <Router>
        <Routes>
          {/* Catch-all route for all app content */}
          <Route path="*" element={<AppContent />} />
          {/* Admin login route changed to /al */}
          <Route path="/al" element={<LoginPage onLogin={handleLogin} />} />
          {/* User login/registration route changed to /login */}
          <Route path="/login" element={<AuthPage />} />
        </Routes>
      </Router>
    </AuthProvider>
  );
}

export default App;
