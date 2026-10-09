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

  vincularCliente: (id, body) => api.post(`/vendas/${id}/vincular-cliente`, body).then((r) => r.data),
  trocarCliente: (id, body) => api.post(`/vendas/${id}/trocar-cliente`, body).then((r) => r.data),

  // Etapa 2: entrega, montagem e pós-venda (as respostas são o detalhe da venda)
  agendarEntrega: (id, body) => api.post(`/vendas/${id}/entrega/agendar`, body).then((r) => r.data),
  reagendarEntrega: (id, body) => api.post(`/vendas/${id}/entrega/reagendar`, body).then((r) => r.data),
  registrarSaida: (id, chave) =>
    api.post(`/vendas/${id}/saida`, null, { headers: { "Idempotency-Key": chave } }).then((r) => r.data),
  tentativaFrustrada: (id, body) =>
    api.post(`/vendas/${id}/entrega/tentativa-frustrada`, body).then((r) => r.data),
  concluirEntrega: (id, formData) =>
    api
      .post(`/vendas/${id}/entrega/concluir`, formData, { headers: { "Content-Type": "multipart/form-data" } })
      .then((r) => r.data),
  agendarMontagem: (id, body) => api.post(`/vendas/${id}/montagem/agendar`, body).then((r) => r.data),
  concluirMontagem: (id, formData) =>
    api
      .post(`/vendas/${id}/montagem/concluir`, formData, { headers: { "Content-Type": "multipart/form-data" } })
      .then((r) => r.data),
  montagemNaoNecessaria: (id, body) => api.post(`/vendas/${id}/montagem/nao-necessaria`, body).then((r) => r.data),
  abrirOcorrencia: (id, body) => api.post(`/vendas/${id}/ocorrencias`, body).then((r) => r.data),
};
