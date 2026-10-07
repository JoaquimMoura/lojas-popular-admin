// src/services/estoqueApi.js
import { api } from "./api";

export const estoqueApi = {
  saldos: (params) => api.get("/estoque", { params }).then((r) => r.data),
};
