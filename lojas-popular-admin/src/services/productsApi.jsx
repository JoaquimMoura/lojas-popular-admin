// ✅ src/services/productsApi.js
import { api } from "./api";

export const productsApi = {
  list: (params = { page: 0, size: 20, nome: "" }) =>
    api.get("/produtos", { params }),

  get: (id) => api.get(`/produtos/${id}`),

  create: (payload) => api.post("/produtos", payload),

  update: (id, payload) => api.put(`/produtos/${id}`, payload),

  remove: (id) => api.delete(`/produtos/${id}`),

  // ✅ Capa
  uploadCover: (id, file) => {
    const form = new FormData();
    form.append("file", file);

    return api.post(`/produtos/${id}/imagem`, form, {
      headers: { "Content-Type": "multipart/form-data" },
    });
  },

  // ✅ Galeria — Upload múltiplo
  uploadGallery: (id, files) => {
    const form = new FormData();
    files.forEach((file) => form.append("files", file));

    return api.post(`/produtos/${id}/galeria`, form, {
      headers: { "Content-Type": "multipart/form-data" },
    });
  },

  // ✅ Remover imagem específica da galeria
  deleteImage: (id, url) =>
    api.delete(`/produtos/${id}/galeria`, {
      params: { url },
    }),

  // ✅ Reordenar imagens após drag&drop
  reorderGallery: (id, orderedUrls) =>
    api.put(`/produtos/${id}/galeria/reordena`, orderedUrls),

  // ✅ Upload de imagem de variação (cor)
  uploadVariacaoImage: (produtoId, variacaoId, file) => {
    const form = new FormData();
    form.append("file", file);
    return api.post(`/produtos/${produtoId}/variacoes/${variacaoId}/imagem`, form, {
      headers: { "Content-Type": "multipart/form-data" },
    });
  },

  // ✅ Busca produto por ID (retorna data diretamente)
  byId: (id) => api.get(`/produtos/${id}`).then(r => r.data),
};
