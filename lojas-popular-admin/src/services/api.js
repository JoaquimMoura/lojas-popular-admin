// src/services/api.js
import axios from "axios";
import { API_BASE } from "../utils/url";

export const api = axios.create({
  baseURL: `${API_BASE}/api/v1`,
});

// Anexa token se existir
api.interceptors.request.use((config) => {
  const tk = localStorage.getItem("lp_token");
  if (tk) config.headers.Authorization = `Bearer ${tk}`;

  // prevenção de duplicar /api/v1 no caminho
  if (config.url?.startsWith("/api/v1")) {
    config.url = config.url.replace("/api/v1", "");
  }
  return config;
});
