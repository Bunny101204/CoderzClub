import apiClient from '../apiClient';

// Centralized auth token management
export const auth = {
  // Get the auth token (standardized to jwtToken)
  getToken: () => {
    return localStorage.getItem('jwtToken') || localStorage.getItem('token');
  },

  // Set the auth token
  setToken: (token) => {
    localStorage.setItem('jwtToken', token);
    // Remove old token key if it exists
    localStorage.removeItem('token');
  },

  // Remove the auth token
  removeToken: () => {
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('token');
  },

  // Create axios config with auth header
  getAuthConfig: () => {
    const token = auth.getToken();
    return token ? {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    } : {};
  },

  // Make authenticated request
  authenticatedRequest: (method, url, data = null) => {
    const config = auth.getAuthConfig();
    config.method = method;
    config.url = url;
    if (data) config.data = data;
    return apiClient(config);
  }
};

export default auth;