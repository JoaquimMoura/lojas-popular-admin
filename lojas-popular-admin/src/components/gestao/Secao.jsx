export function Secao({ titulo, children }) {
  return (
    <div className="card mb-3">
      <div className="card-header">{titulo}</div>
      <div className="card-body">{children}</div>
    </div>
  );
}

export function Linha({ rotulo, children }) {
  return (
    <div className="d-flex justify-content-between gap-3 py-1">
      <span className="text-muted">{rotulo}</span>
      <span className="text-end">{children}</span>
    </div>
  );
}
