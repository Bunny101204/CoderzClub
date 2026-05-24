import axios from 'axios';

// Environment configuration
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
const IS_PRODUCTION = import.meta.env.PROD;
const IS_DEVELOPMENT = import.meta.env.DEV;

// Environment-gated logger
export const logger = {
  debug: (message, ...args) => {
    if (IS_DEVELOPMENT) {
      console.debug(`[DEBUG] ${message}`, ...args);
    }
  },
  info: (message, ...args) => {
    if (IS_DEVELOPMENT) {
      console.info(`[INFO] ${message}`, ...args);
    }
  },
  warn: (message, ...args) => {
    console.warn(`[WARN] ${message}`, ...args);
  },
  error: (message, ...args) => {
    console.error(`[ERROR] ${message}`, ...args);
  }
};

// Auth token management
const getAuthToken = () => {
  return localStorage.getItem('jwtToken') || localStorage.getItem('token');
};

// Create axios instance with interceptors
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Separate instance for auth operations with shorter timeout
const authClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000, // Auth ops should be fast
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor - add auth headers
apiClient.interceptors.request.use(
  (config) => {
    const token = getAuthToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    logger.error('Request interceptor error:', error);
    return Promise.reject(error);
  }
);

// Auth client request interceptor
authClient.interceptors.request.use(
  (config) => {
    const token = getAuthToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    logger.error('Auth request interceptor error:', error);
    return Promise.reject(error);
  }
);

// Response interceptor for auth operations - minimal error handling
authClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // For auth operations, just log and reject - no redirects or complex retry
    if (error.response?.status === 401) {
      localStorage.removeItem('jwtToken');
      localStorage.removeItem('token');
    }
    logger.error('Auth request failed:', {
      url: error.config?.url,
      method: error.config?.method,
      status: error.response?.status,
      message: error.message
    });
    return Promise.reject(error);
  }
);


apiClient.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error) => {
    const originalRequest = error.config;
    const isAuthEndpoint = originalRequest.url?.includes('/api/login') ||
                          originalRequest.url?.includes('/api/register') ||
                          originalRequest.url?.includes('/api/validate-token') ||
                          originalRequest.url?.includes('/api/confirm-email') ||
                          originalRequest.url?.includes('/api/resend-verification');

    // For auth endpoints: retry on network errors ONCE, then fail
    if (isAuthEndpoint && !originalRequest._retry &&
        (error.code === 'ECONNABORTED' || 
         error.code === 'ERR_NETWORK' ||
         error.message === 'Network Error')) {
      
      originalRequest._retry = true;
      logger.warn(`Retrying auth request to ${originalRequest.url}`);
      
      // Short delay for auth retry
      await new Promise(resolve => setTimeout(resolve, 500));
      return apiClient(originalRequest);
    }

    // Handle 401 - token expired (but NOT on auth page or during active auth attempt)
    if (error.response?.status === 401 && !originalRequest._retry && !isAuthEndpoint) {
      const currentPath = window.location.pathname;
      
      // Only redirect if user is NOT already on auth page or trying to auth
      if (currentPath !== '/auth' && currentPath !== '/login') {
        logger.warn('Token expired, clearing auth state and redirecting');
        localStorage.removeItem('jwtToken');
        localStorage.removeItem('token');
        window.location.href = '/auth?reason=session_expired';
      }
      return Promise.reject(error);
    }

    // Retry logic for network errors and 5xx (but not on auth endpoints)
    if (!isAuthEndpoint &&
        !originalRequest._retry &&
        (error.code === 'ECONNABORTED' || 
         error.code === 'ERR_NETWORK' ||
         error.message === 'Network Error' ||
         (error.response?.status >= 500 && error.response?.status < 600))) {

      originalRequest._retry = true;
      logger.warn(`Retrying request to ${originalRequest.url}`);

      // Exponential backoff
      const delay = Math.min(1000 * Math.pow(2, (originalRequest._retry || 1) - 1), 5000);
      await new Promise(resolve => setTimeout(resolve, delay));

      return apiClient(originalRequest);
    }

    logger.error('API request failed:', {
      url: originalRequest.url,
      method: originalRequest.method,
      status: error.response?.status,
      message: error.message
    });

    return Promise.reject(error);
  }
);

// API methods
export const api = {
  // Auth endpoints - use separate client with shorter timeout
  auth: {
    login: (credentials) => authClient.post('/api/login', credentials),
    register: (userData) => authClient.post('/api/register', userData),
    validateToken: () => authClient.post('/api/validate-token'),
    resendVerification: (data) => authClient.post('/api/resend-verification', data),
    confirmEmail: (token) => authClient.post('/api/confirm-email', { token }),
  },

  // Problems
  problems: {
    getAll: () => apiClient.get('/api/problems'),
    getById: (id) => apiClient.get(`/api/problems/${id}`),
    create: (problem) => apiClient.post('/api/problems', problem),
    update: (id, problem) => apiClient.put(`/api/problems/${id}`, problem),
    delete: (id) => apiClient.delete(`/api/problems/${id}`),
  },

  // Bundles
  bundles: {
    getAll: () => apiClient.get('/api/bundles'),
    getById: (id) => apiClient.get(`/api/bundles/${id}`),
    create: (bundle) => apiClient.post('/api/bundles', bundle),
    update: (id, bundle) => apiClient.put(`/api/bundles/${id}`, bundle),
    delete: (id) => apiClient.delete(`/api/bundles/${id}`),
  },

  // Submissions
  submissions: {
    create: (submission) => apiClient.post('/api/submissions', submission),
    getMySubmissions: (params) => apiClient.get('/api/submissions/my-submissions', { params }),
    getLimits: (problemId) => apiClient.get('/api/submissions/limits', { params: { problemId } }),
    getProblemSubmissions: (problemId, params) => apiClient.get(`/api/submissions/problem/${problemId}`, { params }),
  },

  // Submission Jobs (async)
  submissionJobs: {
    create: (job) => apiClient.post('/api/submission-jobs', job),
    getStatus: (jobId) => apiClient.get(`/api/submission-jobs/${jobId}`),
    getMyJobs: (params) => apiClient.get('/api/submission-jobs/my-jobs', { params }),
    getQueueStats: () => apiClient.get('/api/submission-jobs/queue/stats'),
  },

  // Users
  users: {
    getStats: () => apiClient.get('/api/users/stats'),
    getLeaderboard: () => apiClient.get('/api/users/leaderboard'),
  },

  // Judge0 execution
  judge0: {
    execute: (payload) => apiClient.post('/api/judge0/execute', payload),
  },
};

export default apiClient;