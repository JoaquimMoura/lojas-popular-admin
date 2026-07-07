// src/utils/whatsapp.js
export const DEFAULT_PHONE = "5511961118141";

export function buildWhatsAppUrl(phone, text) {
  const p = String(phone || DEFAULT_PHONE).replace(/\D/g, "");
  const msg = encodeURIComponent(text || "Olá! Gostaria de falar com a loja.");
  return `https://wa.me/${p}?text=${msg}`;
}
