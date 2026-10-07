// src/services/posVendaApi.js
import { api } from "./api";

const MULTIPART = { headers: { "Content-Type": "multipart/form-data" } };

export const posVendaApi = {
  listar: ({ status, tipo } = {}) =>
    api
      .get("/ocorrencias", { params: { ...(status ? { status } : {}), ...(tipo ? { tipo } : {}) } })
      .then((r) => r.data),
  obter: (id) => api.get(`/ocorrencias/${id}`).then((r) => r.data),
  anexarEvidencia: (id, formData) => api.post(`/ocorrencias/${id}/evidencias`, formData, MULTIPART).then((r) => r.data),
  receberDevolucao: (id, body, chave) =>
    api
      .post(`/ocorrencias/${id}/receber-devolucao`, body, { headers: { "Idempotency-Key": chave } })
      .then((r) => r.data),
  resolver: (id, body) => api.post(`/ocorrencias/${id}/resolver`, body).then((r) => r.data),
  cancelar: (id, body) => api.post(`/ocorrencias/${id}/cancelar`, body).then((r) => r.data),
};
