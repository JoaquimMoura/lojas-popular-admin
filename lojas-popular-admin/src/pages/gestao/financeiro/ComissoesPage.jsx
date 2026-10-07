import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import { financeiroApi } from "../../../services/financeiroApi";
import { usuariosApi } from "../../../services/usuariosApi";
import { useAuth } from "../../../context/AuthContext";
import ErroAlert from "../../../components/gestao/ErroAlert";
import FormModal from "../../../components/gestao/FormModal";
import StatusBadge from "../../../components/gestao/StatusBadge";
import { useCarga } from "../../../components/gestao/useCarga";
import { AvisosLista, Carregando, Pendente, TabelaCards, Totais } from "../../../components/gestao/financeiro/Comuns";
import { ROTULOS, fmtDate, fmtMoney, fmtMes, fmtPercent, isAdmin } from "../../../utils/format";

function ResumoComissoes({ r, somenteLeitura }) {
  return (
    <>
      <AvisosLista itens={r.avisos} titulo="Avisos sobre comissões" />
      {!r.percentualDefinido && (
        <div className="alert alert-warning">
          Percentual de comissão: <Pendente codigo="D01">{null}</Pendente> — nenhuma comissão é calculada (nem zero, nem estimativa).
        </div>
      )}
      {!r.aquisicaoDefinida && (
        <div className="alert alert-warning">
          Momento em que a comissão passa a ser devida: <Pendente codigo="D02">{null}</Pendente> — as comissões permanecem apenas como previsão.
        </div>
      )}
      <Totais itens={[
        { rotulo: "Previstas", valor: r.previstas },
        { rotulo: "Devidas", valor: r.devidas },
        { rotulo: "Em conta a pagar", valor: r.emConta },
        { rotulo: "Pagas", valor: r.pagas },
        { rotulo: "Reversões pendentes", valor: r.reversoesPendentes, classe: Number(r.reversoesPendentes) !== 0 ? "text-danger" : "" },
        { rotulo: "Saldo a pagar", valor: r.saldoAPagar, classe: "text-primary" },
      ]} />
      <TabelaCards
        linhas={r.itens ?? []}
        vazio="Nenhuma comissão registrada."
        colunas={[
          { titulo: "Venda", render: (c) => (somenteLeitura ? `#${c.pedidoId}` : <Link to={`/gestao/pedidos/${c.pedidoId}`}>#{c.pedidoId}</Link>) },
          ...(somenteLeitura ? [] : [{ titulo: "Vendedor", render: (c) => c.vendedor ?? "—" }]),
          { titulo: "Tipo", render: (c) => (c.tipo === "REVERSAO" ? "Reversão" : "Previsão") },
          { titulo: "Situação", render: (c) => <StatusBadge tipo="comissao" valor={c.status} /> },
          { titulo: "Base", fim: true, render: (c) => fmtMoney(c.base) },
          { titulo: "%", fim: true, render: (c) => fmtPercent(c.percentual) },
          { titulo: "Valor", fim: true, render: (c) => fmtMoney(c.valor) },
          { titulo: "Competência", render: (c) => fmtMes(c.competencia) },
          { titulo: "Adquirida em", render: (c) => fmtDate(c.adquiridaEm) },
          { titulo: "Observação", render: (c) => c.motivo ?? "—" },
        ]}
      />
    </>
  );
}

function PagamentoModal({ vendedores, onClose, onFeito }) {
  const [vendedorId, setVendedorId] = useState("");
  const [venc, setVenc] = useState("");
  return (
    <FormModal titulo="Gerar pagamento de comissão" submitLabel="Gerar conta a pagar" size="md"
      submitDisabled={!vendedorId || !venc} onClose={onClose}
      onSubmit={async () => {
        await financeiroApi.gerarPagamentoComissao({ vendedorId: Number(vendedorId), vencimento: venc });
        toast.success("Conta a pagar gerada com as comissões devidas.");
        onFeito();
      }}>
      <div className="alert alert-info">
        Reúne as comissões <strong>devidas</strong> do vendedor em uma conta a pagar (tela Contas). A baixa é feita lá.
        Nenhum vencimento é presumido.
      </div>
      <div className="row g-3">
        <div className="col-12">
          <label className="form-label">Vendedor <span className="text-danger">*</span></label>
          <select className="form-select" value={vendedorId} onChange={(e) => setVendedorId(e.target.value)} autoFocus>
            <option value="">— selecione —</option>
            {vendedores.map((v) => <option key={v.id} value={v.id}>{v.nome}</option>)}
          </select>
        </div>
        <div className="col-12">
          <label className="form-label">Vencimento <span className="text-danger">*</span></label>
          <input type="date" className="form-control" value={venc} onChange={(e) => setVenc(e.target.value)} />
        </div>
      </div>
    </FormModal>
  );
}

export default function ComissoesPage() {
  const { user } = useAuth();
  const admin = isAdmin(user);
  const [vendedorId, setVendedorId] = useState("");
  const [status, setStatus] = useState("");
  const [vendedores, setVendedores] = useState([]);
  const [modal, setModal] = useState(false);
  const [busy, setBusy] = useState(false);
  const [erroAcao, setErroAcao] = useState(null);
  const { dados, setDados, loading, erro, recarregar } = useCarga(
    () => financeiroApi.comissoes({ vendedorId, status }),
    [vendedorId, status],
  );

  useEffect(() => {
    usuariosApi.vendedores().then((v) => setVendedores(Array.isArray(v) ? v : [])).catch(() => {});
  }, []);

  async function gerarPrevisoes() {
    if (busy) return;
    setBusy(true);
    setErroAcao(null);
    try {
      await financeiroApi.gerarPrevisoes();
      toast.success("Previsões geradas.");
      recarregar();
    } catch (e) {
      setErroAcao(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <div className="row g-2 mb-3 align-items-end">
        <div className="col-12 col-md-3">
          <label className="form-label small mb-1">Vendedor</label>
          <select className="form-select" value={vendedorId} onChange={(e) => setVendedorId(e.target.value)}>
            <option value="">Todos</option>
            {vendedores.map((v) => <option key={v.id} value={v.id}>{v.nome}</option>)}
          </select>
        </div>
        <div className="col-12 col-md-3">
          <label className="form-label small mb-1">Situação</label>
          <select className="form-select" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Todas</option>
            {Object.entries(ROTULOS.comissao).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        {admin && (
          <div className="col-12 col-md-6 d-grid d-md-flex justify-content-md-end gap-2">
            <button className="btn btn-outline-primary" disabled={busy} onClick={gerarPrevisoes}>
              {busy ? "Gerando..." : "Gerar previsões"}
            </button>
            <button className="btn btn-primary" onClick={() => setModal(true)}>Gerar pagamento</button>
          </div>
        )}
      </div>
      {!admin && <div className="small text-muted mb-2">Gerar previsões e pagamentos é exclusivo do proprietário.</div>}
      <ErroAlert erro={erroAcao} onClose={() => setErroAcao(null)} />
      <ErroAlert erro={erro} />
      {loading && !dados ? <Carregando /> : dados && <ResumoComissoes r={dados} />}
      {modal && (
        <PagamentoModal vendedores={vendedores} onClose={() => setModal(false)}
          onFeito={() => { setModal(false); setDados(null); recarregar(); }} />
      )}
    </div>
  );
}

/** Página do vendedor: somente leitura das próprias comissões. */
export function MinhasComissoesPage() {
  const { dados, loading, erro } = useCarga(() => financeiroApi.minhasComissoes(), []);
  return (
    <div>
      <h3 className="mb-3">Minhas comissões</h3>
      <ErroAlert erro={erro} />
      {loading && !dados ? <Carregando /> : dados && <ResumoComissoes r={dados} somenteLeitura />}
    </div>
  );
}
