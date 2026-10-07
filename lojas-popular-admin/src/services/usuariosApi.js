// src/services/usuariosApi.js
import { api } from "./api";

export const usuariosApi = {
  listar: () => api.get("/usuarios").then((r) => r.data),
  vendedores: () => api.get("/usuarios/vendedores").then((r) => r.data),
  criar: (payload) => api.post("/usuarios", payload).then((r) => r.data),
  atualizar: (id, payload) => api.put(`/usuarios/${id}`, payload).then((r) => r.data),
  ativar: (id) => api.post(`/usuarios/${id}/ativar`).then((r) => r.data),
  desativar: (id) => api.post(`/usuarios/${id}/desativar`).then((r) => r.data),
  redefinirSenha: (id, senha) => api.put(`/usuarios/${id}/senha`, { senha }),
};
