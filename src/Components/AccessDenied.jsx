import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function AccessDenied() {
  const { isAuthenticated } = useAuth();
  return (
    <div className="app-shell px-4 py-10">
      <div className="max-w-lg mx-auto app-surface rounded-lg p-6 sm:p-8">
        <h1 className="text-2xl font-bold mb-3">Access denied</h1>
        <p className="app-muted mb-6">
          {isAuthenticated
            ? "This page is limited to administrators."
            : "Sign in with an administrator account to open this page."}
        </p>
        <div className="flex flex-wrap gap-3">
          <Link to={isAuthenticated ? "/home" : "/"} className="px-4 py-2 rounded bg-blue-600 text-white">
            Go to home
          </Link>
          {!isAuthenticated && (
            <Link to="/auth" className="px-4 py-2 rounded bg-gray-200 text-gray-900 dark:bg-gray-700 dark:text-white">
              Sign in
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}
