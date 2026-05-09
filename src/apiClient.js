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

// Response interceptor - handle auth errors and retries
apiClient.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error) => {
    const originalRequest = error.config;

    // Don't retry auth requests to avoid loops
    if (originalRequest.url?.includes('/api/login') ||
        originalRequest.url?.includes('/api/register') ||
        originalRequest.url?.includes('/api/validate-token')) {
      return Promise.reject(error);
    }

    // Handle 401 - token expired
    if (error.response?.status === 401 && !originalRequest._retry) {
      logger.warn('Token expired, clearing auth state');
      localStorage.removeItem('jwtToken');
      localStorage.removeItem('token');
      // Redirect to auth page
      window.location.href = '/auth';
      return Promise.reject(error);
    }

    // Retry logic for network errors and 5xx
    if (!originalRequest._retry &&
        (error.code === 'NETWORK_ERROR' ||
         error.code === 'ECONNABORTED' ||
         (error.response?.status >= 500 && error.response?.status < 600))) {

      originalRequest._retry = true;
      logger.warn(`Retrying request to ${originalRequest.url} (attempt ${originalRequest._retry})`);

      // Exponential backoff
      const delay = Math.min(1000 * Math.pow(2, originalRequest._retry - 1), 5000);
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
  // Auth endpoints
  auth: {
    login: (credentials) => apiClient.post('/api/login', credentials),
    register: (userData) => apiClient.post('/api/register', userData),
    validateToken: () => apiClient.post('/api/validate-token'),
    resendVerification: (data) => apiClient.post('/api/resend-verification', data),
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