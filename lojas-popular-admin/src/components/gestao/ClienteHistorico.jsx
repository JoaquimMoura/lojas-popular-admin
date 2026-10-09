import { useState } from "react";
import { Link } from "react-router-dom";
import { clientesApi } from "../../services/clientesApi";
import { useCarga } from "./useCarga";
import ErroAlert from "./ErroAlert";
import StatusBadge from "./StatusBadge";
import MoneyText from "./MoneyText";
import { CANAIS, MODALIDADES_ITEM, ROTULOS, fmtDate, fmtDateTime, fmtMoney } from "../../utils/format";

const TAMANHO = 10;
const SITUACOES = ["RASCUNHO", "AGUARDANDO_APROVACAO", "CONFIRMADA", "CANCELADA", "LEGADO"];

function Indicador({ titulo, valor, dica, destaque }) {
  return (
    <div className="col-6 col-md-4 col-xl-3">
      <div className={`border rounded p-2 h-100 gestao-indicador${destaque ? " destaque" : ""}`} title={dica}>
        <div className="small text-muted">{titulo}</div>
        <div className="fw-bold fs-5">{valor}</div>
      </div>
    </div>
  );
}

function Resumo({ clienteId }) {
  const { dados: r, loading, erro, setErro } = useCarga(() => clientesApi.resumo(clienteId), [clienteId]);
  if (loading && !r) return <div className="text-muted py-3">Carregando resumo...</div>;
  if (!r) return <ErroAlert erro={erro} onClose={() => setErro(null)} />;
  return (
    <div className="mb-3">
      <ErroAlert erro={erro} onClose={() => setErro(null)} />
      <div className="row g-2">
        <Indicador titulo="Compras" valor={r.compras} />
        <Indicador titulo="Valor comprado" valor={<MoneyText value={r.valorComprado} />} destaque
          dica="Soma das vendas válidas. Não é dinheiro recebido." />
        <Indicador titulo="Ticket médio" valor={<MoneyText value={r.ticketMedio} />} />
        <Indicador titulo="Itens devolvidos (valor)" valor={<MoneyText value={r.valorItensDevolvidos} />}
          dica="Valor dos itens devolvidos fisicamente. Informativo: não é dinheiro restituído." />
        <Indicador titulo="Primeira compra" valor={r.primeiraCompra ? fmtDate(r.primeiraCompra) : "—"} />
        <Indicador titulo="Última compra" valor={r.ultimaCompra ? fmtDate(r.ultimaCompra) : "—"} />
        <Indicador titulo="Entregas pendentes" valor={r.entregasPendentes} />
        <Indicador titulo="Montagens pendentes" valor={r.montagensPendentes} />
        <Indicador titulo="Canceladas" valor={r.canceladas} />
        <Indicador titulo="Trocas" valor={r.trocas} />
        <Indicador titulo="Devoluções" valor={r.devolucoes} />
        <Indicador titulo="Assistências" valor={r.assistencias} />
      </div>
      <div className="small text-muted mt-2">
        {r.observacao ?? "Valor comprado não é dinheiro recebido."}
      </div>
    </div>
  );
}

function CompraCard({ c, clienteNome }) {
  const [aberta, setAberta] = useState(false);
  const itens = c.itens ?? [];
  const ocorrencias = c.ocorrencias ?? [];
  const nomeDiferente =
    c.clienteNaVenda && clienteNome && c.clienteNaVenda.trim().toLowerCase() !== clienteNome.trim().toLowerCase();
  const resumoItens = itens.length === 0
    ? "Sem itens"
    : `${itens[0].descricao}${itens.length > 1 ? ` e mais ${itens.length - 1}` : ""}`;
  return (
    <div className={`card gestao-compra${c.statusComercial === "CANCELADA" ? " cancelada" : ""}`}>
      <div className="card-body">
        <button type="button" className="gestao-compra-topo" aria-expanded={aberta} onClick={() => setAberta((v) => !v)}>
          <span className="d-flex justify-content-between gap-2">
            <strong>#{c.pedidoId} · {fmtDate(c.data)}</strong>
            <strong><MoneyText value={c.total} /></strong>
          </span>
          <span className="d-block small text-muted text-truncate">{resumoItens}</span>
          <span className="d-flex flex-wrap gap-1 mt-1 align-items-center">
            <StatusBadge tipo="comercial" valor={c.statusComercial} />
            <StatusBadge tipo="pagamento" valor={c.statusPagamento} />
            <StatusBadge tipo="entrega" valor={c.statusEntrega} />
            <StatusBadge tipo="montagem" valor={c.statusMontagem} />
            {ocorrencias.length > 0 && (
              <span className="badge text-bg-warning text-dark">{ocorrencias.length} ocorrência(s)</span>
            )}
            {c.legado && <span className="badge text-bg-secondary">Legado</span>}
            <span className="ms-auto small text-primary">{aberta ? "Ocultar detalhes" : "Ver detalhes"}</span>
          </span>
        </button>

        {aberta && (
          <div className="mt-3 pt-3 border-top">
            <div className="small text-muted mb-2">
              Vendedor: {c.vendedor ?? "—"} · Canal: {CANAIS[c.canal] ?? c.canal ?? "—"} · {fmtDateTime(c.data)}
            </div>
            {!c.contaNosIndicadores && (
              <div className="small text-muted mb-2">Esta venda não conta nos indicadores do cliente.</div>
            )}
            {nomeDiferente && (
              <div className="small text-muted mb-2">
                Nome na venda: {c.clienteNaVenda} (o cadastro atual está com outro nome).
              </div>
            )}
            <div className="d-grid gap-2 mb-2">
              {itens.map((i) => (
                <div key={i.id} className="d-flex justify-content-between gap-2 border-bottom pb-1">
                  <div>
                    <div>{i.descricao}</div>
                    <div className="small text-muted">
                      {i.quantidade} x {fmtMoney(i.precoUnitario)}
                      {i.modalidade ? ` · ${MODALIDADES_ITEM[i.modalidade] ?? i.modalidade}` : ""}
                    </div>
                  </div>
                  <strong className="text-nowrap"><MoneyText value={i.total} /></strong>
                </div>
              ))}
            </div>
            <div className="d-flex justify-content-between small"><span>Subtotal</span><MoneyText value={c.subtotal} /></div>
            <div className="d-flex justify-content-between small"><span>Desconto</span><MoneyText value={c.desconto} /></div>
            <div className="d-flex justify-content-between"><strong>Total</strong><strong><MoneyText value={c.total} /></strong></div>

            {ocorrencias.length > 0 && (
              <div className="mt-3">
                <div className="fw-semibold small mb-1">Trocas, devoluções e assistências</div>
                <ul className="list-unstyled mb-0">
                  {ocorrencias.map((o) => (
                    <li key={o.id} className="small py-1">
                      <StatusBadge tipo="tipoOcorrencia" valor={o.tipo} className="me-1" />
                      <StatusBadge tipo="ocorrencia" valor={o.status} className="me-1" />
                      {o.item ?? "Item"}{o.quantidade ? ` (${o.quantidade} un.)` : ""}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            <div className="d-grid d-sm-flex mt-3">
              <Link className="btn btn-outline-primary btn-sm" to={`/gestao/pedidos/${c.pedidoId}`}>
                Abrir pedido
              </Link>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default function ClienteHistorico({ cliente }) {
  const clienteId = cliente.id;
  const [filtros, setFiltros] = useState({ de: "", ate: "", situacao: "", produto: "" });
  const [aplicados, setAplicados] = useState(filtros);
  const [pagina, setPagina] = useState(0);

  const { dados, loading, erro, setErro } = useCarga(
    () =>
      clientesApi.compras(clienteId, {
        de: aplicados.de || undefined,
        ate: aplicados.ate || undefined,
        situacao: aplicados.situacao || undefined,
        produto: aplicados.produto.trim() || undefined,
        pagina,
        tamanho: TAMANHO,
      }),
    [clienteId, aplicados, pagina],
  );

  function aplicar(e) {
    e.preventDefault();
    setPagina(0);
    setAplicados(filtros);
  }
  function limpar() {
    const v = { de: "", ate: "", situacao: "", produto: "" };
    setFiltros(v);
    setAplicados(v);
    setPagina(0);
  }
  const set = (k) => (e) => setFiltros((f) => ({ ...f, [k]: e.target.value }));
  const filtrado = Object.values(aplicados).some(Boolean);
  const lista = dados?.itens ?? [];

  return (
    <div>
      {dados?.escopo === "SOMENTE_SUAS_VENDAS" && (
        <div className="alert alert-warning py-2" role="status">
          <strong>Mostrando apenas as compras feitas por você.</strong> O proprietário define se vendedores veem o
          histórico completo do cliente.
        </div>
      )}
      {dados?.escopo === "COMPLETO" && (
        <div className="small text-muted mb-2">Mostrando o histórico completo do cliente.</div>
      )}

      <Resumo clienteId={clienteId} />

      <form className="card mb-3" onSubmit={aplicar}>
        <div className="card-body">
          <div className="row g-2 align-items-end">
            <div className="col-6 col-md-3 col-xl-2">
              <label className="form-label small mb-1">De</label>
              <input type="date" className="form-control" value={filtros.de} onChange={set("de")} />
            </div>
            <div className="col-6 col-md-3 col-xl-2">
              <label className="form-label small mb-1">Até</label>
              <input type="date" className="form-control" value={filtros.ate} onChange={set("ate")} />
            </div>
            <div className="col-12 col-md-6 col-xl-3">
              <label className="form-label small mb-1">Situação da venda</label>
              <select className="form-select" value={filtros.situacao} onChange={set("situacao")}>
                <option value="">Todas</option>
                {SITUACOES.map((s) => (
                  <option key={s} value={s}>{ROTULOS.comercial[s] ?? s}</option>
                ))}
              </select>
            </div>
            <div className="col-12 col-md-6 col-xl-3">
              <label className="form-label small mb-1">Produto</label>
              <input type="search" className="form-control" placeholder="Nome ou SKU do produto"
                value={filtros.produto} onChange={set("produto")} />
            </div>
            <div className="col-12 col-xl-2 d-grid d-sm-flex gap-2">
              <button className="btn btn-primary flex-fill" type="submit">Filtrar</button>
              {filtrado && <button className="btn btn-outline-secondary" type="button" onClick={limpar}>Limpar</button>}
            </div>
          </div>
        </div>
      </form>

      <ErroAlert erro={erro} onClose={() => setErro(null)} />

      {loading && !dados ? (
        <div className="text-center text-muted py-4">Carregando compras...</div>
      ) : lista.length === 0 ? (
        !erro && (
          <div className="text-center text-muted py-4">
            {filtrado
              ? "Nenhuma compra encontrada com esses filtros."
              : "Este cliente ainda não tem compras visíveis para você."}
          </div>
        )
      ) : (
        <>
          <div className="d-grid gap-2">
            {lista.map((c) => <CompraCard key={c.pedidoId} c={c} clienteNome={cliente.nome} />)}
          </div>
          <div className="d-flex justify-content-between align-items-center mt-3 gap-2">
            <button className="btn btn-outline-secondary" disabled={pagina <= 0 || loading}
              onClick={() => setPagina((p) => p - 1)}>
              Anterior
            </button>
            <span className="small text-muted text-center">
              Página {(dados?.pagina ?? 0) + 1} de {Math.max(dados?.totalPaginas ?? 1, 1)} · {dados?.total ?? 0} compra(s)
            </span>
            <button className="btn btn-outline-secondary"
              disabled={loading || pagina + 1 >= (dados?.totalPaginas ?? 1)} onClick={() => setPagina((p) => p + 1)}>
              Próxima
            </button>
          </div>
        </>
      )}
    </div>
  );
}
