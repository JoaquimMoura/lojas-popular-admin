// src/services/storeConfigApi.js
import { api } from "./api";

export const storeConfigApi = {
  get: () => api.get("/config/loja").then((r) => r.data),
};
