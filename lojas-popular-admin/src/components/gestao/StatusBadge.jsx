import { COR, DESCRICOES, NOME_STATUS, ROTULOS } from "../../utils/format";

/** tipo: comercial | pagamento | entrega | montagem | reserva | desconto */
export default function StatusBadge({ tipo, valor, className = "", comRotulo = false }) {
  if (!valor) return null;
  const cor = COR[tipo]?.[valor] ?? "secondary";
  const texto = ROTULOS[tipo]?.[valor] ?? valor;
  const textDark = cor === "warning" || cor === "light" || cor === "info" ? " text-dark" : "";
  const dica = DESCRICOES[tipo]?.[valor];
  return (
    <span className={`badge text-bg-${cor}${textDark} ${className}`} title={dica} tabIndex={dica ? 0 : undefined}>
      {comRotulo ? `${NOME_STATUS[tipo] ?? tipo}: ` : ""}{texto}
    </span>
  );
}
