// src/services/clientesApi.js
import { api } from "./api";

export const clientesApi = {
  buscar: (q) => api.get("/clientes", { params: { q } }).then((r) => r.data),
  duplicidades: (params) => api.get("/clientes/duplicidades", { params }).then((r) => r.data),
  obter: (id) => api.get(`/clientes/${id}`).then((r) => r.data),
  criar: (payload) => api.post("/clientes", payload).then((r) => r.data),
  atualizar: (id, payload) => api.put(`/clientes/${id}`, payload).then((r) => r.data),
  desativar: (id) => api.post(`/clientes/${id}/desativar`).then((r) => r.data),
};
