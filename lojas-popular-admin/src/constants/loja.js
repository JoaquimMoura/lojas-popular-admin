// src/constants/loja.js
// Decisões comerciais da loja usadas em textos e cálculos do site público.
// Alterou a regra comercial? Altere aqui e todo o site acompanha.

export const PARCELAS_SEM_JUROS = 6;
export const TEXTO_PARCELAMENTO = `${PARCELAS_SEM_JUROS}x sem juros`;
export const PROMESSA_PRINCIPAL = "Frete grátis e montagem inclusa";
export const TEXTO_ENTREGA = "Entrega e montagem agendadas com você";

export const MENSAGEM_WHATSAPP_GERAL =
  "Olá! Vim pelo site da Lá Casa Popular Móveis e gostaria de ajuda para escolher meus móveis.";

export function valorParcela(preco, parcelas = PARCELAS_SEM_JUROS) {
  const n = Number(preco || 0);
  return parcelas > 0 ? n / parcelas : n;
}
