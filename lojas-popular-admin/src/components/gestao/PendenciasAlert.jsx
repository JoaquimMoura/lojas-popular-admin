/** Lista de pendências de configuração comercial (codigo, descricao, bloqueia). */
export default function PendenciasAlert({ pendencias, titulo = "Configuração comercial pendente" }) {
  if (!pendencias || pendencias.length === 0) return null;
  return (
    <div className="alert alert-warning border-warning" role="alert">
      <div className="fw-semibold mb-1">{titulo}</div>
      <ul className="mb-0 ps-3">
        {pendencias.map((p) => (
          <li key={p.codigo}>
            <strong>{p.codigo}</strong> — {p.descricao}
            {p.bloqueia && <span className="text-muted"> (bloqueia: {p.bloqueia})</span>}
          </li>
        ))}
      </ul>
    </div>
  );
}
