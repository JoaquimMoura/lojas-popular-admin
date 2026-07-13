// src/services/storeConfigApi.js
import { api } from "./api";

export const storeConfigApi = {
  get: () =>
    api.get("/config/loja").then((r) => ({ ...r.data, nomeLoja: r.data.nome })),

  update: (payload) => api.put("/config/loja", payload).then((r) => r.data),

  uploadLogo: (file) => {
    const form = new FormData();
    form.append("file", file);
    return api.post("/config/loja/logo", form, {
      headers: { "Content-Type": "multipart/form-data" },
    });
  },

  uploadBanner: (file) => {
    const form = new FormData();
    form.append("file", file);
    return api.post("/config/loja/banner", form, {
      headers: { "Content-Type": "multipart/form-data" },
    });
  },
};
