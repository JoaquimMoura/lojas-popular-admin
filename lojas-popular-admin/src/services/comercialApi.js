// src/services/comercialApi.js
import { api } from "./api";

export const comercialApi = {
  obter: () => api.get("/config/comercial").then((r) => r.data),
  atualizar: (payload) => api.put("/config/comercial", payload).then((r) => r.data),
  atualizarFinanceiro: (payload) => api.put("/config/comercial/financeiro", payload).then((r) => r.data),
  obterClientes: () => api.get("/config/comercial/clientes").then((r) => r.data),
  atualizarClientes: (payload) => api.put("/config/comercial/clientes", payload).then((r) => r.data),
  criarCondicao: (payload) => api.post("/config/comercial/condicoes", payload).then((r) => r.data),
  atualizarCondicao: (id, payload) =>
    api.put(`/config/comercial/condicoes/${id}`, payload).then((r) => r.data),
};
