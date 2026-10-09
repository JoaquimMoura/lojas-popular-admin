import { api } from "./api";

/** Remove acento, caixa e espaços repetidos (busca tolerante). */
export function normalizarTexto(s) {
  return (s ?? "")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

export const materiaisApi = {
  listar: ({ q = "", incluirInativos = false } = {}) =>
    api.get("/materiais", { params: { q: q || undefined, incluirInativos } }).then((r) => r.data),
  criar: (nome) => api.post("/materiais", { nome }).then((r) => r.data),
  atualizar: (id, { nome, ativo }) => api.put(`/materiais/${id}`, { nome, ativo }).then((r) => r.data),
};
