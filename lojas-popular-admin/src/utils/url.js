// src/utils/url.js
export const API_BASE = "http://localhost:8080";

export function absUrl(path = "") {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  if (!path.startsWith("/")) path = "/" + path;
  return `${API_BASE}${path}`;
}

// 🔁 Alias compatível com o código legado
export const resolveImageUrl = absUrl;
