// src/utils/url.js
export const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
export const DEFAULT_IMAGE = "/assets/no-image.png";

/**
 * Gera uma URL absoluta segura (para API e uploads).
 */
export function absUrl(path = "") {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  if (path.startsWith("/uploads/")) return path;
  if (!path.startsWith("/")) path = "/" + path;
  return `${API_BASE}${path}`;
}

/**
 * Resolve uma URL de imagem com fallback.
 */
export function resolveImageUrl(url) {
  if (!url) return DEFAULT_IMAGE;

  if (url.startsWith("http")) return url;

  // caminhos que ja apontam para assets locais (public/)
  if (
    url.startsWith("/assets/") ||
    url.startsWith("/static/") ||
    url.startsWith("/images/") ||
    url.startsWith("/uploads/") ||
    url === DEFAULT_IMAGE
  ) {
    return url;
  }

  if (!url.startsWith("/")) url = "/" + url;

  return `${API_BASE}${url}`;
}
