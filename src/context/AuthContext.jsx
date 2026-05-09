import React, { createContext, useContext, useState, useEffect } from "react";
import { api, logger } from "../apiClient";

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
      const response = await api.auth.validateToken();
      const userData = response.data;
      setIsAuthenticated(true);
      setUser({
        username: userData.username,
        role: userData.role.toString().toUpperCase(),
      });
      return true;
    } catch (error) {
      // Only clear token for explicit auth failures
      if (error.response?.status === 401 || error.response?.status === 403) {
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
      logger.info("Attempting login", username);
      const response = await api.auth.login({ username, password });

      let data = response.data;
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
      const errorText = err.response?.data?.error || err.response?.data?.message || err.message || "Login failed";
      return { success: false, error: errorText };
    }
  };

  // Real register function
  const register = async (username, email, password, role) => {
    try {
      logger.info("Attempting register", username, email);
      const body = { username, email, password };
      if (role) body.role = role;

      const response = await api.auth.register(body);

      let data = response.data;
      if (data.token) {
        localStorage.setItem("jwtToken", data.token);
        setIsAuthenticated(true);
        const normalizedRole = (data.role || "").toString().toUpperCase();
        const userObj = { username, role: normalizedRole };
        setUser(userObj);
        return { success: true, token: data.token, role: normalizedRole };
      } else if (data.message) {
        return { success: true, message: data.message, emailSent: data.emailSent ?? true };
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
      const errorText = err.response?.data?.error || err.response?.data?.message || err.message || "Registration failed";
      return { success: false, error: errorText };
    }
  };

  const resendVerification = async (usernameOrEmail) => {
    try {
      logger.info("Attempting resend verification", usernameOrEmail);
      const response = await api.auth.resendVerification({ usernameOrEmail });

      let data = response.data;
      return { success: true, message: data.message || "Verification email resent.", emailSent: data.emailSent ?? true };
    } catch (err) {
      const errorText = err.response?.data?.error || err.response?.data?.message || err.message || "Resend verification failed";
      return { success: false, error: errorText };
    }
  };

  const logout = () => {
    setIsAuthenticated(false);
    setUser(null);
    localStorage.removeItem("jwtToken");
  };

  return (
    <AuthContext.Provider value={{ isAuthenticated, user, loading, login, register, resendVerification, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
