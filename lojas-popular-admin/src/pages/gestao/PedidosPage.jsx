import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { vendasApi } from "../../services/vendasApi";
import StatusBadge from "../../components/gestao/StatusBadge";
import PendenciasAlert from "../../components/gestao/PendenciasAlert";
import ErroAlert from "../../components/gestao/ErroAlert";
import { CANAIS, ROTULOS, fmtDateTime, fmtMoney } from "../../utils/format";

const TAMANHO = 20;

function Badges({ v }) {
  return (
    <div className="d-flex flex-wrap gap-1">
      <StatusBadge tipo="comercial" valor={v.statusComercial} />
      <StatusBadge tipo="pagamento" valor={v.statusPagamento} />
      <StatusBadge tipo="entrega" valor={v.statusEntrega} />
      {v.revisaoLegado && <span className="badge text-bg-danger">Revisar</span>}
    </div>
  );
}

export default function PedidosPage() {
  const [status, setStatus] = useState("");
  const [busca, setBusca] = useState("");
  const [q, setQ] = useState("");
  const [pagina, setPagina] = useState(0);
  const [dados, setDados] = useState(null);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState(null);
  const [pendencias, setPendencias] = useState([]);

  useEffect(() => {
    vendasApi
      .configuracao()
      .then((c) => setPendencias(c?.pendencias ?? []))
      .catch(() => setPendencias([]));
  }, []);

  const carregar = useCallback(async () => {
    setLoading(true);
    setErro(null);
    try {
      const data = await vendasApi.listar({
        status: status || undefined,
        q: q || undefined,
        pagina,
        tamanho: TAMANHO,
      });
      setDados(data);
    } catch (err) {
      setErro(err);
    } finally {
      setLoading(false);
    }
  }, [status, q, pagina]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  function buscar(e) {
    e.preventDefault();
    setPagina(0);
    setQ(busca.trim());
  }

  const itens = dados?.conteudo ?? [];

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-3 gap-2">
        <h3 className="mb-0">Pedidos</h3>
        <Link to="/gestao/vendas/nova" className="btn btn-success">
          + Nova venda
        </Link>
      </div>

      <PendenciasAlert pendencias={pendencias} />

      <form className="row g-2 mb-3" onSubmit={buscar}>
        <div className="col-12 col-md-4">
          <label className="visually-hidden" htmlFor="filtro-status">Status</label>
          <select
            id="filtro-status"
            className="form-select"
            value={status}
            onChange={(e) => {
              setPagina(0);
              setStatus(e.target.value);
            }}
          >
            <option value="">Todos os status</option>
            {Object.entries(ROTULOS.comercial).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
        </div>
        <div className="col-12 col-md-8">
          <div className="input-group">
            <input
              type="search"
              className="form-control"
              placeholder="Buscar por cliente ou nº do pedido"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
            />
            <button className="btn btn-primary" type="submit">Buscar</button>
          </div>
        </div>
      </form>

      <ErroAlert erro={erro} />

      {loading ? (
        <div className="text-center text-muted py-5">Carregando pedidos...</div>
      ) : itens.length === 0 ? (
        <div className="text-center text-muted py-5">Nenhum pedido encontrado.</div>
      ) : (
        <>
          {/* Desktop: tabela */}
          <div className="card d-none d-md-block">
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0">
                <thead>
                  <tr>
                    <th>Nº</th>
                    <th>Cliente</th>
                    <th>Vendedor</th>
                    <th>Canal</th>
                    <th>Status</th>
                    <th className="text-end">Total</th>
                    <th>Criado em</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {itens.map((v) => (
                    <tr key={v.id}>
                      <td>#{v.id}</td>
                      <td>{v.cliente ?? "—"}</td>
                      <td>{v.vendedor ?? "—"}</td>
                      <td>{CANAIS[v.canal] ?? v.canal ?? "—"}</td>
                      <td><Badges v={v} /></td>
                      <td className="text-end">{fmtMoney(v.total)}</td>
                      <td>{fmtDateTime(v.criadoEm)}</td>
                      <td className="text-end">
                        <Link className="btn btn-sm btn-outline-primary" to={`/gestao/pedidos/${v.id}`}>
                          Abrir
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Celular: cards */}
          <div className="d-md-none d-grid gap-2">
            {itens.map((v) => (
              <Link key={v.id} to={`/gestao/pedidos/${v.id}`} className="gestao-card-link">
                <div className="card">
                  <div className="card-body">
                    <div className="d-flex justify-content-between">
                      <strong>#{v.id} — {v.cliente ?? "Sem cliente"}</strong>
                      <strong>{fmtMoney(v.total)}</strong>
                    </div>
                    <div className="small text-muted mb-2">
                      {v.vendedor ?? "—"} · {CANAIS[v.canal] ?? v.canal ?? "—"} · {fmtDateTime(v.criadoEm)}
                    </div>
                    <Badges v={v} />
                  </div>
                </div>
              </Link>
            ))}
          </div>

          <div className="d-flex justify-content-between align-items-center mt-3">
            <button
              className="btn btn-outline-secondary"
              disabled={pagina <= 0 || loading}
              onClick={() => setPagina((p) => p - 1)}
            >
              Anterior
            </button>
            <span className="small text-muted">
              Página {(dados?.pagina ?? 0) + 1} de {Math.max(dados?.totalPaginas ?? 1, 1)} · {dados?.total ?? 0} pedido(s)
            </span>
            <button
              className="btn btn-outline-secondary"
              disabled={loading || pagina + 1 >= (dados?.totalPaginas ?? 1)}
              onClick={() => setPagina((p) => p + 1)}
            >
              Próxima
            </button>
          </div>
        </>
      )}
    </div>
  );
}
