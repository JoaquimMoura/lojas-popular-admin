// src/services/arquivosApi.js
import { api } from "./api";

export const arquivosApi = {
  /** Baixa o arquivo com o token (o endpoint exige Authorization) e abre em nova aba. */
  abrir: async (caminho) => {
    const r = await api.get("/arquivos", { params: { caminho }, responseType: "blob" });
    const url = URL.createObjectURL(r.data);
    const w = window.open(url, "_blank", "noopener");
    if (!w) {
      // pop-up bloqueado: tenta abrir por um link temporário
      const a = document.createElement("a");
      a.href = url;
      a.target = "_blank";
      a.rel = "noopener";
      a.click();
    }
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  },
};
