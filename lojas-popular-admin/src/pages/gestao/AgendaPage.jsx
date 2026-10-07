import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { agendaApi } from "../../services/agendaApi";
import ErroAlert from "../../components/gestao/ErroAlert";
import StatusBadge from "../../components/gestao/StatusBadge";
import { ROTULOS, fmtDate, hojeIso } from "../../utils/format";

const MAX_DIAS = 92;
const ORDEM_PERIODO = { MANHA: 0, TARDE: 1, DIA_INTEIRO: 2 };

function diasEntre(de, ate) {
  return Math.round((new Date(`${ate}T00:00:00`) - new Date(`${de}T00:00:00`)) / 86400000);
}

function rotuloTipo(i) {
  if (i.tipo === "MONTAGEM") return "MONTAGEM";
  return i.entregaTipo === "RETIRADA" || i.tipo === "RETIRADA" ? "RETIRADA" : "ENTREGA";
}

export default function AgendaPage() {
  const [de, setDe] = useState(hojeIso());
  const [ate, setAte] = useState(hojeIso(14));
  const [itens, setItens] = useState([]);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState(null);

  const dias = de && ate ? diasEntre(de, ate) : -1;
  const periodoInvalido = dias < 0 || dias > MAX_DIAS;

  useEffect(() => {
    if (periodoInvalido) return undefined;
    let ativo = true;
    setLoading(true);
    setErro(null);
    agendaApi
      .listar(de, ate)
      .then((d) => ativo && setItens(Array.isArray(d) ? d : []))
      .catch((err) => ativo && setErro(err))
      .finally(() => ativo && setLoading(false));
    return () => {
      ativo = false;
    };
  }, [de, ate, periodoInvalido]);

  const grupos = useMemo(() => {
    const mapa = new Map();
    [...itens]
      .sort((a, b) => (ORDEM_PERIODO[a.periodo] ?? 9) - (ORDEM_PERIODO[b.periodo] ?? 9))
      .forEach((i) => {
        if (!mapa.has(i.data)) mapa.set(i.data, []);
        mapa.get(i.data).push(i);
      });
    return [...mapa.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [itens]);

  return (
    <div>
      <h3 className="mb-3">Agenda</h3>

      <div className="row g-2 mb-3">
        <div className="col-6 col-md-3">
          <label className="form-label small mb-1">De</label>
          <input type="date" className="form-control" value={de} onChange={(e) => setDe(e.target.value)} />
        </div>
        <div className="col-6 col-md-3">
          <label className="form-label small mb-1">Até</label>
          <input type="date" className="form-control" value={ate} onChange={(e) => setAte(e.target.value)} />
        </div>
      </div>

      {periodoInvalido && (
        <div className="alert alert-warning">
          Informe um período válido (a data final não pode ser anterior à inicial) de no máximo {MAX_DIAS} dias.
        </div>
      )}
      <ErroAlert erro={erro} />

      {periodoInvalido ? null : loading ? (
        <div className="text-center text-muted py-5">Carregando agenda...</div>
      ) : grupos.length === 0 ? (
        <div className="text-center text-muted py-5">Nada agendado neste período.</div>
      ) : (
        grupos.map(([data, lista]) => (
          <div key={data} className="mb-3">
            <h6 className="text-uppercase text-muted border-bottom pb-1">
              {fmtDate(data)}
              {data === hojeIso() ? " — hoje" : ""}
            </h6>
            <div className="d-grid gap-2">
              {lista.map((i, idx) => (
                <div key={`${i.tipo}-${i.pedidoId}-${idx}`} className="card">
                  <div className="card-body py-2">
                    <div className="d-flex flex-wrap justify-content-between gap-2">
                      <span>
                        <StatusBadge tipo="agenda" valor={rotuloTipo(i)} className="me-1" />
                        <span className="badge text-bg-light border text-dark">
                          {ROTULOS.periodo[i.periodo] ?? i.periodo ?? "—"}
                        </span>
                      </span>
                      <Link to={`/gestao/pedidos/${i.pedidoId}`}>Pedido #{i.pedidoId}</Link>
                    </div>
                    <div className="fw-semibold mt-1">{i.cliente ?? "—"}</div>
                    {i.responsavel && (
                      <div className="small">{i.tipo === "MONTAGEM" ? "Responsável" : "Equipe"}: {i.responsavel}</div>
                    )}
                    {i.endereco && <div className="small text-muted">{i.endereco}</div>}
                    {i.status && <div className="small text-muted">Situação: {ROTULOS.entregaRegistro[i.status] ?? ROTULOS.montagem[i.status] ?? i.status}</div>}
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))
      )}
    </div>
  );
}
