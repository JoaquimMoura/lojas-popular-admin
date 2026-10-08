import { useState } from "react";
import { useFinanceiroPermissoes } from "../../../components/gestao/financeiro/useFinanceiroPermissoes";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import { financeiroApi } from "../../../services/financeiroApi";
import { useAuth } from "../../../context/AuthContext";
import ErroAlert from "../../../components/gestao/ErroAlert";
import FormModal from "../../../components/gestao/FormModal";
import StatusBadge from "../../../components/gestao/StatusBadge";
import { useChave } from "../../../components/gestao/useChave";
import { useCarga } from "../../../components/gestao/useCarga";
import MotivoModal from "../../../components/gestao/financeiro/MotivoModal";
import { NotaTresConceitos } from "../../../components/gestao/financeiro/PagamentoSecao";
import { CampoValor, Carregando, Pendente, TabelaCards, Totais } from "../../../components/gestao/financeiro/Comuns";
import { ROTULOS, fmtDate, fmtMoney, fmtPercent, hojeIso, isAdmin } from "../../../utils/format";

function LiquidarModal({ r, onClose, onFeito }) {
  const [data, setData] = useState("");
  const [valor, setValor] = useState("");
  const chave = useChave();
  const dif = valor !== "" ? Math.round((Number(valor) - Number(r.valorLiquido)) * 100) / 100 : null;
  return (
    <FormModal titulo={`Liquidar parcela ${r.parcela}/${r.totalParcelas} — pedido #${r.pedidoId}`} submitLabel="Liquidar" variant="success"
      size="md" submitDisabled={valor !== "" && !(Number(valor) > 0)} onClose={onClose}
      onSubmit={async () => {
        const body = { dataLiquidacao: data || null, valorLiquidado: valor === "" ? null : Number(valor) };
        await financeiroApi.liquidar(r.id, body, chave.obter(`${r.id}:${data}:${valor}`));
        chave.limpar();
        toast.success("Parcela liquidada: o valor entrou no banco.");
        onFeito();
      }}>
      <div className="alert alert-info">
        A liquidação registra a <strong>entrada efetiva no banco</strong>. Líquido previsto: {fmtMoney(r.valorLiquido)}
        {" "}(bruto {fmtMoney(r.valorBruto)} − taxa {fmtMoney(r.valorTaxa)}), previsão {fmtDate(r.dataPrevista)}.
      </div>
      <div className="row g-3">
        <div className="col-12 col-sm-6">
          <label className="form-label">Data da liquidação</label>
          <input type="date" className="form-control" max={hojeIso()} value={data} onChange={(e) => setData(e.target.value)} autoFocus />
          <div className="form-text">Vazio = hoje.</div>
        </div>
        <div className="col-12 col-sm-6">
          <CampoValor label="Valor liquidado (se diferente do previsto)" value={valor} onChange={setValor}
            ajuda="Vazio = o líquido previsto." />
        </div>
      </div>
      {dif !== null && (
        <div className={`mt-2 ${dif !== 0 ? "text-danger fw-semibold" : ""}`} aria-live="polite">
          Diferença em relação ao previsto: {fmtMoney(dif)}
        </div>
      )}
    </FormModal>
  );
}

function Recebiveis() {
  const { perm } = useFinanceiroPermissoes();
  const [status, setStatus] = useState("");
  const [operadora, setOperadora] = useState("");
  const [de, setDe] = useState("");
  const [ate, setAte] = useState("");
  const [modal, setModal] = useState(null);
  const { dados, loading, erro, recarregar } = useCarga(
    () => financeiroApi.recebiveis({ status, operadora: operadora.trim(), de, ate }),
    [status, operadora, de, ate],
  );
  const lista = Array.isArray(dados) ? dados : [];
  const ativos = lista.filter((r) => r.status !== "CANCELADO");
  const soma = (k) => ativos.reduce((s, r) => s + Number(r[k] ?? 0), 0);
  const vencidos = lista.filter((r) => r.vencido && r.status === "PREVISTO");
  const feito = () => {
    setModal(null);
    recarregar();
  };
  return (
    <div>
      <NotaTresConceitos />
      <div className="row g-2 mb-3 align-items-end">
        <div className="col-6 col-md-3">
          <label className="form-label small mb-1">Situação</label>
          <select className="form-select" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Todas</option>
            {Object.entries(ROTULOS.recebivel).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div className="col-6 col-md-3">
          <label className="form-label small mb-1">Operadora</label>
          <input className="form-control" value={operadora} onChange={(e) => setOperadora(e.target.value)} />
        </div>
        <div className="col-6 col-md-3">
          <label className="form-label small mb-1">Previsão de</label>
          <input type="date" className="form-control" value={de} onChange={(e) => setDe(e.target.value)} />
        </div>
        <div className="col-6 col-md-3">
          <label className="form-label small mb-1">até</label>
          <input type="date" className="form-control" value={ate} onChange={(e) => setAte(e.target.value)} />
        </div>
      </div>
      <Totais itens={[
        { rotulo: "Bruto", valor: soma("valorBruto"), nota: "sem cancelados" },
        { rotulo: "Taxas", valor: soma("valorTaxa") },
        { rotulo: "Líquido", valor: soma("valorLiquido") },
        { rotulo: "Vencidos a liquidar", texto: String(vencidos.length), classe: vencidos.length ? "text-danger" : "" },
      ]} />
      <ErroAlert erro={erro} />
      {loading && !dados ? <Carregando /> : (
        <TabelaCards
          linhas={lista}
          vazio="Nenhum recebível de cartão encontrado."
          destaque={(r) => (r.vencido && r.status === "PREVISTO" ? "border-danger bg-danger-subtle" : "")}
          colunas={[
            { titulo: "Venda", render: (r) => <Link to={`/gestao/pedidos/${r.pedidoId}`}>#{r.pedidoId}</Link> },
            { titulo: "Parcela", render: (r) => `${r.parcela}/${r.totalParcelas} · ${r.operadora ?? "—"}` },
            { titulo: "Bruto", fim: true, render: (r) => fmtMoney(r.valorBruto) },
            { titulo: "Taxa", fim: true, render: (r) => `${fmtMoney(r.valorTaxa)} (${fmtPercent(r.taxaPercentual)})` },
            { titulo: "Líquido", fim: true, render: (r) => fmtMoney(r.valorLiquido) },
            { titulo: "Previsão", render: (r) => (
              <span className={r.vencido && r.status === "PREVISTO" ? "text-danger fw-semibold" : ""}>
                {fmtDate(r.dataPrevista)}{r.vencido && r.status === "PREVISTO" ? " (vencido)" : ""}
              </span>
            ) },
            { titulo: "Situação", render: (r) => (
              <span>
                <StatusBadge tipo="recebivel" valor={r.status} />
                {r.status === "LIQUIDADO" && (
                  <div className="small">
                    {fmtDate(r.dataLiquidacao)} · {fmtMoney(r.valorLiquidado)}
                    {r.diferencaLiquidacao != null && Number(r.diferencaLiquidacao) !== 0 && (
                      <span className="text-danger"> · dif. {fmtMoney(r.diferencaLiquidacao)}</span>
                    )}
                  </div>
                )}
              </span>
            ) },
            { titulo: "Ações", render: (r) => (
              <div className="d-flex gap-1 justify-content-end">
                {r.status === "PREVISTO" && perm.RECEBER && <button className="btn btn-sm btn-success" onClick={() => setModal({ acao: "liquidar", r })}>Liquidar</button>}
                {r.status === "LIQUIDADO" && perm.ESTORNAR && <button className="btn btn-sm btn-outline-warning" onClick={() => setModal({ acao: "estornar", r })}>Estornar liquidação</button>}
              </div>
            ) },
          ]}
        />
      )}
      {modal?.acao === "liquidar" && <LiquidarModal r={modal.r} onClose={() => setModal(null)} onFeito={feito} />}
      {modal?.acao === "estornar" && <EstornarLiquidacao r={modal.r} onClose={() => setModal(null)} onFeito={feito} />}
    </div>
  );
}

function EstornarLiquidacao({ r, onClose, onFeito }) {
  const chave = useChave();
  return (
    <MotivoModal titulo={`Estornar liquidação — parcela ${r.parcela}/${r.totalParcelas}`} submitLabel="Estornar liquidação"
      onClose={onClose}
      onSubmit={async (motivo) => {
        await financeiroApi.estornarLiquidacao(r.id, motivo, chave.obter(`${r.id}:${motivo}`));
        chave.limpar();
        toast.success("Liquidação estornada.");
        onFeito();
      }}>
      <div className="alert alert-warning">
        A entrada de {fmtMoney(r.valorLiquidado)} no banco será estornada e a parcela volta a ficar prevista.
      </div>
    </MotivoModal>
  );
}

function TaxaForm({ taxa, onClose, onFeito }) {
  const [operadora, setOperadora] = useState(taxa?.operadora ?? "");
  const [parcelas, setParcelas] = useState(taxa?.parcelas != null ? String(taxa.parcelas) : "1");
  const [pct, setPct] = useState(taxa?.taxaPercentual != null ? String(taxa.taxaPercentual) : "");
  const [prazo, setPrazo] = useState(taxa?.prazoPrimeiraParcelaDias != null ? String(taxa.prazoPrimeiraParcelaDias) : "");
  const [intervalo, setIntervalo] = useState(taxa?.intervaloDias != null ? String(taxa.intervaloDias) : "");
  const [ativa, setAtiva] = useState(taxa?.ativa ?? true);
  const inteiro = (v, min) => v !== "" && Number.isInteger(Number(v)) && Number(v) >= min;
  const invalido = operadora.trim() === "" || !inteiro(parcelas, 1) || pct === "" || Number(pct) < 0 || !inteiro(prazo, 0) || !inteiro(intervalo, 0);
  return (
    <FormModal titulo={taxa ? `Editar taxa #${taxa.id}` : "Nova taxa de cartão"} submitLabel="Salvar" size="md"
      submitDisabled={invalido} onClose={onClose}
      onSubmit={async () => {
        const body = {
          operadora: operadora.trim(),
          parcelas: Number(parcelas),
          taxaPercentual: Number(pct),
          prazoPrimeiraParcelaDias: Number(prazo),
          intervaloDias: Number(intervalo),
          ativa,
        };
        if (taxa) await financeiroApi.atualizarTaxa(taxa.id, body);
        else await financeiroApi.criarTaxa(body);
        toast.success("Taxa salva.");
        onFeito();
      }}>
      <div className="alert alert-info small">
        Valores informados pela loja conforme o contrato com a operadora; o sistema não presume nenhuma taxa ou prazo.
      </div>
      <div className="row g-3">
        <div className="col-12 col-sm-8">
          <label className="form-label">Operadora <span className="text-danger">*</span></label>
          <input className="form-control" maxLength={60} value={operadora} onChange={(e) => setOperadora(e.target.value)} autoFocus />
        </div>
        <div className="col-6 col-sm-4">
          <label className="form-label">Parcelas <span className="text-danger">*</span></label>
          <input type="number" min="1" className="form-control" value={parcelas} onChange={(e) => setParcelas(e.target.value)} />
        </div>
        <div className="col-6 col-sm-4">
          <label className="form-label">Taxa (%) <span className="text-danger">*</span></label>
          <input type="number" min="0" step="0.0001" className="form-control" value={pct} onChange={(e) => setPct(e.target.value)} />
        </div>
        <div className="col-6 col-sm-4">
          <label className="form-label">Prazo 1ª parcela (dias) <span className="text-danger">*</span></label>
          <input type="number" min="0" className="form-control" value={prazo} onChange={(e) => setPrazo(e.target.value)} />
        </div>
        <div className="col-6 col-sm-4">
          <label className="form-label">Intervalo (dias) <span className="text-danger">*</span></label>
          <input type="number" min="0" className="form-control" value={intervalo} onChange={(e) => setIntervalo(e.target.value)} />
        </div>
        <div className="col-12">
          <div className="form-check form-switch">
            <input className="form-check-input" type="checkbox" id="taxa-ativa" checked={ativa} onChange={(e) => setAtiva(e.target.checked)} />
            <label className="form-check-label" htmlFor="taxa-ativa">Ativa</label>
          </div>
        </div>
      </div>
    </FormModal>
  );
}

function Taxas() {
  const { user } = useAuth();
  const admin = isAdmin(user);
  const [modal, setModal] = useState(null);
  const { dados, loading, erro, recarregar } = useCarga(() => financeiroApi.taxas(), []);
  const lista = Array.isArray(dados) ? dados : [];
  const feito = () => {
    setModal(null);
    recarregar();
  };
  return (
    <div>
      <div className="alert alert-info small">
        Taxas e prazos por operadora e número de parcelas (D11). Sem taxa cadastrada, vendas e recebimentos em cartão ficam bloqueados.
        {!admin && " Somente o proprietário cadastra ou altera taxas."}
      </div>
      <ErroAlert erro={erro} />
      {!loading && lista.length === 0 && (
        <div className="alert alert-warning" role="alert">
          <Pendente codigo="D11">{null}</Pendente> Nenhuma taxa cadastrada: o recebimento em cartão está bloqueado.
        </div>
      )}
      {admin && (
        <div className="d-grid d-sm-flex mb-3">
          <button className="btn btn-primary" onClick={() => setModal({ taxa: null })}>Nova taxa</button>
        </div>
      )}
      {loading && !dados ? <Carregando /> : (
        <TabelaCards
          linhas={lista}
          vazio="Nenhuma taxa cadastrada."
          destaque={(t) => (t.ativa ? "" : "text-muted")}
          colunas={[
            { titulo: "Operadora", render: (t) => t.operadora },
            { titulo: "Parcelas", render: (t) => `${t.parcelas}x` },
            { titulo: "Taxa", fim: true, render: (t) => fmtPercent(t.taxaPercentual) },
            { titulo: "Prazo 1ª parcela", render: (t) => `${t.prazoPrimeiraParcelaDias} dia(s)` },
            { titulo: "Intervalo", render: (t) => `${t.intervaloDias} dia(s)` },
            { titulo: "Situação", render: (t) => (t.ativa ? <span className="badge text-bg-success">Ativa</span> : <span className="badge text-bg-secondary">Inativa</span>) },
            ...(admin ? [{ titulo: "Ações", render: (t) => <button className="btn btn-sm btn-outline-primary" onClick={() => setModal({ taxa: t })}>Editar</button> }] : []),
          ]}
        />
      )}
      {modal && <TaxaForm taxa={modal.taxa} onClose={() => setModal(null)} onFeito={feito} />}
    </div>
  );
}

export default function CartaoPage() {
  const [aba, setAba] = useState("recebiveis");
  return (
    <div>
      <ul className="nav nav-tabs mb-3" role="tablist">
        {[["recebiveis", "Recebíveis"], ["taxas", "Taxas de cartão"]].map(([k, t]) => (
          <li className="nav-item" key={k} role="presentation">
            <button type="button" role="tab" aria-selected={aba === k} className={`nav-link ${aba === k ? "active" : ""}`}
              onClick={() => setAba(k)}>{t}</button>
          </li>
        ))}
      </ul>
      {aba === "recebiveis" ? <Recebiveis /> : <Taxas />}
    </div>
  );
}
