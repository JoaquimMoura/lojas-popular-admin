import { useState } from "react";
import { financeiroApi } from "../../../services/financeiroApi";
import ErroAlert from "../../../components/gestao/ErroAlert";
import { Secao } from "../../../components/gestao/Secao";
import { useCarga } from "../../../components/gestao/useCarga";
import { AvisosLista, Carregando, TabelaCards, Totais } from "../../../components/gestao/financeiro/Comuns";
import { CANAIS, FORMAS, LIVROS, TIPOS_CONTA, fmtDate, fmtMes, fmtMoney, fmtPercent, hojeIso, mesAtual } from "../../../utils/format";

const SECOES = [
  { id: "vendas", text: "Vendas" },
  { id: "recebimentos", text: "Recebimentos" },
  { id: "contas", text: "Contas pendentes" },
  { id: "estoque", text: "Estoque" },
  { id: "entregas", text: "Entregas" },
  { id: "comissoes", text: "Comissões" },
  { id: "metas", text: "Metas" },
];

const AGRUPAMENTOS = { VENDEDOR: "Vendedor", CANAL: "Canal", DIA: "Dia", MES: "Mês" };

const rotulo = (s) => (s ? String(s).charAt(0) + String(s).slice(1).toLowerCase().replace(/_/g, " ") : "—");

function Periodo({ de, ate, setDe, setAte, children }) {
  return (
    <div className="row g-2 align-items-end mb-3">
      <div className="col-6 col-md-3">
        <label className="form-label small mb-1">De</label>
        <input type="date" className="form-control" value={de} max={ate || undefined}
          onChange={(e) => e.target.value && setDe(e.target.value)} />
      </div>
      <div className="col-6 col-md-3">
        <label className="form-label small mb-1">Até</label>
        <input type="date" className="form-control" value={ate} min={de || undefined}
          onChange={(e) => e.target.value && setAte(e.target.value)} />
      </div>
      {children}
    </div>
  );
}

function Aviso({ texto }) {
  if (!texto) return null;
  return <div className="alert alert-warning fw-semibold" role="status">{texto}</div>;
}

function baixarCsv(nome, linhas) {
  const esc = (v) => {
    const t = v === null || v === undefined ? "" : String(v);
    return /[";\n\r]/.test(t) ? `"${t.replace(/"/g, '""')}"` : t;
  };
  const texto = linhas.map((l) => l.map(esc).join(";")).join("\r\n");
  const blob = new Blob(["﻿" + texto], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = nome;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

const num = (v) => (v === null || v === undefined ? "" : String(v).replace(".", ","));

function Estado({ c }) {
  return (
    <>
      <ErroAlert erro={c.erro} />
      {c.loading && !c.dados && <Carregando />}
    </>
  );
}

// ---------------------------------------------------------------- vendas

function chaveVenda(agrupar, k) {
  if (agrupar === "CANAL") return CANAIS[k] ?? k;
  if (agrupar === "DIA") return fmtDate(k);
  if (agrupar === "MES") return fmtMes(k);
  return k;
}

const LEGADO_VENDEDOR = "(vendedor desconhecido — venda legada)";
const LEGADO_CANAL = "LEGADO_ONLINE (canal desconhecido)";
const ehLegado = (k) => k === LEGADO_VENDEDOR || k === LEGADO_CANAL || /legad/i.test(String(k ?? ""));

function CoberturaVendas({ cob }) {
  if (!cob) return null;
  return (
    <div className="card border-warning mb-3" role="note">
      <div className="card-body">
        <h6 className="card-title">Cobertura do relatório</h6>
        {cob.observacao && <p className="mb-2">{cob.observacao}</p>}
        <ul className="mb-2 ps-3">
          <li>Vendas da gestão: <strong>{cob.vendasGestao ?? 0}</strong></li>
          <li>
            Vendas legadas incluídas: <strong>{cob.legadasIncluidas ?? 0}</strong> (total {fmtMoney(cob.totalLegadoIncluido ?? 0)})
          </li>
          <li>
            Legadas não pagas, excluídas: <strong>{cob.legadasNaoPagasExcluidas ?? 0}</strong> (total {fmtMoney(cob.totalLegadoNaoPagoExcluido ?? 0)})
          </li>
          <li>Legadas canceladas, excluídas: <strong>{cob.legadasCanceladasExcluidas ?? 0}</strong></li>
        </ul>
        <div className="small text-muted">
          Vendas legadas têm vendedor e canal desconhecidos: aparecem nas linhas marcadas como &quot;legado&quot; e não
          devem ser atribuídas a nenhum vendedor ou canal.
        </div>
      </div>
    </div>
  );
}

function Margem({ l }) {
  return l.margemCompleta
    ? <span>{fmtMoney(l.margemBrutaItens)}</span>
    : <span className="text-muted">Incompleta ({l.itensSemCusto} {l.itensSemCusto === 1 ? "item sem custo" : "itens sem custo"})</span>;
}

function VendasSecao() {
  const [de, setDe] = useState(`${mesAtual()}-01`);
  const [ate, setAte] = useState(hojeIso());
  const [agrupar, setAgrupar] = useState("VENDEDOR");
  const c = useCarga(() => financeiroApi.relVendas({ de, ate, agrupar }), [de, ate, agrupar]);
  const r = c.dados;
  const linhas = r?.linhas ?? [];

  function exportar() {
    const cab = [AGRUPAMENTOS[agrupar], "Vendas", "Total vendido", "Ticket medio", "Unidades", "Custo conhecido", "Itens sem custo", "Margem bruta dos itens"];
    const corpo = [...linhas, r.total].map((l, i) => [
      i === linhas.length ? "TOTAL" : chaveVenda(agrupar, l.chave), l.vendas, num(l.totalVendido), num(l.ticketMedio), l.unidades,
      num(l.custoConhecido), l.itensSemCusto, l.margemCompleta ? num(l.margemBrutaItens) : "Incompleta",
    ]);
    baixarCsv(`vendas_${de}_${ate}.csv`, [[r.aviso], cab, ...corpo]);
  }

  return (
    <div>
      <Periodo de={de} ate={ate} setDe={setDe} setAte={setAte}>
        <div className="col-12 col-md-3">
          <label className="form-label small mb-1">Agrupar por</label>
          <select className="form-select" value={agrupar} onChange={(e) => setAgrupar(e.target.value)}>
            {Object.entries(AGRUPAMENTOS).map(([k, t]) => <option key={k} value={k}>{t}</option>)}
          </select>
        </div>
        <div className="col-12 col-md-3 d-grid">
          <button className="btn btn-outline-secondary" disabled={!r || linhas.length === 0} onClick={exportar}>Exportar CSV</button>
        </div>
      </Periodo>
      <Estado c={c} />
      {r && (
        <>
          <Aviso texto={r.aviso} />
          <CoberturaVendas cob={r.cobertura} />
          <div className="alert alert-info small">
            Venda realizada não é dinheiro recebido, e a margem bruta dos itens não é lucro (não considera taxas, comissões nem despesas).
          </div>
          <Totais itens={[
            { rotulo: "Vendas", texto: String(r.total?.vendas ?? 0) },
            { rotulo: "Total vendido", valor: r.total?.totalVendido },
            { rotulo: "Ticket médio", valor: r.total?.ticketMedio },
            { rotulo: "Unidades", texto: String(r.total?.unidades ?? 0) },
          ]} />
          <TabelaCards
            linhas={linhas}
            chave={(l) => l.chave}
            destaque={(l) => (ehLegado(l.chave) ? "border-warning bg-warning-subtle" : "")}
            vazio="Nenhuma venda confirmada no período."
            colunas={[
              {
                titulo: AGRUPAMENTOS[agrupar],
                render: (l) => (
                  <>
                    {chaveVenda(agrupar, l.chave)}
                    {ehLegado(l.chave) && <span className="badge text-bg-warning ms-1">Legado</span>}
                  </>
                ),
              },
              { titulo: "Vendas", fim: true, render: (l) => l.vendas },
              { titulo: "Total vendido", fim: true, render: (l) => fmtMoney(l.totalVendido) },
              { titulo: "Ticket médio", fim: true, render: (l) => fmtMoney(l.ticketMedio) },
              { titulo: "Unidades", fim: true, render: (l) => l.unidades },
              { titulo: "Custo conhecido", fim: true, render: (l) => fmtMoney(l.custoConhecido) },
              { titulo: "Itens sem custo", fim: true, render: (l) => l.itensSemCusto },
              { titulo: "Margem bruta", fim: true, render: (l) => <Margem l={l} /> },
            ]}
          />
          {linhas.length > 0 && (
            <div className="border rounded p-2 mt-2 d-flex justify-content-between gap-3 fw-semibold">
              <span>Margem bruta do período</span>
              <span className="text-end"><Margem l={r.total} /></span>
            </div>
          )}
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- recebimentos

function RecebimentosSecao() {
  const [de, setDe] = useState(`${mesAtual()}-01`);
  const [ate, setAte] = useState(hojeIso());
  const c = useCarga(() => financeiroApi.relRecebimentos({ de, ate }), [de, ate]);
  const r = c.dados;
  const pag = r?.pagamentosDoCliente ?? [];
  const contas = [...new Set([...Object.keys(r?.entradaEfetivaPorConta ?? {}), ...Object.keys(r?.saidaEfetivaPorConta ?? {})])];

  function exportar() {
    const corpo = [
      ["Pagamentos do cliente"], ["Forma", "Recebimentos", "Registrado", "Estornado", "Liquido"],
      ...pag.map((l) => [FORMAS[l.chave] ?? l.chave, l.recebimentos, num(l.registrado), num(l.estornado), num(l.liquido)]),
      [], ["Entrada e saida efetiva por conta"], ["Conta", "Entrada", "Saida"],
      ...contas.map((k) => [LIVROS[k] ?? k, num(r.entradaEfetivaPorConta?.[k]), num(r.saidaEfetivaPorConta?.[k])]),
      [], ["Cartao"], ["Item", "Parcelas", "Bruto", "Taxa", "Liquido"],
      ["Previsto no periodo", r.cartaoPrevistoParcelas, num(r.cartaoPrevistoBruto), num(r.cartaoPrevistoTaxa), num(r.cartaoPrevistoLiquido)],
      ["Liquidado no periodo", r.cartaoLiquidadoParcelas, "", "", num(r.cartaoLiquidadoValor)],
    ];
    baixarCsv(`recebimentos_${de}_${ate}.csv`, [[r.aviso], ...corpo]);
  }

  return (
    <div>
      <Periodo de={de} ate={ate} setDe={setDe} setAte={setAte}>
        <div className="col-12 col-md-3 d-grid">
          <button className="btn btn-outline-secondary" disabled={!r} onClick={exportar}>Exportar CSV</button>
        </div>
      </Periodo>
      <Estado c={c} />
      {r && (
        <>
          <Aviso texto={r.aviso} />
          {r.cobertura && <div className="alert alert-warning" role="alert"><strong>Atenção:</strong> {r.cobertura}</div>}
          <Secao titulo="Pagamentos do cliente (por forma)">
            <TabelaCards
              linhas={pag}
              chave={(l) => l.chave}
              vazio="Nenhum pagamento registrado no período."
              colunas={[
                { titulo: "Forma", render: (l) => FORMAS[l.chave] ?? l.chave },
                { titulo: "Recebimentos", fim: true, render: (l) => l.recebimentos },
                { titulo: "Registrado", fim: true, render: (l) => fmtMoney(l.registrado) },
                { titulo: "Estornado", fim: true, render: (l) => fmtMoney(l.estornado) },
                { titulo: "Líquido", fim: true, render: (l) => fmtMoney(l.liquido) },
              ]}
            />
          </Secao>
          <Secao titulo="Entrada e saída efetiva por conta">
            <div className="small text-muted mb-2">Caixa (dinheiro) e banco são contas separadas e não se misturam.</div>
            <TabelaCards
              linhas={contas.map((k) => ({ id: k }))}
              vazio="Sem movimentos."
              colunas={[
                { titulo: "Conta", render: (l) => LIVROS[l.id] ?? l.id },
                { titulo: "Entrada", fim: true, render: (l) => fmtMoney(r.entradaEfetivaPorConta?.[l.id] ?? 0) },
                { titulo: "Saída", fim: true, render: (l) => fmtMoney(r.saidaEfetivaPorConta?.[l.id] ?? 0) },
              ]}
            />
          </Secao>
          <Secao titulo="Cartão">
            <h6>Previsto no período ({r.cartaoPrevistoParcelas} parcela(s))</h6>
            <Totais itens={[
              { rotulo: "Bruto", valor: r.cartaoPrevistoBruto },
              { rotulo: "Taxa", valor: r.cartaoPrevistoTaxa },
              { rotulo: "Líquido", valor: r.cartaoPrevistoLiquido },
            ]} />
            <h6>Liquidado no período ({r.cartaoLiquidadoParcelas} parcela(s))</h6>
            <Totais itens={[{ rotulo: "Valor liquidado (entrou no banco)", valor: r.cartaoLiquidadoValor }]} />
          </Secao>
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- contas pendentes

function ContasSecao() {
  const c = useCarga(() => financeiroApi.relContasPendentes(), []);
  const r = c.dados;
  return (
    <div>
      <Estado c={c} />
      {r && (
        <Secao titulo={`Contas abertas em ${fmtDate(r.referencia)}`}>
          <TabelaCards
            linhas={r.grupos ?? []}
            chave={(l) => l.tipo}
            vazio="Sem contas abertas."
            destaque={(l) => (l.vencidas > 0 ? "table-warning" : "")}
            colunas={[
              { titulo: "Tipo", render: (l) => TIPOS_CONTA[l.tipo] ?? l.tipo },
              { titulo: "Contas", fim: true, render: (l) => l.contas },
              { titulo: "Total", fim: true, render: (l) => fmtMoney(l.total) },
              { titulo: "Vencidas", fim: true, render: (l) => <span className={l.vencidas > 0 ? "text-danger fw-semibold" : ""}>{l.vencidas} · {fmtMoney(l.totalVencido)}</span> },
              { titulo: "Vencem em 7 dias", fim: true, render: (l) => `${l.vencemEm7Dias} · ${fmtMoney(l.totalEm7Dias)}` },
            ]}
          />
        </Secao>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- estoque

function EstoqueSecao() {
  const c = useCarga(() => financeiroApi.relEstoque(), []);
  const r = c.dados;
  return (
    <div>
      <Estado c={c} />
      {r && (
        <>
          <Totais itens={[
            { rotulo: "Itens", texto: String(r.itens) },
            { rotulo: "Sem saldo informado", texto: String(r.semSaldo) },
            { rotulo: "Indisponíveis", texto: String(r.indisponiveis) },
            { rotulo: "Unidades físicas", texto: String(r.unidadesFisicas) },
            { rotulo: "Unidades reservadas", texto: String(r.unidadesReservadas) },
          ]} />
          <TabelaCards
            linhas={r.saldos ?? []}
            chave={(l) => `${l.produtoId}-${l.variacaoId ?? 0}`}
            vazio="Nenhum item de estoque."
            colunas={[
              { titulo: "Produto", render: (l) => `${l.produtoNome}${l.variacaoDescricao ? ` — ${l.variacaoDescricao}` : ""}` },
              { titulo: "SKU", render: (l) => l.sku ?? "—" },
              { titulo: "Físico", fim: true, render: (l) => l.fisico ?? <span className="text-muted">Não informado</span> },
              { titulo: "Reservado", fim: true, render: (l) => l.reservado },
              { titulo: "Disponível", fim: true, render: (l) => l.disponivel ?? "—" },
              { titulo: "Alerta", render: (l) => (l.alerta ? <span className="badge text-bg-warning text-dark">{rotulo(l.alerta)}</span> : "—") },
            ]}
          />
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- entregas

function MapaContagem({ titulo, mapa }) {
  const itens = Object.entries(mapa ?? {});
  return (
    <div className="col-12 col-md-4">
      <div className="border rounded p-2 h-100">
        <div className="fw-semibold mb-1">{titulo}</div>
        {itens.length === 0 ? <div className="text-muted small">Nenhum.</div> : itens.map(([k, v]) => (
          <div key={k} className="d-flex justify-content-between gap-2"><span>{rotulo(k)}</span><strong>{v}</strong></div>
        ))}
      </div>
    </div>
  );
}

function EntregasSecao() {
  const [de, setDe] = useState(`${mesAtual()}-01`);
  const [ate, setAte] = useState(hojeIso());
  const c = useCarga(() => financeiroApi.relEntregas({ de, ate }), [de, ate]);
  const r = c.dados;
  const atrasadas = r?.atrasadas ?? [];
  return (
    <div>
      <Periodo de={de} ate={ate} setDe={setDe} setAte={setAte} />
      <Estado c={c} />
      {r && (
        <>
          {atrasadas.length > 0 && (
            <div className="alert alert-danger" role="alert">
              <strong>{atrasadas.length} entrega(s) atrasada(s)</strong> (data prevista anterior a hoje e ainda não concluída).
            </div>
          )}
          <div className="row g-2 mb-3">
            <MapaContagem titulo="Pedidos por status de entrega" mapa={r.pedidosPorStatusEntrega} />
            <MapaContagem titulo="Pedidos por status de montagem" mapa={r.pedidosPorStatusMontagem} />
            <MapaContagem titulo="Agendadas no período" mapa={r.agendadasNoPeriodoPorStatus} />
          </div>
          <Totais itens={[{ rotulo: "Concluídas no período", texto: String(r.concluidasNoPeriodo) }]} />
          <Secao titulo="Entregas atrasadas">
            <TabelaCards
              linhas={atrasadas}
              chave={(l) => `${l.pedidoId}-${l.dataPrevista}`}
              vazio="Nenhuma entrega atrasada."
              destaque={() => "table-danger"}
              colunas={[
                { titulo: "Pedido", render: (l) => `#${l.pedidoId}` },
                { titulo: "Data prevista", render: (l) => fmtDate(l.dataPrevista) },
                { titulo: "Equipe", render: (l) => l.equipe ?? "—" },
                { titulo: "Situação", render: (l) => rotulo(l.status) },
              ]}
            />
          </Secao>
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- comissões

function ComissoesSecao() {
  const [de, setDe] = useState(`${mesAtual()}-01`);
  const [ate, setAte] = useState(hojeIso());
  const c = useCarga(() => financeiroApi.relComissoes({ de, ate }), [de, ate]);
  const r = c.dados;
  return (
    <div>
      <Periodo de={de} ate={ate} setDe={setDe} setAte={setAte} />
      <Estado c={c} />
      {r && (
        <>
          <AvisosLista itens={r.avisos} titulo="Decisões pendentes (D01/D02)" />
          {(!r.percentualDefinido || !r.aquisicaoDefinida) && (
            <div className="small text-muted mb-2">
              {!r.percentualDefinido && "Percentual de comissão não definido (D01). "}
              {!r.aquisicaoDefinida && "Regra de aquisição não definida (D02)."}
            </div>
          )}
          <TabelaCards
            linhas={r.linhas ?? []}
            chave={(l) => l.vendedorId}
            vazio="Nenhuma comissão no período."
            colunas={[
              { titulo: "Vendedor", render: (l) => l.vendedor ?? "—" },
              { titulo: "Previstas", fim: true, render: (l) => fmtMoney(l.previstas) },
              { titulo: "Devidas", fim: true, render: (l) => fmtMoney(l.devidas) },
              { titulo: "Em conta", fim: true, render: (l) => fmtMoney(l.emConta) },
              { titulo: "Pagas", fim: true, render: (l) => fmtMoney(l.pagas) },
              { titulo: "Reversões", fim: true, render: (l) => fmtMoney(l.reversoes) },
            ]}
          />
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- metas

function MetasSecao() {
  const [mes, setMes] = useState(mesAtual());
  const c = useCarga(() => financeiroApi.relMetas(mes), [mes]);
  const r = c.dados;
  return (
    <div>
      <div className="mb-3" style={{ maxWidth: 240 }}>
        <label className="form-label small mb-1">Mês</label>
        <input type="month" className="form-control" value={mes} onChange={(e) => e.target.value && setMes(e.target.value)} />
      </div>
      <Estado c={c} />
      {r && (
        <>
          <Totais itens={[
            { rotulo: "Total das metas", valor: r.totalMeta },
            { rotulo: "Total vendido", valor: r.totalVendido },
          ]} />
          {(r.metas ?? []).some((m) => m.provisorio) && (
            <div className="alert alert-warning">Atingimento provisório (D09): a política de devoluções na meta ainda não foi definida.</div>
          )}
          <TabelaCards
            linhas={r.metas ?? []}
            chave={(l) => l.vendedorId}
            vazio="Nenhuma meta no mês."
            colunas={[
              { titulo: "Vendedor", render: (l) => l.vendedor ?? "—" },
              { titulo: "Meta", fim: true, render: (l) => (l.meta == null ? <span className="text-muted">Sem meta</span> : fmtMoney(l.meta)) },
              { titulo: "Vendido", fim: true, render: (l) => fmtMoney(l.vendido) },
              { titulo: "Restituído", fim: true, render: (l) => fmtMoney(l.restituidoNoMes) },
              { titulo: "Atingimento", fim: true, render: (l) => `${fmtPercent(l.atingimentoPercentual)}${l.provisorio ? " (provisório)" : ""}` },
            ]}
          />
        </>
      )}
    </div>
  );
}

const CORPOS = {
  vendas: VendasSecao, recebimentos: RecebimentosSecao, contas: ContasSecao, estoque: EstoqueSecao,
  entregas: EntregasSecao, comissoes: ComissoesSecao, metas: MetasSecao,
};

export default function RelatoriosPage() {
  const [secao, setSecao] = useState("vendas");
  const Corpo = CORPOS[secao];
  return (
    <div>
      <nav className="nav nav-tabs flex-nowrap overflow-auto mb-3" aria-label="Relatórios">
        {SECOES.map((s) => (
          <button key={s.id} type="button" className={`nav-link text-nowrap ${secao === s.id ? "active" : ""}`}
            onClick={() => setSecao(s.id)}>{s.text}</button>
        ))}
      </nav>
      <Corpo key={secao} />
    </div>
  );
}
