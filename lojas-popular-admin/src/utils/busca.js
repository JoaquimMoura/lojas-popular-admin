import { somenteDigitos } from "./mascaras";

/** Minúsculas e sem acento, para comparar textos. */
export function normalizar(s) {
  return String(s ?? "")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .trim();
}

/**
 * Verdadeiro se todos os termos da busca aparecem em algum campo (sem acento/maiúscula).
 * Termos numéricos (CPF, telefone, nº) também casam ignorando pontuação.
 */
export function casaBusca(busca, ...campos) {
  const termos = normalizar(busca).split(/\s+/).filter(Boolean);
  if (termos.length === 0) return true;
  const texto = normalizar(campos.flat().filter((c) => c != null && c !== "").join(" | "));
  const digitos = somenteDigitos(texto);
  return termos.every((t) => {
    if (texto.includes(t)) return true;
    const dt = somenteDigitos(t);
    return dt.length >= 2 && /^[\d().\-+\s]+$/.test(t) && digitos.includes(dt);
  });
}
