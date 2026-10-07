import { useEffect, useMemo, useState } from "react";
import { estoqueApi } from "../../services/estoqueApi";
import ErroAlert from "../../components/gestao/ErroAlert";

function nomeItem(s) {
  return s.variacaoDescricao ? `${s.produtoNome} — ${s.variacaoDescricao}` : s.produtoNome;
}

export default function EstoquePage() {
  const [saldos, setSaldos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState(null);
  const [apenasAlertas, setApenasAlertas] = useState(false);
  const [busca, setBusca] = useState("");

  useEffect(() => {
    let ativo = true;
    setLoading(true);
    setErro(null);
    estoqueApi
      .saldos({ apenasAlertas })
      .then((d) => ativo && setSaldos(Array.isArray(d) ? d : []))
      .catch((err) => ativo && setErro(err))
      .finally(() => ativo && setLoading(false));
    return () => {
      ativo = false;
    };
  }, [apenasAlertas]);

  const filtrados = useMemo(() => {
    const t = busca.trim().toLowerCase();
    if (!t) return saldos;
    return saldos.filter(
      (s) => nomeItem(s).toLowerCase().includes(t) || (s.sku ?? "").toLowerCase().includes(t),
    );
  }, [saldos, busca]);

  return (
    <div>
      <h3 className="mb-3">Estoque</h3>

      <div className="row g-2 mb-3 align-items-center">
        <div className="col-12 col-md-6">
          <input type="search" className="form-control" placeholder="Buscar por nome ou SKU" value={busca}
            onChange={(e) => setBusca(e.target.value)} />
        </div>
        <div className="col-12 col-md-6">
          <div className="form-check form-switch">
            <input className="form-check-input" type="checkbox" id="so-alertas" checked={apenasAlertas}
              onChange={(e) => setApenasAlertas(e.target.checked)} />
            <label className="form-check-label" htmlFor="so-alertas">Só com alertas</label>
          </div>
        </div>
      </div>

      <ErroAlert erro={erro} />

      {loading ? (
        <div className="text-center text-muted py-5">Carregando estoque...</div>
      ) : filtrados.length === 0 ? (
        <div className="text-center text-muted py-5">Nenhum item encontrado.</div>
      ) : (
        <>
          <div className="card d-none d-md-block">
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0">
                <thead>
                  <tr>
                    <th>Produto</th>
                    <th>SKU</th>
                    <th className="text-end">Físico</th>
                    <th className="text-end">Reservado</th>
                    <th className="text-end">Disponível</th>
                    <th>Alerta</th>
                  </tr>
                </thead>
                <tbody>
                  {filtrados.map((s, i) => (
                    <tr key={`${s.produtoId}-${s.variacaoId ?? "p"}-${i}`} className={s.alerta ? "table-warning" : ""}>
                      <td>{nomeItem(s)}</td>
                      <td>{s.sku ?? "—"}</td>
                      <td className="text-end">{s.fisico ?? "—"}</td>
                      <td className="text-end">{s.reservado}</td>
                      <td className="text-end fw-semibold">{s.disponivel ?? "—"}</td>
                      <td>{s.alerta ? <span className="badge text-bg-danger">{s.alerta}</span> : "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="d-md-none d-grid gap-2">
            {filtrados.map((s, i) => (
              <div key={`${s.produtoId}-${s.variacaoId ?? "p"}-${i}`} className={`card ${s.alerta ? "border-danger" : ""}`}>
                <div className="card-body">
                  <div className="fw-semibold">{nomeItem(s)}</div>
                  <div className="small text-muted mb-2">{s.sku ? `SKU ${s.sku}` : "Sem SKU"}</div>
                  <div className="row text-center g-1">
                    <div className="col-4"><div className="small text-muted">Físico</div>{s.fisico ?? "—"}</div>
                    <div className="col-4"><div className="small text-muted">Reservado</div>{s.reservado}</div>
                    <div className="col-4"><div className="small text-muted">Disponível</div><strong>{s.disponivel ?? "—"}</strong></div>
                  </div>
                  {s.alerta && <div className="badge text-bg-danger mt-2 text-wrap">{s.alerta}</div>}
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
