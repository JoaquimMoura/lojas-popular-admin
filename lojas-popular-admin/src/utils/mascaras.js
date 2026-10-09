// Máscaras e validações de campos de formulário.

export function somenteDigitos(valor) {
  return String(valor ?? "").replace(/\D/g, "");
}

// Dígitos nacionais (DDD + número), no máximo 11. Tolera o prefixo 55 colado (12 ou 13 dígitos).
function digitosNacionais(valor) {
  let d = somenteDigitos(valor);
  if (d.length > 11 && d.startsWith("55")) d = d.slice(2);
  return d.slice(0, 11);
}

/** (11) 3456-7890 (10 dígitos) ou (11) 98765-4321 (11); aceita digitação parcial. */
export function mascararTelefone(valor) {
  const d = digitosNacionais(valor);
  if (d.length === 0) return "";
  if (d.length <= 2) return `(${d}`;
  const ddd = d.slice(0, 2);
  const resto = d.slice(2);
  if (d.length <= 6) return `(${ddd}) ${resto}`;
  const corte = d.length === 11 ? 5 : 4;
  return `(${ddd}) ${resto.slice(0, corte)}-${resto.slice(corte)}`;
}

/** Somente dígitos para enviar ao servidor; null quando vazio. */
export function telefoneParaEnvio(valor) {
  const d = digitosNacionais(valor);
  return d === "" ? null : d;
}

/** Mensagem de erro quando preenchido com menos de 10 dígitos; null se vazio ou válido. */
export function erroTelefone(valor) {
  const d = digitosNacionais(valor);
  if (d === "" || d.length >= 10) return null;
  return "Telefone incompleto: informe DDD e número, ex.: (11) 98765-4321.";
}
