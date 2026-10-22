export function themeTogglePresentation(theme) {
  const next = theme === "dark" ? "light" : "dark";
  return {
    next,
    icon: theme === "dark" ? "sun" : "moon",
    label: theme === "dark" ? "Switch to light theme" : "Switch to dark theme"
  };
}
