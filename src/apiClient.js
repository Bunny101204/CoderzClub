import axios from 'axios';
import { htmlApiPayloadError, prefixApiPath, resolveApiBaseUrl } from './apiBase.js';
import { shouldRetryTransientFailure } from './apiRetry.js';

// Development uses the Vite `/api` proxy. Production uses the HTTPS API origin
// inlined from VITE_API_BASE_URL at build time.
// Call api.*, apiUrl(), or apiFetch(). Do not patch global fetch: production
// minifiers can delete that side effect, and relative /api then hits the SPA.
const IS_DEVELOPMENT = import.meta.env.DEV;
const API_BASE_URL = resolveApiBaseUrl({
  dev: IS_DEVELOPMENT,
  raw: import.meta.env.VITE_API_BASE_URL,
});

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
export function getAuthToken() {
  return localStorage.getItem('jwtToken') || localStorage.getItem('token');
}

export function getApiBaseUrl() {
  return API_BASE_URL;
}

export function apiUrl(path) {
  return prefixApiPath(path, API_BASE_URL);
}

function acceptApiResponse(response) {
  const contentType = response?.headers?.['content-type'] || response?.headers?.get?.('content-type') || '';
  const htmlError = htmlApiPayloadError(response?.data, contentType);
  if (htmlError) {
    return Promise.reject(htmlError);
  }
  return response;
}

export async function apiFetch(path, init) {
  const response = await fetch(apiUrl(path), init);
  const contentType = response.headers.get('content-type') || '';
  const headerError = htmlApiPayloadError(null, contentType);
  if (headerError) {
    throw headerError;
  }
  if (!contentType || /text\/plain/i.test(contentType)) {
    const head = (await response.clone().text()).slice(0, 64);
    const bodyError = htmlApiPayloadError(head, contentType);
    if (bodyError) {
      throw bodyError;
    }
  }
  return response;
}

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
    if (typeof window !== 'undefined') {
      config.headers['X-Client-Origin'] = window.location.origin;
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
  (response) => acceptApiResponse(response),
  (error) => {
    // For auth operations, just log and reject - no redirects or complex retry
    if (error.response?.status === 401) {
      localStorage.removeItem('jwtToken');
      localStorage.removeItem('token');
      logger.info('Session is no longer valid.');
    } else {
      logger.error('Auth request failed:', {
        url: error.config?.url,
        method: error.config?.method,
        status: error.response?.status,
        message: error.message
      });
    }
    return Promise.reject(error);
  }
);


apiClient.interceptors.response.use(
  (response) => acceptApiResponse(response),
  async (error) => {
    const originalRequest = error.config;
    const isAuthEndpoint = originalRequest.url?.includes('/api/login') ||
                          originalRequest.url?.includes('/api/register') ||
                          originalRequest.url?.includes('/api/validate-token') ||
                          originalRequest.url?.includes('/api/confirm-email') ||
                          originalRequest.url?.includes('/api/resend-verification');

    // Retry only safe, idempotent reads. Writes such as contest creation,
    // submissions, contest runs, admin mutations, and draft saves are not repeated.
    if (originalRequest && shouldRetryTransientFailure({
      method: originalRequest.method,
      status: error.response?.status,
      code: error.code,
      message: error.message,
      alreadyRetried: Boolean(originalRequest._retry),
    })) {
      originalRequest._retry = true;
      logger.warn(`Retrying request to ${originalRequest.url}`);
      const delay = isAuthEndpoint ? 500 : 1000;
      await new Promise(resolve => setTimeout(resolve, delay));
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
    confirmEmail: (token) => authClient.get('/api/confirm-email', { params: { token } }),
  },

  // Problems
  problems: {
    getAll: (params) => apiClient.get('/api/problems', { params }),
    getById: (id) => apiClient.get(`/api/problems/${id}`),
    getByIds: (ids) => {
      const params = new URLSearchParams();
      (ids || []).forEach((id) => {
        if (id) params.append('ids', id);
      });
      return apiClient.get('/api/problems/by-ids', { params });
    },
    getAdminById: (id) => apiClient.get(`/api/problems/${id}/admin`),
    runPublic: (id, body) => apiClient.post(`/api/problems/${id}/run-public`, body),
    create: (problem) => apiClient.post('/api/problems', problem),
    updateContent: (id, problem) => apiClient.patch(`/api/problems/${id}/content`, problem),
    updateExecution: (id, config) => apiClient.patch(`/api/problems/${id}/execution`, config),
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
    getMyProgress: () => apiClient.get('/api/submissions/my-progress'),
    getLimits: (problemId) => apiClient.get('/api/submissions/limits', { params: { problemId } }),
    getProblemSubmissions: (problemId, params) => apiClient.get(`/api/submissions/problem/${problemId}`, { params }),
  },

  // Submission Jobs (async)
  submissionJobs: {
    create: (job, operationId) => apiClient.post(
      "/api/submission-jobs",
      job,
      operationId ? { headers: { "Idempotency-Key": operationId } } : undefined
    ),
    getStatus: (jobId, config) => apiClient.get(`/api/submission-jobs/${jobId}`, config),
    createEventTicket: (jobId) => apiClient.post(`/api/submission-jobs/${jobId}/events/ticket`, {}),
    getMyJobs: (params) => apiClient.get('/api/submission-jobs/my-jobs', { params }),
    getQueueStats: () => apiClient.get('/api/submission-jobs/queue/stats'),
  },

  // Users
  users: {
    getStats: () => apiClient.get('/api/users/stats'),
    getLeaderboard: (params) => apiClient.get('/api/users/leaderboard', { params }),
    deleteMe: (confirmation) => apiClient.delete('/api/users/me', { data: { confirmation } }),
    exportData: () => apiClient.get('/api/users/me/export'),
  },

  // Judge0 execution
  judge0: {
    execute: (payload) => apiClient.post('/api/judge0/execute', payload),
  },

  adminBatches: {
    list: (params) => apiClient.get('/api/admin/batches', { params }),
    get: (id) => apiClient.get(`/api/admin/batches/${id}`),
  },

  adminContests: {
    list: (params) => apiClient.get('/api/admin/contests', { params }),
    get: (id) => apiClient.get(`/api/admin/contests/${id}`),
    create: (body) => apiClient.post('/api/admin/contests', body),
    update: (id, body) => apiClient.patch(`/api/admin/contests/${id}`, body),
    publish: (id) => apiClient.post(`/api/admin/contests/${id}/publish`),
    unpublish: (id) => apiClient.post(`/api/admin/contests/${id}/unpublish`),
    delete: (id) => apiClient.delete(`/api/admin/contests/${id}`),
  },

  contests: {
    list: (params) => apiClient.get('/api/contests', { params }),
    get: (id) => apiClient.get(`/api/contests/${id}`),
    getProblem: (contestId, problemId) => apiClient.get(
      `/api/contests/${contestId}/problems/${problemId}`
    ),
    getLeaderboard: (contestId, params) => apiClient.get(
      `/api/contests/${contestId}/leaderboard`,
      { params }
    ),
    submit: (contestId, problemId, payload, operationId) => apiClient.post(
      `/api/contests/${contestId}/problems/${problemId}/submission-jobs`,
      payload,
      operationId ? { headers: { "Idempotency-Key": operationId } } : undefined
    ),
    run: (contestId, problemId, payload) => apiClient.post(
      `/api/contests/${contestId}/problems/${problemId}/runs`,
      payload
    ),
    getRun: (runId) => apiClient.get(`/api/contest-runs/${runId}`),
  },

  drafts: {
    getPractice: (problemId, languageId) => apiClient.get(
      `/api/problems/${encodeURIComponent(problemId)}/drafts/${encodeURIComponent(languageId)}`
    ),
    putPractice: (problemId, languageId, body) => apiClient.put(
      `/api/problems/${encodeURIComponent(problemId)}/drafts/${encodeURIComponent(languageId)}`,
      body
    ),
    getContest: (contestId, problemId, languageId) => apiClient.get(
      `/api/contests/${encodeURIComponent(contestId)}/problems/${encodeURIComponent(problemId)}/drafts/${encodeURIComponent(languageId)}`
    ),
    putContest: (contestId, problemId, languageId, body) => apiClient.put(
      `/api/contests/${encodeURIComponent(contestId)}/problems/${encodeURIComponent(problemId)}/drafts/${encodeURIComponent(languageId)}`,
      body
    ),
  },
};

function isSafeBusinessMessage(value) {
  if (typeof value !== "string") {
    return false;
  }
  const text = value.trim();
  if (!text) {
    return false;
  }
  if (text.includes("\n") || text.includes("\t") || text.length > 300) {
    return false;
  }
  if (/\bjava\.|Exception\b|\bat com\./.test(text)) {
    return false;
  }
  return true;
}

export function apiErrorMessage(error, fallback = "Request failed") {
  const data = error?.response?.data;
  if (isSafeBusinessMessage(data)) {
    return data.trim();
  }
  if (isSafeBusinessMessage(data?.error)) {
    return data.error.trim();
  }
  if (isSafeBusinessMessage(data?.message)) {
    return data.message.trim();
  }
  if (!error?.response && isSafeBusinessMessage(error?.message) && !/^request failed with status code/i.test(error.message)) {
    return error.message.trim();
  }
  return fallback;
}

export default apiClient;