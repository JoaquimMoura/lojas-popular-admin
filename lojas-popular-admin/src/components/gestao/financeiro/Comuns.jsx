import { fmtMoney } from "../../../utils/format";

/** Tabela no desktop e cartões no celular (sem rolagem horizontal da página). */
export function TabelaCards({ colunas, linhas, chave = (l) => l.id, destaque, vazio = "Nenhum registro." }) {
  if (!linhas || linhas.length === 0) return <div className="text-center text-muted py-4">{vazio}</div>;
  return (
    <>
      <div className="table-responsive d-none d-md-block">
        <table className="table align-middle mb-0">
          <thead>
            <tr>
              {colunas.map((c) => (
                <th key={c.titulo} className={c.fim ? "text-end" : ""}>{c.titulo}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {linhas.map((l) => (
              <tr key={chave(l)} className={destaque?.(l) ?? ""}>
                {colunas.map((c) => (
                  <td key={c.titulo} className={c.fim ? "text-end" : ""}>{c.render(l)}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="d-md-none d-grid gap-2">
        {linhas.map((l) => (
          <div key={chave(l)} className={`border rounded p-2 ${destaque?.(l) ?? ""}`}>
            {colunas.map((c) => (
              <div key={c.titulo} className="d-flex justify-content-between gap-3 py-1">
                <span className="text-muted small">{c.titulo}</span>
                <span className="text-end text-break">{c.render(l)}</span>
              </div>
            ))}
          </div>
        ))}
      </div>
    </>
  );
}

/** Lista de avisos/bloqueios textuais devolvidos pela API (array ou mapa chave->texto). */
export function AvisosLista({ itens, titulo, variant = "warning" }) {
  const lista = Array.isArray(itens) ? itens : itens && typeof itens === "object" ? Object.values(itens) : [];
  if (lista.length === 0) return null;
  return (
    <div className={`alert alert-${variant}`} role="alert">
      {titulo && <div className="fw-semibold mb-1">{titulo}</div>}
      <ul className="mb-0 ps-3">
        {lista.map((t, i) => (
          <li key={i}>{t}</li>
        ))}
      </ul>
    </div>
  );
}

/** Valor que pode estar pendente de decisão do proprietário (nunca preenche um padrão). */
export function Pendente({ codigo, children }) {
  if (children === null || children === undefined || children === "") {
    return <span className="badge text-bg-warning text-dark">Decisão pendente{codigo ? ` (${codigo})` : ""}</span>;
  }
  return <>{children}</>;
}

export function Totais({ itens }) {
  return (
    <div className="row g-2 mb-3">
      {itens.map((t) => (
        <div key={t.rotulo} className="col-6 col-md">
          <div className="border rounded p-2 h-100">
            <div className="small text-muted">{t.rotulo}</div>
            <div className={`fw-semibold ${t.classe ?? ""}`}>{t.texto ?? fmtMoney(t.valor)}</div>
            {t.nota && <div className="small text-muted">{t.nota}</div>}
          </div>
        </div>
      ))}
    </div>
  );
}

/** Barra de botões: largura total no celular. */
export function Barra({ children }) {
  return <div className="d-grid d-sm-flex flex-wrap gap-2 mb-3">{children}</div>;
}

/** Campo de valor monetário (decimal com 2 casas). */
export function CampoValor({ label, value, onChange, obrigatorio, min = "0.01", ajuda, ...rest }) {
  return (
    <div>
      <label className="form-label">
        {label}
        {obrigatorio && <span className="text-danger"> *</span>}
      </label>
      <input type="number" inputMode="decimal" step="0.01" min={min} className="form-control" value={value}
        onChange={(e) => onChange(e.target.value)} {...rest} />
      {ajuda && <div className="form-text">{ajuda}</div>}
    </div>
  );
}

/** Texto de apoio de ação cujo botão está desabilitado. */
export function Carregando({ texto = "Carregando..." }) {
  return <div className="text-center text-muted py-5" aria-live="polite">{texto}</div>;
}
