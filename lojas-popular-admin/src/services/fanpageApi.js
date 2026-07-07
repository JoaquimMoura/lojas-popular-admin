import { api } from "./api";

export const fanpageApi = {
  get: () => api.get("/fanpage").then((r) => r.data),
  update: (payload) => api.put("/fanpage", payload).then((r) => r.data),
};
