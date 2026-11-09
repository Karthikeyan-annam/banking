import axios from 'axios';

// Base Axios instance.
// When using the Vite dev proxy, requests to /api are proxied to http://localhost:8080.
// Alternatively VITE_API_BASE_URL can point directly to backend.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
});

// Request interceptor: attach Authorization Bearer token from localStorage
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('access_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: handle 401 Unauthorized and Network / Connection drops (ECONNREFUSED)
api.interceptors.response.use(
  (response) => response,
  (error) => {
    // 1. Detect network errors, timeouts, or connection refused (ECONNREFUSED)
    if (!error.response || error.code === 'ERR_NETWORK' || error.code === 'ECONNREFUSED' || error.message?.includes('Network Error')) {
      const friendlyMessage =
        'Unable to connect to the banking server. Please ensure the backend is running on port 8080.';

      // Enrich error object with a normalized user-facing message
      error.isNetworkError = true;
      error.userFriendlyMessage = friendlyMessage;

      if (!error.response) {
        error.response = {
          data: {
            message: friendlyMessage,
            error: 'Connection Refused',
            status: 503,
          },
        };
      } else if (error.response.data) {
        error.response.data.message = friendlyMessage;
      }
    }

    // 2. Handle 401 Unauthorized (session expired or invalid)
    if (error.response && error.response.status === 401) {
      const isAuthPath =
        window.location.pathname.startsWith('/login') ||
        window.location.pathname.startsWith('/register');

      if (!isAuthPath) {
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('auth_user');
        window.location.href = '/login?expired=true';
      }
    }

    return Promise.reject(error);
  }
);

export default api;
