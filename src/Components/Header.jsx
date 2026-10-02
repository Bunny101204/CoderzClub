import React from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';

const Header = () => {
  const { user, isAuthenticated } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();

  const isActive = (path) => location.pathname === path;
  const navClass = (path) =>
    `px-3 py-2 rounded-md text-sm font-medium transition-colors ${
      isActive(path)
        ? 'text-gray-900 bg-gray-200 dark:text-white dark:bg-gray-700'
        : 'text-gray-700 hover:text-gray-900 hover:bg-gray-100 dark:text-gray-300 dark:hover:text-white dark:hover:bg-gray-700'
    }`;

  return (
    <header className="bg-white/90 dark:bg-gray-800/90 backdrop-blur-sm border-b border-gray-200 dark:border-gray-700 sticky top-0 z-40">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          <Link to={isAuthenticated ? "/home" : "/"} className="flex items-center space-x-2">
            <div className="w-8 h-8 bg-gradient-to-r from-blue-500 to-purple-600 rounded-lg flex items-center justify-center">
              <span className="text-white font-bold text-sm">C</span>
            </div>
            <span className="font-bold text-xl text-gray-900 dark:text-white">CoderzClub</span>
          </Link>

          {isAuthenticated && (
            <nav className="hidden md:flex items-center space-x-2" aria-label="Main">
              <Link to="/" className={navClass('/')}>Problems</Link>
              <Link to="/bundles" className={navClass('/bundles')}>Bundles</Link>
              <Link to="/editor" className={navClass('/editor')}>Editor</Link>
              <Link to="/leaderboard" className={navClass('/leaderboard')}>Leaderboard</Link>
              {(user?.role === "ADMIN" || user?.role === "admin" || user?.role === "Admin") && (
                <Link to="/admin" className={navClass('/admin')}>Admin</Link>
              )}
            </nav>
          )}

          <div className="flex items-center space-x-3">
            <button
              type="button"
              onClick={toggleTheme}
              className="px-3 py-2 rounded-lg text-sm font-medium bg-gray-100 text-gray-900 hover:bg-gray-200 dark:bg-gray-700 dark:text-white dark:hover:bg-gray-600"
              aria-label={theme === "dark" ? "Switch to light theme" : "Switch to dark theme"}
            >
              {theme === "dark" ? "Light" : "Dark"}
            </button>
            {isAuthenticated && (
              <button
                type="button"
                onClick={() => navigate('/profile')}
                className="flex items-center space-x-2 bg-gray-100 hover:bg-gray-200 dark:bg-gray-700 dark:hover:bg-gray-600 rounded-lg px-3 py-2 transition-colors"
              >
                <div className="w-8 h-8 bg-gradient-to-r from-blue-500 to-purple-600 rounded-full flex items-center justify-center">
                  <span className="text-white font-bold text-sm">
                    {user?.username?.charAt(0).toUpperCase()}
                  </span>
                </div>
                <span className="text-sm font-medium hidden sm:block text-gray-900 dark:text-white">
                  {user?.username}
                </span>
              </button>
            )}
          </div>
        </div>
      </div>
    </header>
  );
};

export default Header;
