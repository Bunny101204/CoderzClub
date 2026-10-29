export const DESKTOP_NAV_CLASS = "hidden md:flex items-center space-x-2";
export const MOBILE_MENU_BUTTON_CLASS = "md:hidden inline-flex items-center justify-center p-2 rounded-lg bg-gray-100 text-gray-900 hover:bg-gray-200 dark:bg-gray-700 dark:text-white dark:hover:bg-gray-600";
export const MOBILE_NAV_CLASS = "md:hidden border-t border-gray-200 dark:border-gray-700 px-2 py-2 space-y-1";

const USER_ROUTES = [
  { to: "/", label: "Problems" },
  { to: "/contests", label: "Contests" },
  { to: "/bundles", label: "Bundles" },
  { to: "/editor", label: "Editor" },
  { to: "/leaderboard", label: "Leaderboard" },
  { to: "/my/batches", label: "My batches" },
];

export function isAdminRole(role) {
  return role === "ADMIN" || role === "admin" || role === "Admin";
}

export function navigationItems({ isAuthenticated, role } = {}) {
  if (!isAuthenticated) {
    return [];
  }
  const items = USER_ROUTES.map((item) => ({ ...item }));
  if (isAdminRole(role)) {
    items.push({ to: "/admin", label: "Admin" });
  }
  return items;
}

export function reduceMobileMenu(open, event) {
  if (!event || event.type === "navigate" || event.type === "escape" || event.type === "route") {
    return false;
  }
  if (event.type === "toggle") {
    return !open;
  }
  return open;
}
