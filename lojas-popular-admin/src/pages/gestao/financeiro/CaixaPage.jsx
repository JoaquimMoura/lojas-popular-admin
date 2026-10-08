import { useState } from "react";
import { useFinanceiroPermissoes } from "../../../components/gestao/financeiro/useFinanceiroPermissoes";
import { toast } from "react-toastify";
import { financeiroApi } from "../../../services/financeiroApi";
import ErroAlert from "../../../components/gestao/ErroAlert";
import FormModal from "../../../components/gestao/FormModal";
import ModalShell from "../../../components/gestao/ModalShell";
import StatusBadge from "../../../components/gestao/StatusBadge";
import { Secao } from "../../../components/gestao/Secao";
import { useChave } from "../../../components/gestao/useChave";
import { useCarga } from "../../../components/gestao/useCarga";
import { CampoValor, Carregando, TabelaCards, Totais } from "../../../components/gestao/financeiro/Comuns";
import { ORIGENS_LANCAMENTO, fmtDate, fmtDateTime, fmtMoney } from "../../../utils/format";

function MovimentosTabela({ movimentos }) {
  return (
    <TabelaCards
      linhas={movimentos ?? []}
      vazio="Nenhum movimento nesta sessão."
      destaque={(l) => (l.estornado ? "text-muted" : "")}
      colunas={[
        { titulo: "Quando", render: (l) => fmtDateTime(l.criadoEm) },
        { titulo: "Origem", render: (l) => ORIGENS_LANCAMENTO[l.origem] ?? l.origem },
        { titulo: "Descrição", render: (l) => `${l.descricao ?? ""}${l.estornado ? " (estornado)" : ""}` },
        { titulo: "Usuário", render: (l) => l.criadoPor ?? "—" },
        { titulo: "Valor", fim: true, render: (l) => `${l.tipo === "SAIDA" ? "−" : "+"} ${fmtMoney(l.valor)}` },
      ]}
    />
  );
}

function AbrirForm({ onFeito }) {
  const [saldo, setSaldo] = useState("0");
  const [busy, setBusy] = useState(false);
  const [erro, setErro] = useState(null);
  async function abrir(e) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setErro(null);
    try {
      await financeiroApi.abrirCaixa(Number(saldo));
      toast.success("Caixa aberto.");
      onFeito();
    } catch (err) {
      setErro(err);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form className="card mb-3" onSubmit={abrir}>
      <div className="card-header">Abrir o caixa</div>
      <div className="card-body">
        <div className="alert alert-warning">O caixa está fechado. Recebimentos em dinheiro só são aceitos com o caixa aberto.</div>
        <ErroAlert erro={erro} onClose={() => setErro(null)} />
        <div className="row g-3 align-items-end">
          <div className="col-12 col-sm-6 col-md-4">
            <CampoValor label="Saldo inicial (troco)" obrigatorio min="0" value={saldo} onChange={setSaldo} />
          </div>
          <div className="col-12 col-sm-6 col-md-3 d-grid">
            <button className="btn btn-success" type="submit" disabled={busy || saldo === "" || Number(saldo) < 0}>
              {busy ? "Abrindo..." : "Abrir caixa"}
            </button>
          </div>
        </div>
      </div>
    </form>
  );
}

function MovimentoModal({ tipo, onClose, onFeito }) {
  const [valor, setValor] = useState("");
  const [motivo, setMotivo] = useState("");
  const chave = useChave();
  const retirada = tipo === "RETIRADA";
  return (
    <FormModal titulo={retirada ? "Retirada de caixa" : "Suprimento de caixa"} submitLabel="Registrar" size="md"
      submitDisabled={!(Number(valor) > 0) || motivo.trim() === ""} onClose={onClose}
      onSubmit={async () => {
        const body = { tipo, valor: Number(valor), motivo: motivo.trim() };
        await financeiroApi.movimentarCaixa(body, chave.obter(JSON.stringify(body)));
        chave.limpar();
        toast.success("Movimento registrado.");
        onFeito();
      }}>
      <div className="alert alert-info">
        {retirada ? "Retirada: dinheiro que sai do caixa (sangria, pagamento em espécie...)." : "Suprimento: dinheiro que entra no caixa (reforço de troco)."}
      </div>
      <div className="row g-3">
        <div className="col-12"><CampoValor label="Valor" obrigatorio value={valor} onChange={setValor} autoFocus /></div>
        <div className="col-12">
          <label className="form-label">Motivo <span className="text-danger">*</span></label>
          <textarea className="form-control" rows={2} maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
        </div>
      </div>
    </FormModal>
  );
}

function FecharModal({ sessao, onClose, onFeito }) {
  const [contado, setContado] = useState("");
  const [motivo, setMotivo] = useState("");
  const esperado = Number(sessao.saldoEsperado ?? 0);
  const preenchido = contado !== "";
  const dif = preenchido ? Math.round((Number(contado) - esperado) * 100) / 100 : null;
  const temDif = dif !== null && dif !== 0;
  return (
    <FormModal titulo="Fechar o caixa" submitLabel="Fechar caixa" variant="danger" size="md"
      submitDisabled={!preenchido || Number(contado) < 0 || (temDif && motivo.trim() === "")} onClose={onClose}
      onSubmit={async () => {
        const r = await financeiroApi.fecharCaixa({ saldoContado: Number(contado), motivoDiferenca: motivo.trim() || null });
        toast.success(r?.diferenca && Number(r.diferenca) !== 0 ? `Caixa fechado com diferença de ${fmtMoney(r.diferenca)}.` : "Caixa fechado.");
        onFeito();
      }}>
      <div className="row text-center g-2 mb-3">
        <div className="col-4"><div className="small text-muted">Esperado</div><strong>{fmtMoney(esperado)}</strong></div>
        <div className="col-4"><div className="small text-muted">Contado</div><strong>{preenchido ? fmtMoney(contado) : "—"}</strong></div>
        <div className="col-4">
          <div className="small text-muted">Diferença</div>
          <strong className={temDif ? "text-danger" : ""}>{dif === null ? "—" : fmtMoney(dif)}</strong>
        </div>
      </div>
      <CampoValor label="Valor contado no caixa" obrigatorio min="0" value={contado} onChange={setContado} autoFocus />
      {temDif && (
        <div className="mt-3">
          <label className="form-label">Motivo da diferença <span className="text-danger">*</span></label>
          <textarea className="form-control" rows={2} maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
          <div className="form-text">Obrigatório quando o valor contado difere do esperado.</div>
        </div>
      )}
    </FormModal>
  );
}

function SessaoDetalhe({ id, onClose }) {
  const { dados, loading, erro } = useCarga(() => financeiroApi.sessaoCaixa(id), [id]);
  return (
    <ModalShell titulo={`Sessão de caixa #${id}`} onClose={onClose}>
      {loading && <Carregando />}
      <ErroAlert erro={erro} />
      {dados && (
        <>
          <Totais itens={[
            { rotulo: "Saldo inicial", valor: dados.saldoInicial },
            { rotulo: "Entradas", valor: dados.entradas },
            { rotulo: "Saídas", valor: dados.saidas },
            { rotulo: "Esperado", valor: dados.saldoEsperado },
            { rotulo: "Contado", valor: dados.saldoContado },
            { rotulo: "Diferença", valor: dados.diferenca, classe: Number(dados.diferenca) !== 0 ? "text-danger" : "" },
          ]} />
          {dados.motivoDiferenca && <div className="mb-2">Motivo da diferença: {dados.motivoDiferenca}</div>}
          <MovimentosTabela movimentos={dados.movimentos} />
        </>
      )}
    </ModalShell>
  );
}

export default function CaixaPage() {
  const atual = useCarga(() => financeiroApi.caixaAtual(), []);
  const hist = useCarga(() => financeiroApi.sessoesCaixa(30), []);
  const { perm } = useFinanceiroPermissoes();
  const [modal, setModal] = useState(null); // SUPRIMENTO | RETIRADA | fechar
  const [ver, setVer] = useState(null);

  function feito() {
    setModal(null);
    atual.recarregar();
    hist.recarregar();
  }

  const c = atual.dados;
  const s = c?.sessao;
  return (
    <div>
      <div className="small text-muted mb-3">
        O caixa físico guarda apenas dinheiro em espécie. Pix e cartão entram no banco e não passam por aqui.
      </div>
      <ErroAlert erro={atual.erro} />
      {atual.loading && !c && <Carregando />}

      {c && !c.aberta && perm.RECEBER && <AbrirForm onFeito={feito} />}
      {c && !c.aberta && !perm.RECEBER && <div className="alert alert-secondary small">Seu perfil não pode abrir o caixa (permissão de receber não concedida).</div>}

      {c?.aberta && s && (
        <Secao titulo={`Caixa aberto — sessão #${s.id}`}>
          <div className="small text-muted mb-2">
            Aberto em {fmtDateTime(s.abertaEm)} por {s.abertaPor ?? "—"} · referência {fmtDate(s.dataReferencia)}
          </div>
          <Totais itens={[
            { rotulo: "Saldo inicial", valor: s.saldoInicial },
            { rotulo: "Entradas", valor: s.entradas },
            { rotulo: "Saídas", valor: s.saidas },
            { rotulo: "Saldo esperado", valor: s.saldoEsperado, classe: "text-primary" },
          ]} />
          <div className="d-grid d-sm-flex gap-2 mb-3">
            {perm.RECEBER && <button className="btn btn-outline-primary" onClick={() => setModal("SUPRIMENTO")}>Suprimento</button>}
            {perm.PAGAR && <button className="btn btn-outline-primary" onClick={() => setModal("RETIRADA")}>Retirada</button>}
            {perm.RECEBER && <button className="btn btn-danger" onClick={() => setModal("fechar")}>Fechar caixa</button>}
          </div>
          <h6>Movimentos da sessão</h6>
          <MovimentosTabela movimentos={s.movimentos} />
        </Secao>
      )}

      <Secao titulo="Histórico de sessões">
        <ErroAlert erro={hist.erro} />
        {hist.loading && !hist.dados ? <Carregando /> : (
          <TabelaCards
            linhas={hist.dados ?? []}
            vazio="Nenhuma sessão de caixa registrada."
            colunas={[
              { titulo: "Sessão", render: (x) => `#${x.id}` },
              { titulo: "Data", render: (x) => fmtDate(x.dataReferencia) },
              { titulo: "Situação", render: (x) => <StatusBadge tipo="sessaoCaixa" valor={x.status} /> },
              { titulo: "Esperado", fim: true, render: (x) => fmtMoney(x.saldoEsperado) },
              { titulo: "Contado", fim: true, render: (x) => fmtMoney(x.saldoContado) },
              {
                titulo: "Diferença",
                fim: true,
                render: (x) => (
                  <span className={Number(x.diferenca) !== 0 && x.diferenca != null ? "text-danger fw-semibold" : ""}>
                    {fmtMoney(x.diferenca)}
                  </span>
                ),
              },
              {
                titulo: "Detalhe",
                render: (x) => (
                  <button className="btn btn-sm btn-outline-secondary" onClick={() => setVer(x.id)}>Ver movimentos</button>
                ),
              },
            ]}
          />
        )}
      </Secao>

      {(modal === "SUPRIMENTO" || modal === "RETIRADA") && <MovimentoModal tipo={modal} onClose={() => setModal(null)} onFeito={feito} />}
      {modal === "fechar" && s && <FecharModal sessao={s} onClose={() => setModal(null)} onFeito={feito} />}
      {ver && <SessaoDetalhe id={ver} onClose={() => setVer(null)} />}
    </div>
  );
}
