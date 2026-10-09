/** Um valor está vazio? (usado para destacar obrigatórias e validar antes de enviar) */
export function valorVazio(c, v) {
  if (!v) return true;
  if (c.tipo === "TEXTO") return !(v.valorTexto ?? "").trim();
  if (c.tipo === "NUMERO") return v.valorNumero === "" || v.valorNumero == null;
  return !(v.opcaoIds?.length > 0);
}
