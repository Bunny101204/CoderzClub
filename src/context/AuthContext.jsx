import React, { createContext, useContext, useState, useEffect } from "react";

const AuthContext = createContext();

export function AuthProvider({ children }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [user, setUser] = useState(null); // { username, role }
  const [loading, setLoading] = useState(true);

  // Helper to decode JWT without verifying signature (for offline fallback)
  const decodeJwt = (token) => {
    try {
      const [, payload] = token.split(".");
      const json = JSON.parse(atob(payload));
      const username = json.sub || json.username || json.user || null;
      const role = (json.role || "").toString().toUpperCase();
      if (username && role) return { username, role };
    } catch {}
    return null;
  };

  // Secure token validation function
  const validateToken = async (token) => {
    try {
      const response = await fetch("/api/validate-token", {
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
      });

      if (response.ok) {
        const userData = await response.json();
        setIsAuthenticated(true);
        setUser({
          username: userData.username,
          role: userData.role.toString().toUpperCase(),
        });
        return true;
      } else {
        // Only clear token for explicit auth failures
        if (response.status === 401 || response.status === 403) {
          localStorage.removeItem("jwtToken");
          setIsAuthenticated(false);
          setUser(null);
          return false;
        }
        // For other errors, try local decode fallback
        const decoded = decodeJwt(token);
        if (decoded) {
          setIsAuthenticated(true);
          setUser(decoded);
          return true;
        }
        return false;
      }
    } catch (error) {
      // Network/server unavailable → fallback to local decode, do NOT clear token
      const decoded = decodeJwt(token);
      if (decoded) {
        setIsAuthenticated(true);
        setUser(decoded);
        return true;
      }
      return false;
    }
  };

  useEffect(() => {
    // Check for JWT token and validate it with server
    const token = localStorage.getItem("jwtToken");
    if (token) {
      validateToken(token).finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  // Real login function
  const login = async (username, password) => {
    try {
      console.log("[AuthContext] Attempting login", username);
      const response = await fetch("/api/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });

      if (!response.ok) {
        localStorage.removeItem("jwtToken");
        setIsAuthenticated(false);
        setUser(null);
        let errorText = await response.text();
        if (errorText.startsWith("<!DOCTYPE")) errorText = "Server error or forbidden (HTML received)";
        return { success: false, error: errorText };
      }

      let data;
      try {
        data = await response.json();
      } catch (e) {
        localStorage.removeItem("jwtToken");
        setIsAuthenticated(false);
        setUser(null);
        return { success: false, error: "Invalid JSON response from server" };
      }

      if (data.token) {
        localStorage.setItem("jwtToken", data.token);
        setIsAuthenticated(true);
        const normalizedRole = (data.role || "").toString().toUpperCase();
        const userObj = { username, role: normalizedRole };
        setUser(userObj);
        return { success: true, token: data.token, role: normalizedRole };
      } else {
        localStorage.removeItem("jwtToken");
        setIsAuthenticated(false);
        setUser(null);
        return { success: false, error: "No token received" };
      }
    } catch (err) {
      localStorage.removeItem("jwtToken");
      setIsAuthenticated(false);
      setUser(null);
      return { success: false, error: err.message };
    }
  };

  // Real register function
  const register = async (username, email, password, role) => {
    try {
      console.log("[AuthContext] Attempting register", username, email);
      const body = { username, email, password };
      if (role) body.role = role;

      const response = await fetch("/api/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });

      if (!response.ok) {
        localStorage.removeItem("jwtToken");
        setIsAuthenticated(false);
        setUser(null);
        let errorText = await response.text();
        if (errorText.startsWith("<!DOCTYPE")) errorText = "Server error or forbidden (HTML received)";
        return { success: false, error: errorText };
      }

      let data;
      try {
        data = await response.json();
      } catch (e) {
        localStorage.removeItem("jwtToken");
        setIsAuthenticated(false);
        setUser(null);
        return { success: false, error: "Invalid JSON response from server" };
      }

      if (data.token) {
        localStorage.setItem("jwtToken", data.token);
        setIsAuthenticated(true);
        const normalizedRole = (data.role || "").toString().toUpperCase();
        const userObj = { username, role: normalizedRole };
        setUser(userObj);
        return { success: true, token: data.token, role: normalizedRole };
      } else {
        localStorage.removeItem("jwtToken");
        setIsAuthenticated(false);
        setUser(null);
        return { success: false, error: "No token received" };
      }
    } catch (err) {
      localStorage.removeItem("jwtToken");
      setIsAuthenticated(false);
      setUser(null);
      return { success: false, error: err.message };
    }
  };

  const logout = () => {
    setIsAuthenticated(false);
    setUser(null);
    localStorage.removeItem("jwtToken");
  };

  return (
    <AuthContext.Provider value={{ isAuthenticated, user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
