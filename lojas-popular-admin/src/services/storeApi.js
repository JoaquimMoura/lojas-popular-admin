// src/services/storeApi.js
import { api } from "./api";

export const storeApi = {
  list: () => api.get("/produtos").then((r) => r.data),
  get: (id) => api.get(`/produtos/${id}`).then((r) => r.data),
};
