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

/** Data (yyyy-MM-dd) em pt-BR, sem deslocamento de fuso. */
export function fmtDate(v) {
  if (!v) return "—";
  const [a, m, d] = String(v).slice(0, 10).split("-");
  return a && m && d ? `${d}/${m}/${a}` : String(v);
}

/** Data local de hoje em yyyy-MM-dd; `dias` soma dias. */
export function hojeIso(dias = 0) {
  const d = new Date();
  d.setDate(d.getDate() + dias);
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
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
    TENTATIVA_FRUSTRADA: "Tentativa frustrada",
    ENTREGUE: "Entregue",
  },
  entregaRegistro: {
    AGENDADA: "Agendada",
    SAIU: "Saiu para entrega",
    TENTATIVA_FRUSTRADA: "Tentativa frustrada",
    ENTREGUE: "Entregue",
    CANCELADA: "Cancelada",
  },
  eventoEntrega: {
    AGENDADA: "Agendada",
    REAGENDADA: "Reagendada",
    SAIDA: "Saída registrada",
    TENTATIVA_FRUSTRADA: "Tentativa frustrada",
    ENTREGUE: "Entregue",
    CANCELADA: "Cancelada",
  },
  encomenda: {
    AGUARDANDO_PEDIDO: "Aguardando pedido",
    PEDIDO_REALIZADO: "Pedido realizado",
    RECEBIDA: "Recebida",
    CANCELADA: "Cancelada",
  },
  ocorrencia: {
    ABERTA: "Aberta",
    DEVOLUCAO_RECEBIDA: "Devolução recebida",
    RESOLVIDA: "Resolvida",
    CANCELADA: "Cancelada",
  },
  tipoOcorrencia: { ASSISTENCIA: "Assistência", TROCA: "Troca", DEVOLUCAO: "Devolução" },
  condicao: { APTA_REVENDA: "Apta para revenda", NAO_APTA: "Não apta" },
  movimentacao: {
    ENTRADA_ENCOMENDA: "Entrada de encomenda",
    SAIDA_VENDA: "Saída de venda",
    AJUSTE_INVENTARIO: "Ajuste de inventário",
    ENTRADA_DEVOLUCAO: "Entrada de devolução",
  },
  periodo: { MANHA: "Manhã", TARDE: "Tarde", DIA_INTEIRO: "Dia inteiro" },
  agenda: { ENTREGA: "Entrega", RETIRADA: "Retirada", MONTAGEM: "Montagem" },
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
  entrega: {
    NAO_INFORMADO: "secondary",
    NAO_AGENDADA: "secondary",
    AGENDADA: "info",
    SAIU: "primary",
    TENTATIVA_FRUSTRADA: "danger",
    ENTREGUE: "success",
  },
  entregaRegistro: { AGENDADA: "info", SAIU: "primary", TENTATIVA_FRUSTRADA: "danger", ENTREGUE: "success", CANCELADA: "secondary" },
  eventoEntrega: { AGENDADA: "info", REAGENDADA: "info", SAIDA: "primary", TENTATIVA_FRUSTRADA: "danger", ENTREGUE: "success", CANCELADA: "secondary" },
  encomenda: { AGUARDANDO_PEDIDO: "warning", PEDIDO_REALIZADO: "info", RECEBIDA: "success", CANCELADA: "secondary" },
  ocorrencia: { ABERTA: "warning", DEVOLUCAO_RECEBIDA: "info", RESOLVIDA: "success", CANCELADA: "secondary" },
  tipoOcorrencia: { ASSISTENCIA: "secondary", TROCA: "primary", DEVOLUCAO: "dark" },
  condicao: { APTA_REVENDA: "success", NAO_APTA: "danger" },
  movimentacao: { ENTRADA_ENCOMENDA: "success", SAIDA_VENDA: "primary", AJUSTE_INVENTARIO: "warning", ENTRADA_DEVOLUCAO: "info" },
  periodo: { MANHA: "light", TARDE: "light", DIA_INTEIRO: "light" },
  agenda: { ENTREGA: "primary", RETIRADA: "info", MONTAGEM: "warning" },
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

export const LIMITE_ARQUIVO_MB = 5;
export function arquivoGrande(arquivo) {
  return !!arquivo && arquivo.size > LIMITE_ARQUIVO_MB * 1024 * 1024;
}

export function isGestor(user) {
  const r = user?.roles ?? [];
  return r.includes("ADMIN") || r.includes("GERENTE");
}

export function novaChave() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID();
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

/** Quantidade com sinal explícito (+3, -2). */
export function sinal(n) {
  if (n === null || n === undefined) return "—";
  return n > 0 ? `+${n}` : String(n);
}
