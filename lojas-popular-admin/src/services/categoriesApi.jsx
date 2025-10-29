import { api } from "./api";

// ✅ API padronizada e completa
export const categoriesApi = {
  list: () => api.get("/categorias").then(r => r.data),
  byId: (id) => api.get(`/categorias/${id}`).then(r => r.data),
  create: (payload) => api.post("/categorias", payload).then(r => r.data),
  update: (id, payload) => api.put(`/categororias/${id}`, payload).then(r => r.data),
  remove: (id) => api.delete(`/categorias/${id}`).then(r => r.data),
};
