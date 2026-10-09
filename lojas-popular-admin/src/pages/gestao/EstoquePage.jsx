import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "react-toastify";
import { estoqueApi } from "../../services/estoqueApi";
import { useAuth } from "../../context/AuthContext";
import ErroAlert from "../../components/gestao/ErroAlert";
import FormModal from "../../components/gestao/FormModal";
import StatusBadge from "../../components/gestao/StatusBadge";
import { useChave } from "../../components/gestao/useChave";
import FiltroAutocomplete, { SemResultados } from "../../components/FiltroAutocomplete";
import { casaBusca } from "../../utils/busca";
import { fmtDateTime, isGestor, sinal } from "../../utils/format";

function nomeItem(s) {
  return s.variacaoDescricao ? `${s.produtoNome} — ${s.variacaoDescricao}` : s.produtoNome;
}

function AjusteModal({ s, onClose, onAjustado }) {
  const [contado, setContado] = useState("");
  const [motivo, setMotivo] = useState("");
  const chave = useChave();
  const fisico = s.fisico ?? 0;
  const n = Number(contado);
  const valido = contado !== "" && Number.isInteger(n) && n >= 0;
  const diferenca = valido ? n - fisico : null;

  return (
    <FormModal titulo="Contar / ajustar estoque" submitLabel="Registrar ajuste" size="md"
      submitDisabled={!valido || motivo.trim() === ""} onClose={onClose}
      onSubmit={async () => {
        await estoqueApi.ajustar(
          { produtoId: s.produtoId, variacaoId: s.variacaoId ?? null, contado: n, motivo: motivo.trim() },
          chave.obter(`${s.produtoId}:${s.variacaoId ?? "p"}:${n}:${motivo.trim()}`),
        );
        chave.limpar();
        toast.success("Ajuste de inventário registrado.");
        onAjustado();
      }}>
      <div className="fw-semibold mb-2">{nomeItem(s)}</div>
      <div className="row text-center g-2 mb-3">
        <div className="col-6"><div className="small text-muted">Físico no sistema</div><strong>{s.fisico ?? "—"}</strong></div>
        <div className="col-6"><div className="small text-muted">Reservado</div><strong>{s.reservado}</strong></div>
      </div>
      <div className="mb-3">
        <label className="form-label">Quantidade contada <span className="text-danger">*</span></label>
        <input type="number" min="0" className="form-control" value={contado}
          onChange={(e) => setContado(e.target.value)} autoFocus />
        {diferenca !== null && (
          <div className={`form-text ${diferenca === 0 ? "" : "fw-semibold"}`}>
            Diferença: {sinal(diferenca)} un.{diferenca === 0 ? " (nenhum ajuste necessário)" : ""}
          </div>
        )}
      </div>
      {valido && n < s.reservado && (
        <div className="alert alert-warning">
          A quantidade contada ({n}) é menor que o reservado ({s.reservado}): há vendas com reserva que ficarão sem
          estoque físico suficiente.
        </div>
      )}
      <div>
        <label className="form-label">Motivo <span className="text-danger">*</span></label>
        <textarea className="form-control" rows={3} maxLength={300} value={motivo}
          onChange={(e) => setMotivo(e.target.value)} />
      </div>
    </FormModal>
  );
}

function Movimentacoes({ filtro, onLimparFiltro }) {
  const [pagina, setPagina] = useState(0);
  const [pedido, setPedido] = useState("");
  const [dados, setDados] = useState(null);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState(null);

  const { produtoId, variacaoId } = filtro ?? {};
  const pedidoId = pedido.trim() && /^\d+$/.test(pedido.trim()) ? Number(pedido.trim()) : null;

  useEffect(() => {
    let ativo = true;
    setLoading(true);
    setErro(null);
    estoqueApi
      .movimentacoes({ produtoId, variacaoId, pedidoId, pagina, tamanho: 30 })
      .then((d) => ativo && setDados(d))
      .catch((err) => ativo && setErro(err))
      .finally(() => ativo && setLoading(false));
    return () => {
      ativo = false;
    };
  }, [produtoId, variacaoId, pedidoId, pagina]);

  const lista = dados?.conteudo ?? [];
  const totalPaginas = dados?.totalPaginas ?? 0;

  return (
    <div>
      <div className="row g-2 mb-3 align-items-end">
        <div className="col-12 col-md-4">
          <label className="form-label small mb-1">Pedido (número)</label>
          <input type="search" inputMode="numeric" className="form-control" value={pedido}
            onChange={(e) => { setPedido(e.target.value); setPagina(0); }} />
        </div>
        {filtro && (
          <div className="col-12 col-md-8">
            <span className="badge text-bg-info text-dark text-wrap me-2">Filtro: {filtro.nome}</span>
            <button className="btn btn-sm btn-outline-secondary" onClick={onLimparFiltro}>Limpar filtro</button>
          </div>
        )}
      </div>

      <ErroAlert erro={erro} />

      {loading ? (
        <div className="text-center text-muted py-5">Carregando movimentações...</div>
      ) : lista.length === 0 ? (
        <div className="text-center text-muted py-5">Nenhuma movimentação encontrada.</div>
      ) : (
        <>
          <div className="card d-none d-md-block">
            <div className="table-responsive">
              <table className="table align-middle mb-0">
                <thead>
                  <tr>
                    <th>Data</th><th>Tipo</th><th>Produto</th><th>Pedido</th>
                    <th className="text-end">Qtd</th><th className="text-end">Saldo</th><th>Motivo</th><th>Usuário</th>
                  </tr>
                </thead>
                <tbody>
                  {lista.map((m) => (
                    <tr key={m.id}>
                      <td>{fmtDateTime(m.criadoEm)}</td>
                      <td><StatusBadge tipo="movimentacao" valor={m.tipo} /></td>
                      <td>{m.variacao ? `${m.produto} — ${m.variacao}` : m.produto}</td>
                      <td>{m.pedidoId ?? "—"}</td>
                      <td className="text-end">{sinal(m.quantidade)}</td>
                      <td className="text-end">{m.saldoAnterior} → {m.saldoPosterior}</td>
                      <td>{m.motivo || "—"}</td>
                      <td>{m.usuario || "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <div className="d-md-none d-grid gap-2">
            {lista.map((m) => (
              <div key={m.id} className="card">
                <div className="card-body py-2">
                  <div className="d-flex justify-content-between gap-2">
                    <StatusBadge tipo="movimentacao" valor={m.tipo} />
                    <strong>{sinal(m.quantidade)} un.</strong>
                  </div>
                  <div>{m.variacao ? `${m.produto} — ${m.variacao}` : m.produto}</div>
                  <div className="small">Saldo: {m.saldoAnterior} → {m.saldoPosterior}{m.pedidoId ? ` · Pedido #${m.pedidoId}` : ""}</div>
                  {m.motivo && <div className="small">{m.motivo}</div>}
                  <div className="small text-muted">{fmtDateTime(m.criadoEm)}{m.usuario ? ` · ${m.usuario}` : ""}</div>
                </div>
              </div>
            ))}
          </div>
          {totalPaginas > 1 && (
            <div className="d-flex justify-content-between align-items-center mt-3 gap-2">
              <button className="btn btn-outline-secondary" disabled={pagina <= 0} onClick={() => setPagina((p) => p - 1)}>
                Anterior
              </button>
              <span className="small text-muted">Página {pagina + 1} de {totalPaginas}</span>
              <button className="btn btn-outline-secondary" disabled={pagina + 1 >= totalPaginas}
                onClick={() => setPagina((p) => p + 1)}>
                Próxima
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

export default function EstoquePage() {
  const { user } = useAuth();
  const gestor = isGestor(user);
  const [aba, setAba] = useState("saldos");
  const [saldos, setSaldos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState(null);
  const [apenasAlertas, setApenasAlertas] = useState(false);
  const [busca, setBusca] = useState("");
  const [ajuste, setAjuste] = useState(null);
  const [filtroMov, setFiltroMov] = useState(null);

  const carregar = useCallback(async () => {
    setLoading(true);
    setErro(null);
    try {
      const d = await estoqueApi.saldos({ apenasAlertas });
      setSaldos(Array.isArray(d) ? d : []);
    } catch (err) {
      setErro(err);
    } finally {
      setLoading(false);
    }
  }, [apenasAlertas]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  const filtrados = useMemo(() => {
    return saldos.filter((s) => casaBusca(busca, nomeItem(s), s.sku));
  }, [saldos, busca]);

  function verMovimentacoes(s) {
    setFiltroMov({ produtoId: s.produtoId, variacaoId: s.variacaoId ?? null, nome: nomeItem(s) });
    setAba("movimentacoes");
  }

  function renderAcoes(s, bloco) {
    return (
      <div className={bloco ? "d-grid d-sm-flex gap-2 mt-2" : "d-flex gap-1 justify-content-end"}>
        <button className="btn btn-sm btn-outline-secondary" onClick={() => verMovimentacoes(s)}>Movimentações</button>
        {gestor && (
          <button className="btn btn-sm btn-outline-primary" onClick={() => setAjuste(s)}>Contar/ajustar</button>
        )}
      </div>
    );
  }

  return (
    <div>
      <h3 className="mb-3">Estoque</h3>

      <ul className="nav nav-tabs mb-3">
        <li className="nav-item">
          <button className={`nav-link ${aba === "saldos" ? "active" : ""}`} onClick={() => setAba("saldos")}>Saldos</button>
        </li>
        <li className="nav-item">
          <button className={`nav-link ${aba === "movimentacoes" ? "active" : ""}`} onClick={() => setAba("movimentacoes")}>
            Movimentações
          </button>
        </li>
      </ul>

      {aba === "movimentacoes" ? (
        <Movimentacoes filtro={filtroMov} onLimparFiltro={() => setFiltroMov(null)} />
      ) : (
        <>
          <div className="row g-2 mb-3 align-items-center">
            <div className="col-12 col-md-6">
              <FiltroAutocomplete
                value={busca}
                onChange={setBusca}
                itens={saldos}
                getRotulo={nomeItem}
                getDetalhe={(s) => (s.sku ? `SKU ${s.sku}` : "Sem SKU")}
                getTextoBusca={(s) => [nomeItem(s), s.sku]}
                getChave={(s, i) => `${s.produtoId}-${s.variacaoId ?? "p"}-${i}`}
                placeholder="Buscar por nome ou SKU"
              />
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
            <SemResultados busca={busca} vazio="Nenhum item encontrado." />
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
                        <th />
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
                          <td>{renderAcoes(s, false)}</td>
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
                      {renderAcoes(s, true)}
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </>
      )}

      {ajuste && (
        <AjusteModal s={ajuste} onClose={() => setAjuste(null)}
          onAjustado={() => { setAjuste(null); carregar(); }} />
      )}
    </div>
  );
}
