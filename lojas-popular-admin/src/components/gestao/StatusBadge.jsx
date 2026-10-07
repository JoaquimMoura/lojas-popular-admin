import { COR, ROTULOS } from "../../utils/format";

/** tipo: comercial | pagamento | entrega | montagem | reserva | desconto */
export default function StatusBadge({ tipo, valor, className = "" }) {
  if (!valor) return null;
  const cor = COR[tipo]?.[valor] ?? "secondary";
  const texto = ROTULOS[tipo]?.[valor] ?? valor;
  const textDark = cor === "warning" || cor === "light" || cor === "info" ? " text-dark" : "";
  return <span className={`badge text-bg-${cor}${textDark} ${className}`}>{texto}</span>;
}
