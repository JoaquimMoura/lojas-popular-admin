// src/services/vendasApi.js
import { api } from "./api";

export const vendasApi = {
  configuracao: () => api.get("/vendas/configuracao").then((r) => r.data),
  listar: (params) => api.get("/vendas", { params }).then((r) => r.data),
  obter: (id) => api.get(`/vendas/${id}`).then((r) => r.data),
  registrar: (payload) => api.post("/vendas", payload).then((r) => r.data),
  atualizar: (id, payload) => api.put(`/vendas/${id}`, payload).then((r) => r.data),
  recalcular: (id) => api.post(`/vendas/${id}/recalcular`).then((r) => r.data),
  confirmar: (id, chave) =>
    api
      .post(`/vendas/${id}/confirmar`, null, { headers: { "Idempotency-Key": chave } })
      .then((r) => r.data),
  cancelar: (id, motivo) => api.post(`/vendas/${id}/cancelar`, { motivo }).then((r) => r.data),
  aprovarDesconto: (id, motivo) =>
    api.post(`/vendas/${id}/desconto/aprovar`, { motivo: motivo || null }).then((r) => r.data),
  rejeitarDesconto: (id, motivo) =>
    api.post(`/vendas/${id}/desconto/rejeitar`, { motivo: motivo || null }).then((r) => r.data),
};
