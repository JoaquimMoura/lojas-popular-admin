// src/utils/format.js — formatação e rótulos da área de gestão
const moneyFmt = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

export function fmtMoney(v) {
  if (v === null || v === undefined || v === "") return "—";
  return moneyFmt.format(Number(v));
}

export function fmtPercent(v) {
  if (v === null || v === undefined || v === "") return "—";
  return `${Number(v).toLocaleString("pt-BR", { maximumFractionDigits: 4 })}%`;
}

export function fmtDateTime(v) {
  if (!v) return "—";
  return new Date(v).toLocaleString("pt-BR");
}

export const ROTULOS = {
  comercial: {
    LEGADO: "Legado",
    RASCUNHO: "Rascunho",
    AGUARDANDO_APROVACAO: "Aguardando aprovação",
    CONFIRMADA: "Confirmada",
    CANCELADA: "Cancelada",
  },
  pagamento: { NAO_INFORMADO: "Não informado", PENDENTE: "Pendente", PAGO: "Pago" },
  entrega: {
    NAO_INFORMADO: "Não informada",
    NAO_AGENDADA: "Não agendada",
    AGENDADA: "Agendada",
    SAIU: "Saiu para entrega",
    ENTREGUE: "Entregue",
  },
  montagem: {
    NAO_INFORMADO: "Não informada",
    NAO_AGENDADA: "Não agendada",
    AGENDADA: "Agendada",
    CONCLUIDA: "Concluída",
    NAO_NECESSARIA: "Não necessária",
  },
  reserva: { ATIVA: "Ativa", LIBERADA: "Liberada", CONSUMIDA: "Consumida" },
  desconto: { PENDENTE: "Pendente", APROVADA: "Aprovada", REJEITADA: "Rejeitada", INVALIDADA: "Invalidada" },
};

export const COR = {
  comercial: {
    LEGADO: "secondary",
    RASCUNHO: "secondary",
    AGUARDANDO_APROVACAO: "warning",
    CONFIRMADA: "success",
    CANCELADA: "danger",
  },
  pagamento: { NAO_INFORMADO: "secondary", PENDENTE: "warning", PAGO: "success" },
  entrega: { NAO_INFORMADO: "secondary", NAO_AGENDADA: "secondary", AGENDADA: "info", SAIU: "primary", ENTREGUE: "success" },
  montagem: { NAO_INFORMADO: "secondary", NAO_AGENDADA: "secondary", AGENDADA: "info", CONCLUIDA: "success", NAO_NECESSARIA: "light" },
  reserva: { ATIVA: "success", LIBERADA: "secondary", CONSUMIDA: "primary" },
  desconto: { PENDENTE: "warning", APROVADA: "success", REJEITADA: "danger", INVALIDADA: "secondary" },
};

export const FORMAS = { DINHEIRO: "Dinheiro", PIX: "Pix", CARTAO: "Cartão" };
export const CANAIS = { LOJA: "Loja", WHATSAPP: "WhatsApp", SITE: "Site" };
export const TIPOS_ENTREGA = { ENTREGA: "Entrega", RETIRADA: "Retirada na loja" };
export const MODALIDADES_ITEM = { PRONTA_ENTREGA: "Pronta entrega", ENCOMENDA: "Encomenda" };
export const MODALIDADES_PRODUTO = {
  PRONTA_ENTREGA: "Pronta entrega",
  ENCOMENDA: "Encomenda",
  AMBAS: "Pronta entrega e encomenda",
};
export const ARREDONDAMENTOS = {
  HALF_UP: "Meio para cima (0,5 arredonda para cima)",
  HALF_EVEN: "Meio para o par (arredondamento bancário)",
  DOWN: "Sempre para baixo (trunca)",
  UP: "Sempre para cima",
};
export const PERFIS = { ADMIN: "Proprietário", GERENTE: "Gerente", VENDEDOR: "Vendedor" };

export function isGestor(user) {
  const r = user?.roles ?? [];
  return r.includes("ADMIN") || r.includes("GERENTE");
}

export function novaChave() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID();
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}
