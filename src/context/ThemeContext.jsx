import React, { createContext, useContext, useEffect, useMemo, useState } from "react";
import {
  applyThemeClass,
  normalizeTheme,
  persistTheme,
  readStoredTheme
} from "../theme/theme.js";

const ThemeContext = createContext({
  theme: "dark",
  setTheme: () => {},
  toggleTheme: () => {}
});

export function ThemeProvider({ children }) {
  const [theme, setThemeState] = useState(() =>
    readStoredTheme(typeof localStorage === "undefined" ? null : localStorage)
  );

  useEffect(() => {
    applyThemeClass(theme, document.documentElement);
    persistTheme(theme, localStorage);
  }, [theme]);

  const value = useMemo(() => ({
    theme,
    setTheme: (next) => setThemeState(normalizeTheme(next)),
    toggleTheme: () => setThemeState((current) => (current === "light" ? "dark" : "light"))
  }), [theme]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  return useContext(ThemeContext);
}
