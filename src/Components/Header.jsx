import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { ThemeToggleIcon } from "../icons/AppIcons.jsx";
import { themeTogglePresentation } from "../icons/themeToggle.js";
import {
  DESKTOP_NAV_CLASS,
  MOBILE_MENU_BUTTON_CLASS,
  MOBILE_NAV_CLASS,
  navigationItems,
  reduceMobileMenu,
} from "../navigation/mainNav";

const Header = () => {
  const { user, isAuthenticated } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const items = navigationItems({ isAuthenticated, role: user?.role });

  useEffect(() => {
    setMenuOpen((open) => reduceMobileMenu(open, { type: "route" }));
  }, [location.pathname]);

  useEffect(() => {
    if (!menuOpen) {
      return undefined;
    }
    const onKeyDown = (event) => {
      if (event.key === "Escape") {
        setMenuOpen((open) => reduceMobileMenu(open, { type: "escape" }));
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [menuOpen]);

  const isActive = (path) => {
    if (path === "/contests") {
      return location.pathname === "/contests" || location.pathname.startsWith("/contests/");
    }
    if (path === "/admin") {
      return location.pathname === "/admin" || location.pathname.startsWith("/admin/");
    }
    return location.pathname === path;
  };
  const navClass = (path) =>
    `px-3 py-2 rounded-md text-sm font-medium transition-colors ${
      isActive(path)
        ? 'text-gray-900 bg-gray-200 dark:text-white dark:bg-gray-700'
        : 'text-gray-700 hover:text-gray-900 hover:bg-gray-100 dark:text-gray-300 dark:hover:text-white dark:hover:bg-gray-700'
    }`;
  const closeMenu = () => setMenuOpen((open) => reduceMobileMenu(open, { type: "navigate" }));

  return (
    <header className="bg-white/90 dark:bg-gray-800/90 backdrop-blur-sm border-b border-gray-200 dark:border-gray-700 sticky top-0 z-40">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16 gap-2 min-w-0">
          <Link to={isAuthenticated ? "/home" : "/"} className="flex items-center space-x-2 min-w-0">
            <div className="w-8 h-8 shrink-0 bg-gradient-to-r from-blue-500 to-purple-600 rounded-lg flex items-center justify-center">
              <span className="text-white font-bold text-sm">C</span>
            </div>
            <span className="font-bold text-xl text-gray-900 dark:text-white truncate">CoderzClub</span>
          </Link>

          {isAuthenticated && (
            <nav className={DESKTOP_NAV_CLASS} aria-label="Main">
              {items.map((item) => (
                <Link key={item.to} to={item.to} className={navClass(item.to)}>{item.label}</Link>
              ))}
            </nav>
          )}

          <div className="flex items-center space-x-2 sm:space-x-3 shrink-0">
            {isAuthenticated && (
              <button
                type="button"
                className={MOBILE_MENU_BUTTON_CLASS}
                aria-expanded={menuOpen}
                aria-controls="mobile-nav"
                aria-label={menuOpen ? "Close menu" : "Open menu"}
                onClick={() => setMenuOpen((open) => reduceMobileMenu(open, { type: "toggle" }))}
              >
                <span aria-hidden="true">{menuOpen ? "✕" : "☰"}</span>
              </button>
            )}
            <button
              type="button"
              onClick={toggleTheme}
              className="p-2 rounded-lg bg-gray-100 text-gray-900 hover:bg-gray-200 dark:bg-gray-700 dark:text-white dark:hover:bg-gray-600"
              aria-label={themeTogglePresentation(theme).label}
              title={themeTogglePresentation(theme).label}
            >
              <ThemeToggleIcon theme={theme} />
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
        {isAuthenticated && menuOpen && (
          <nav id="mobile-nav" className={MOBILE_NAV_CLASS} aria-label="Mobile">
            {items.map((item) => (
              <Link
                key={item.to}
                to={item.to}
                className={`block ${navClass(item.to)}`}
                onClick={closeMenu}
              >
                {item.label}
              </Link>
            ))}
          </nav>
        )}
      </div>
    </header>
  );
};

export default Header;
