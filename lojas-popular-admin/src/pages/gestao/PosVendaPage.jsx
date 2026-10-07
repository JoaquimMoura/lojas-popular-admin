import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { posVendaApi } from "../../services/posVendaApi";
import ErroAlert from "../../components/gestao/ErroAlert";
import StatusBadge from "../../components/gestao/StatusBadge";
import { ROTULOS, fmtDateTime } from "../../utils/format";

export default function PosVendaPage() {
  const [lista, setLista] = useState([]);
  const [status, setStatus] = useState("");
  const [tipo, setTipo] = useState("");
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState(null);

  useEffect(() => {
    let ativo = true;
    setLoading(true);
    setErro(null);
    posVendaApi
      .listar({ status, tipo })
      .then((d) => ativo && setLista(Array.isArray(d) ? d : []))
      .catch((err) => ativo && setErro(err))
      .finally(() => ativo && setLoading(false));
    return () => {
      ativo = false;
    };
  }, [status, tipo]);

  return (
    <div>
      <h3 className="mb-3">Pós-venda</h3>

      <div className="row g-2 mb-3">
        <div className="col-6 col-md-3">
          <label className="form-label small mb-1">Situação</label>
          <select className="form-select" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Todas</option>
            {Object.entries(ROTULOS.ocorrencia).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
        </div>
        <div className="col-6 col-md-3">
          <label className="form-label small mb-1">Tipo</label>
          <select className="form-select" value={tipo} onChange={(e) => setTipo(e.target.value)}>
            <option value="">Todos</option>
            {Object.entries(ROTULOS.tipoOcorrencia).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
        </div>
      </div>

      <ErroAlert erro={erro} />

      {loading ? (
        <div className="text-center text-muted py-5">Carregando ocorrências...</div>
      ) : lista.length === 0 ? (
        <div className="text-center text-muted py-5">Nenhuma ocorrência encontrada.</div>
      ) : (
        <div className="d-grid gap-2">
          {lista.map((o) => (
            <Link key={o.id} to={`/gestao/pos-venda/${o.id}`} className="gestao-card-link">
              <div className="card">
                <div className="card-body">
                  <div className="d-flex flex-wrap justify-content-between gap-2">
                    <strong>#{o.id} · Pedido #{o.pedidoId}</strong>
                    <span>
                      <StatusBadge tipo="tipoOcorrencia" valor={o.tipo} className="me-1" />
                      <StatusBadge tipo="ocorrencia" valor={o.status} />
                    </span>
                  </div>
                  <div>{o.cliente ?? "—"}</div>
                  <div className="small text-truncate">{o.descricao}</div>
                  <div className="small text-muted">
                    {o.item ? `${o.item} · ` : ""}
                    {fmtDateTime(o.criadaEm)}
                  </div>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
