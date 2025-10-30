// src/services/storeApi.js
import { api } from "./api";

export const storeApi = {
  /**
   * Lista publica de produtos.
   * Sempre retorna um array, mesmo que o backend utilize PageImpl.
   */
  listProducts: async ({ page = 0, size = 24 } = {}) => {
    try {
      const { data } = await api.get(
        `/api/v1/produtos?page=${page}&size=${size}`
      );

      if (Array.isArray(data)) {
        return data;
      }

      if (data?.content) {
        return data.content;
      }

      console.warn("Estrutura inesperada da resposta da API:", data);
      return [];
    } catch (error) {
      console.error("Erro ao buscar produtos:", error);
      return [];
    }
  },

  /**
   * Obtem um produto individual pelo identificador.
   */
  getProduct: async (id) => {
    try {
      const { data } = await api.get(`/api/v1/produtos/${id}`);
      return data;
    } catch (error) {
      console.error("Erro ao buscar produto:", error);
      throw error;
    }
  },

  /**
   * Dados publicos da loja (fanpage e vitrine).
   */
  getStoreInfo: async () => {
    try {
      const { data } = await api.get(`/api/v1/config/loja`);
      return data;
    } catch (error) {
      console.error("Erro ao buscar informacoes da loja:", error);
      return null;
    }
  },
};
