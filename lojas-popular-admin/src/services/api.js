// src/services/api.js
import axios from "axios";
import { API_BASE } from "../utils/url";

// API_BASE ja inclui o prefixo /api/v1 (ver src/utils/url.js)
export const api = axios.create({
  baseURL: API_BASE,
});

// Anexa token se existir
api.interceptors.request.use((config) => {
  const tk = localStorage.getItem("lp_token");
  if (tk) config.headers.Authorization = `Bearer ${tk}`;
  return config;
});

// Redireciona para login em caso de token expirado/inválido
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    // 401 = token expirado ou ausente — limpa sessão e redireciona para login
    if (status === 401 && window.location.pathname !== "/login") {
      localStorage.removeItem("lp_token");
      localStorage.removeItem("lp_refresh");
      localStorage.removeItem("lp_user");
      window.location.href = "/login";
    }
    return Promise.reject(error);
  }
);
