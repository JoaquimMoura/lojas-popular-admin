// src/utils/textoLoja.js
// Corrige textos legados vindos da configuração salva no banco (fanpage / loja):
// promessas antigas (12x, boleto, prazo de entrega) e acentos faltando.
// Não altera depoimentos (conteúdo real dos clientes).
import { PARCELAS_SEM_JUROS, TEXTO_ENTREGA } from "../constants/loja";

const PALAVRAS = {
  condicoes: "condições",
  condicao: "condição",
  decoracao: "decoração",
  simulacao: "simulação",
  complicacoes: "complicações",
  escritorio: "escritório",
  voce: "você",
  armrios: "armários",
  armarios: "armários",
  balcoes: "balcões",
  cabeceras: "cabeceiras",
  acessorios: "acessórios",
  espaco: "espaço",
  orcamento: "orçamento",
  comodo: "cômodo",
  colecoes: "coleções",
  colecao: "coleção",
  beneficios: "benefícios",
  planejados: "planejados",
  tendencias: "tendências",
  familias: "famílias",
};

const RE_PALAVRAS = new RegExp(`\\b(${Object.keys(PALAVRAS).join("|")})\\b`, "gi");

function comCaixa(original, correta) {
  return original[0] === original[0].toUpperCase() && original[0] !== original[0].toLowerCase()
    ? correta[0].toUpperCase() + correta.slice(1)
    : correta;
}

export function corrigirTexto(valor) {
  if (typeof valor !== "string" || !valor || valor.startsWith("http") || valor.startsWith("/")) {
    return valor;
  }
  let t = valor;

  t = t.replace(/(?<!casa\s)(?:lojas\s+)?popular\s+m[oó]veis|popular\s+movies/gi, "Lá Casa Popular Móveis");
  t = t.replace(/\b(\d{1,2})\s*x(?=\s|$|[.,;)])/gi, (m, n) =>
    Number(n) === 12 ? `${PARCELAS_SEM_JUROS}x` : m
  );
  t = t.replace(/\b(ate|até)\s+(6x)/gi, "até $2");
  t = t.replace(/\bcomo e o seu\b/gi, "como é o seu");
  t = t.replace(RE_PALAVRAS, (m) => comCaixa(m, PALAVRAS[m.toLowerCase()]));
  t = t.replace(/(\d)hs\b/g, "$1h");
  t = t.replace(/[;,]+\s*$/, "");

  // promessas de prazo que a loja não definiu para pronta entrega
  if (/entrega expressa/i.test(t)) t = t.replace(/entrega expressa/i, "Entrega agendada");
  if (/\b5 dias [uú]teis/i.test(t) && !/encomenda/i.test(t)) t = TEXTO_ENTREGA + (/\.$/.test(t) ? "." : "");
  if (/tempo recorde|mesmo dia/i.test(t)) {
    t = t.replace(/\s*em tempo recorde para (você|voce) usar no mesmo dia\.?/i, " com data combinada com você.");
  }
  t = t.replace(/\s+/g, " ").trim();
  return t;
}

export function corrigirEndereco(valor) {
  if (typeof valor !== "string") return valor;
  return valor.replace(/\bavenida presidente m[eé]dici\b/i, "Avenida Presidente Médici");
}

// Percorre a configuração (objetos/arrays) corrigindo strings, exceto depoimentos.
export function corrigirConfig(valor, chave = "") {
  if (chave === "testimonials") return valor;
  if (typeof valor === "string") return corrigirTexto(valor);
  if (Array.isArray(valor)) return valor.map((v) => corrigirConfig(v, chave));
  if (valor && typeof valor === "object") {
    return Object.fromEntries(Object.entries(valor).map(([k, v]) => [k, corrigirConfig(v, k)]));
  }
  return valor;
}

// Formas de pagamento: a loja não trabalha com boleto.
// Fotos de banco de imagens (Unsplash) salvas como exemplo na configuração não são
// fotos da loja: o site usa o bloco de marca no lugar até existirem fotos reais.
export function fotoDaLoja(url) {
  return url && !/images\.unsplash\.com/i.test(url) ? url : "";
}

export function semBoleto(lista) {
  return (lista ?? []).filter((x) => !/boleto/i.test(String(x)));
}
