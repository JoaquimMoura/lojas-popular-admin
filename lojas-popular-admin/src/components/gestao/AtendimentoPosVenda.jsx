import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import { vendasApi } from "../../services/vendasApi";
import { productsApi } from "../../services/productsApi";
import StatusBadge from "./StatusBadge";
import FormModal from "./FormModal";
import { Secao } from "./Secao";
import { ROTULOS, fmtDate, fmtDateTime, sinal } from "../../utils/format";

function descVariacao(v) {
  return [v.cor, v.tamanho].filter(Boolean).join(" / ") || v.sku || `Variação ${v.id}`;
}

function AbrirOcorrenciaModal({ venda, onClose, onCriada }) {
  const itens = venda.itens ?? [];
  const [tipo, setTipo] = useState("ASSISTENCIA");
  const [descricao, setDescricao] = useState("");
  const [itemId, setItemId] = useState("");
  const [quantidade, setQuantidade] = useState("1");
  const [busca, setBusca] = useState("");
  const [resultados, setResultados] = useState([]);
  const [buscando, setBuscando] = useState(false);
  const [produto, setProduto] = useState(null);
  const [variacaoId, setVariacaoId] = useState("");
  const [qtdTroca, setQtdTroca] = useState("1");

  const troca = tipo === "TROCA";

  useEffect(() => {
    const t = busca.trim();
    if (!troca || t.length < 2) {
      setResultados([]);
      return undefined;
    }
    let ativo = true;
    setBuscando(true);
    const timer = setTimeout(() => {
      productsApi
        .list({ page: 0, size: 10, nome: t })
        .then((r) => {
          const c = r.data?.content ?? r.data;
          if (ativo) setResultados(Array.isArray(c) ? c : []);
        })
        .catch(() => ativo && setResultados([]))
        .finally(() => ativo && setBuscando(false));
    }, 400);
    return () => {
      ativo = false;
      clearTimeout(timer);
    };
  }, [busca, troca]);

  const variacoes = produto?.variacoes ?? [];
  const qtd = Number(quantidade);
  const qtdT = Number(qtdTroca);
  const exigeItem = tipo !== "ASSISTENCIA";
  const invalido =
    descricao.trim() === "" ||
    (exigeItem && (!itemId || quantidade === "")) ||
    (quantidade !== "" && (!Number.isInteger(qtd) || qtd < 1)) ||
    (troca &&
      (!produto || (variacoes.length > 0 && !variacaoId) || !Number.isInteger(qtdT) || qtdT < 1));

  async function enviar() {
    const body = {
      tipo,
      descricao: descricao.trim(),
      itemId: itemId ? Number(itemId) : null,
      quantidade: quantidade === "" ? null : qtd,
      troca: troca
        ? { produtoId: produto.id, variacaoId: variacaoId ? Number(variacaoId) : null, quantidade: qtdT }
        : null,
    };
    const criada = await vendasApi.abrirOcorrencia(venda.id, body);
    toast.success("Ocorrência aberta.");
    onCriada(criada);
  }

  return (
    <FormModal titulo="Abrir ocorrência de pós-venda" submitLabel="Abrir ocorrência" submitDisabled={invalido}
      onClose={onClose} onSubmit={enviar}>
      <div className="row g-3">
        <div className="col-12 col-sm-6">
          <label className="form-label">Tipo <span className="text-danger">*</span></label>
          <select className="form-select" value={tipo} onChange={(e) => setTipo(e.target.value)}>
            {Object.entries(ROTULOS.tipoOcorrencia).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
        </div>
        <div className="col-12">
          <label className="form-label">Descrição <span className="text-danger">*</span></label>
          <textarea className="form-control" rows={3} maxLength={500} value={descricao}
            onChange={(e) => setDescricao(e.target.value)} />
        </div>
        <div className="col-12 col-sm-8">
          <label className="form-label">Item da venda{exigeItem && <span className="text-danger"> *</span>}</label>
          <select className="form-select" value={itemId} onChange={(e) => setItemId(e.target.value)}>
            <option value="">— não se aplica a um item —</option>
            {itens.map((i) => (
              <option key={i.id} value={i.id}>{i.descricao} (vendido: {i.quantidade})</option>
            ))}
          </select>
        </div>
        <div className="col-12 col-sm-4">
          <label className="form-label">Quantidade{exigeItem && <span className="text-danger"> *</span>}</label>
          <input type="number" min="1" className="form-control" value={quantidade}
            onChange={(e) => setQuantidade(e.target.value)} />
        </div>

        {troca && (
          <div className="col-12">
            <div className="border rounded p-2">
              <div className="fw-semibold mb-2">Produto da troca</div>
              {produto ? (
                <div className="d-flex justify-content-between align-items-center gap-2 mb-2">
                  <span>{produto.nome}</span>
                  <button type="button" className="btn btn-sm btn-outline-secondary"
                    onClick={() => { setProduto(null); setVariacaoId(""); }}>
                    Trocar
                  </button>
                </div>
              ) : (
                <>
                  <input type="search" className="form-control" placeholder="Buscar produto por nome"
                    value={busca} onChange={(e) => setBusca(e.target.value)} />
                  {buscando && <div className="small text-muted mt-1">Buscando...</div>}
                  {resultados.length > 0 && (
                    <div className="list-group mt-2">
                      {resultados.map((p) => (
                        <button type="button" key={p.id} className="list-group-item list-group-item-action"
                          onClick={() => { setProduto(p); setVariacaoId(""); setResultados([]); }}>
                          {p.nome}
                        </button>
                      ))}
                    </div>
                  )}
                </>
              )}
              {produto && (
                <div className="row g-2">
                  {variacoes.length > 0 && (
                    <div className="col-12 col-sm-8">
                      <label className="form-label">Variação <span className="text-danger">*</span></label>
                      <select className="form-select" value={variacaoId} onChange={(e) => setVariacaoId(e.target.value)}>
                        <option value="">— selecione —</option>
                        {variacoes.map((v) => (
                          <option key={v.id} value={v.id}>{descVariacao(v)}</option>
                        ))}
                      </select>
                    </div>
                  )}
                  <div className="col-12 col-sm-4">
                    <label className="form-label">Quantidade da troca</label>
                    <input type="number" min="1" className="form-control" value={qtdTroca}
                      onChange={(e) => setQtdTroca(e.target.value)} />
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </FormModal>
  );
}

export function OcorrenciasSecao({ venda, onNovaOcorrencia, gestor }) {
  const [aberto, setAberto] = useState(false);
  const acoes = venda.acoes ?? {};
  const lista = venda.ocorrencias ?? [];
  const algumaFinanceira = lista.some((o) => o.tipo !== "ASSISTENCIA");

  return (
    <Secao titulo="Ocorrências de pós-venda">
      {lista.length === 0 ? (
        <div className="text-muted">Nenhuma ocorrência registrada.</div>
      ) : (
        <div className="d-grid gap-2">
          {lista.map((o) => (
            <div key={o.id} className="border rounded p-2">
              <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
                <span>
                  <StatusBadge tipo="tipoOcorrencia" valor={o.tipo} className="me-1" />
                  <StatusBadge tipo="ocorrencia" valor={o.status} />
                  {o.situacaoFinanceira && (
                    <StatusBadge tipo="situacaoFinanceira" valor={o.situacaoFinanceira} className="ms-1" />
                  )}
                </span>
                {gestor ? (
                  <Link to={`/gestao/pos-venda/${o.id}`} className="btn btn-sm btn-outline-primary">
                    Ocorrência #{o.id}
                  </Link>
                ) : (
                  <span className="small text-muted">#{o.id}</span>
                )}
              </div>
              <div className="mt-1">{o.descricao}</div>
              <div className="small text-muted">
                {o.item ? `${o.item}${o.quantidade ? ` (${o.quantidade} un.)` : ""} · ` : ""}
                {fmtDateTime(o.criadaEm)}
              </div>
            </div>
          ))}
        </div>
      )}
      {algumaFinanceira && (
        <div className="alert alert-warning mt-3 mb-0">
          {lista.find((o) => o.bloqueios?.solucaoFinanceira)?.bloqueios.solucaoFinanceira}
        </div>
      )}
      {gestor && acoes.podeAbrirOcorrencia && (
        <div className="d-grid mt-3">
          <button className="btn btn-primary" onClick={() => setAberto(true)}>Abrir ocorrência</button>
        </div>
      )}
      {aberto && (
        <AbrirOcorrenciaModal venda={venda} onClose={() => setAberto(false)}
          onCriada={(o) => { setAberto(false); onNovaOcorrencia(o); }} />
      )}
    </Secao>
  );
}

export function EncomendasSecao({ venda, gestor }) {
  const lista = venda.encomendas ?? [];
  if (lista.length === 0) return null;
  return (
    <Secao titulo="Encomendas">
      <div className="d-grid gap-2">
        {lista.map((e) => (
          <div key={e.id} className={`border rounded p-2 ${e.atrasada ? "border-danger" : ""}`}>
            <div className="d-flex flex-wrap justify-content-between gap-2">
              <strong>{e.descricao} <span className="fw-normal">({e.quantidade} un.)</span></strong>
              <span>
                <StatusBadge tipo="encomenda" valor={e.status} />
                {e.atrasada && <span className="badge text-bg-danger ms-1">Atrasada</span>}
              </span>
            </div>
            <div className="small">Fornecedor: {e.fornecedor || "—"} · Referência: {e.referenciaFornecedor || "—"}</div>
            <div className={`small ${e.atrasada ? "text-danger fw-semibold" : ""}`}>
              Previsão de chegada: {e.previsaoChegada ? fmtDate(e.previsaoChegada) : "—"}
            </div>
            {e.prazoPadrao && <div className="small text-muted">Prazo padrão: {e.prazoPadrao}</div>}
            <div className="small">
              Reserva: {e.reserva ? <StatusBadge tipo="reserva" valor={e.reserva} /> : "—"}
              {e.recebidaEm ? ` · Recebida em ${fmtDateTime(e.recebidaEm)}` : ""}
            </div>
            <div className="small">
              Vendido: {e.quantidade} · Recebido do fornecedor: {e.quantidadeRecebida ?? 0} · Reservado ao cliente:{" "}
              {e.quantidadeReservada ?? 0} · Faltante: {e.quantidadeFaltante ?? 0}
            </div>
            {e.status === "PARCIALMENTE_RECEBIDA" && (
              <div className="small text-muted">A entrega ao cliente continua só quando tudo estiver recebido e reservado.</div>
            )}
            {e.observacao && <div className="small text-muted">{e.observacao}</div>}
          </div>
        ))}
      </div>
      {gestor && (
        <div className="mt-3">
          <Link to="/gestao/encomendas">Ir para o acompanhamento de encomendas</Link>
        </div>
      )}
    </Secao>
  );
}

export function MovimentacoesSecao({ venda }) {
  const lista = venda.movimentacoes ?? [];
  return (
    <Secao titulo="Movimentações de estoque">
      {lista.length === 0 ? (
        <div className="text-muted">Nenhuma movimentação de estoque para este pedido.</div>
      ) : (
        <>
          <div className="table-responsive d-none d-md-block">
            <table className="table align-middle mb-0">
              <thead>
                <tr>
                  <th>Data</th><th>Tipo</th><th>Produto</th>
                  <th className="text-end">Qtd</th><th className="text-end">Saldo</th>
                  <th>Motivo</th><th>Usuário</th>
                </tr>
              </thead>
              <tbody>
                {lista.map((m) => (
                  <tr key={m.id}>
                    <td>{fmtDateTime(m.criadoEm)}</td>
                    <td><StatusBadge tipo="movimentacao" valor={m.tipo} /></td>
                    <td>{m.variacao ? `${m.produto} — ${m.variacao}` : m.produto}</td>
                    <td className="text-end">{sinal(m.quantidade)}</td>
                    <td className="text-end">{m.saldoAnterior} → {m.saldoPosterior}</td>
                    <td>{m.motivo || "—"}</td>
                    <td>{m.usuario || "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="d-md-none d-grid gap-2">
            {lista.map((m) => (
              <div key={m.id} className="border rounded p-2">
                <div className="d-flex justify-content-between gap-2">
                  <StatusBadge tipo="movimentacao" valor={m.tipo} />
                  <strong>{sinal(m.quantidade)} un.</strong>
                </div>
                <div>{m.variacao ? `${m.produto} — ${m.variacao}` : m.produto}</div>
                <div className="small">Saldo: {m.saldoAnterior} → {m.saldoPosterior}</div>
                {m.motivo && <div className="small">{m.motivo}</div>}
                <div className="small text-muted">{fmtDateTime(m.criadoEm)}{m.usuario ? ` · ${m.usuario}` : ""}</div>
              </div>
            ))}
          </div>
        </>
      )}
    </Secao>
  );
}
