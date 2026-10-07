// src/services/estoqueApi.js
import { api } from "./api";

export const estoqueApi = {
  saldos: (params) => api.get("/estoque", { params }).then((r) => r.data),
  movimentacoes: ({ produtoId, variacaoId, pedidoId, pagina = 0, tamanho = 30 } = {}) =>
    api
      .get("/estoque/movimentacoes", {
        params: {
          ...(produtoId ? { produtoId } : {}),
          ...(variacaoId ? { variacaoId } : {}),
          ...(pedidoId ? { pedidoId } : {}),
          pagina,
          tamanho,
        },
      })
      .then((r) => r.data),
  ajustar: ({ produtoId, variacaoId, contado, motivo }, chave) =>
    api
      .post(
        "/estoque/ajustes",
        { produtoId, variacaoId: variacaoId ?? null, contado, motivo },
        { headers: { "Idempotency-Key": chave } },
      )
      .then((r) => r.data),
};
