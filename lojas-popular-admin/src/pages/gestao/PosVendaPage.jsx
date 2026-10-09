import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { posVendaApi } from "../../services/posVendaApi";
import ErroAlert from "../../components/gestao/ErroAlert";
import StatusBadge from "../../components/gestao/StatusBadge";
import FiltroAutocomplete, { SemResultados } from "../../components/FiltroAutocomplete";
import { casaBusca } from "../../utils/busca";
import { ROTULOS, fmtDateTime } from "../../utils/format";

export default function PosVendaPage() {
  const [lista, setLista] = useState([]);
  const [status, setStatus] = useState("");
  const [tipo, setTipo] = useState("");
  const [busca, setBusca] = useState("");
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

  const textoOc = (o) => [o.cliente, o.pedidoId, o.id, o.item, o.descricao];
  const filtradas = useMemo(() => lista.filter((o) => casaBusca(busca, textoOc(o))), [lista, busca]);

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
        <div className="col-12 col-md-3 order-md-last">
          <label className="form-label small mb-1">Buscar</label>
          <FiltroAutocomplete
            value={busca}
            onChange={setBusca}
            itens={lista}
            getRotulo={(o) => `#${o.id} · ${o.cliente ?? "—"}`}
            getValorBusca={(o) => o.cliente ?? String(o.id)}
            getDetalhe={(o) => `Pedido #${o.pedidoId}${o.item ? ` · ${o.item}` : ""}`}
            getTextoBusca={textoOc}
            placeholder="Cliente, produto ou nº"
          />
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
      ) : filtradas.length === 0 ? (
        <SemResultados busca={busca} vazio="Nenhuma ocorrência encontrada." />
      ) : (
        <div className="d-grid gap-2">
          {filtradas.map((o) => (
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
