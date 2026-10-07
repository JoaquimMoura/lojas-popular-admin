// src/services/encomendasApi.js
import { api } from "./api";

export const encomendasApi = {
  listar: (status) => api.get("/encomendas", { params: status ? { status } : {} }).then((r) => r.data),
  atualizar: (id, body) => api.put(`/encomendas/${id}`, body).then((r) => r.data),
  receber: (id, quantidade, chave) =>
    api
      .post(`/encomendas/${id}/receber`, { quantidade }, { headers: { "Idempotency-Key": chave } })
      .then((r) => r.data),
};
