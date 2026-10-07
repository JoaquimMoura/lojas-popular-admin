// src/services/agendaApi.js
import { api } from "./api";

export const agendaApi = {
  // de/ate no formato yyyy-MM-dd
  listar: (de, ate) => api.get("/agenda", { params: { de, ate } }).then((r) => r.data),
};
