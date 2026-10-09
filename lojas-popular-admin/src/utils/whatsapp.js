// src/utils/whatsapp.js
export const DEFAULT_PHONE = "5511961118141";

export function buildWhatsAppUrl(phone, text) {
  let p = String(phone || DEFAULT_PHONE).replace(/\D/g, "");
  if (p.length === 10 || p.length === 11) p = `55${p}`; // número nacional sem DDI
  const msg = encodeURIComponent(text || "Olá! Gostaria de falar com a loja.");
  return `https://wa.me/${p}?text=${msg}`;
}
