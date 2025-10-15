import axios from "axios";

const baseURL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export const http = axios.create({
  baseURL,
  timeout: 20000,
});

export const setAuthHeader = (token: string | null) => {
  if (token) {
    http.defaults.headers.common.Authorization = `Bearer ${token}`;
  } else {
    delete http.defaults.headers.common.Authorization;
  }
};

type AuthStoreAccessor = () => { clearSession: () => void };

let interceptorAttached = false;

export const attachAuthInterceptor = (getAuthStore: AuthStoreAccessor) => {
  if (interceptorAttached) {
    return;
  }
  http.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error.response?.status === 401) {
        try {
          const store = getAuthStore();
          store?.clearSession();
        } catch (err) {
          console.warn("Failed to clear auth session after 401", err);
        }
      }
      return Promise.reject(error);
    }
  );
  interceptorAttached = true;
};
