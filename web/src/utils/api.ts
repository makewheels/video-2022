import axios from 'axios';
import { getToken, removeToken } from './auth';

const api = axios.create();

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers['token'] = token;
  }
  return config;
});

api.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new Error(body.message || 'Request failed'));
    }
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      removeToken();
      const target = encodeURIComponent(window.location.pathname + window.location.search);
      window.location.href = `/login?target=${target}`;
    }
    return Promise.reject(error);
  },
);

export default api;
