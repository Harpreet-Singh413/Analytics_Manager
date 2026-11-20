import axios from 'axios';

const baseURL = import.meta.env.VITE_API_BASE_URL || '';

export const apiClient = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
});

// Interceptor to normalize errors
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // If backend returns RFC 7807 problem detail
    const problem = error.response?.data;
    if (problem) {
      error.problemDetail = problem;
    }
    return Promise.reject(error);
  }
);

// Ingestion APIs
export const ingestEvent = async (eventData) => {
  const response = await apiClient.post('/api/events', eventData);
  return {
    data: response.data,
    status: response.status,
    location: response.headers['location'],
  };
};

export const ingestBatchEvents = async (events) => {
  const response = await apiClient.post('/api/events/batch', { events });
  return {
    data: response.data,
    status: response.status,
  };
};

// Event Retrieval APIs
export const getEvents = async (params = {}) => {
  const response = await apiClient.get('/api/events', { params });
  return response.data;
};

export const getEventById = async (eventId) => {
  const response = await apiClient.get(`/api/events/${encodeURIComponent(eventId)}`);
  return response.data;
};

export const getFailedEvents = async () => {
  const response = await apiClient.get('/api/events/failed');
  return response.data;
};

// Analytics APIs
export const getAnalyticsSummary = async () => {
  const response = await apiClient.get('/api/analytics/summary');
  return response.data;
};

export const getEventsByType = async () => {
  const response = await apiClient.get('/api/analytics/events-by-type');
  return response.data;
};

export const getEventsOverTime = async () => {
  const response = await apiClient.get('/api/analytics/events-over-time');
  return response.data;
};

export const getActiveClients = async () => {
  const response = await apiClient.get('/api/analytics/clients');
  return response.data;
};

export const getTopPages = async () => {
  const response = await apiClient.get('/api/analytics/top-pages');
  return response.data;
};

// Actuator Health API
export const getHealth = async () => {
  try {
    const response = await apiClient.get('/actuator/health');
    return response.data;
  } catch (err) {
    if (err.response?.data) {
      return err.response.data;
    }
    return { status: 'DOWN', error: err.message };
  }
};
