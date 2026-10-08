import { useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import { financeiroApi } from "../../../services/financeiroApi";
import ErroAlert from "../ErroAlert";
import FormModal from "../FormModal";
import StatusBadge from "../StatusBadge";
import { Secao, Linha } from "../Secao";
import { useChave } from "../useChave";
import MotivoModal from "./MotivoModal";
import { useFinanceiroPermissoes } from "./useFinanceiroPermissoes";
import { AvisosLista, CampoValor } from "./Comuns";
import { FORMAS, fmtDate, fmtDateTime, fmtMoney, fmtMoneyRestrito, hojeIso } from "../../../utils/format";

export function SolicitarModal({ o, onClose, onFeito }) {
  const max = Number(o.valorRestituivel ?? 0);
  const [valor, setValor] = useState(max > 0 ? max.toFixed(2) : "");
  const [motivo, setMotivo] = useState("");
  const n = Number(valor);
  return (
    <FormModal titulo="Solicitar restituição ao cliente" submitLabel="Solicitar" size="md"
      submitDisabled={!(n > 0) || n > max + 0.004 || motivo.trim() === ""} onClose={onClose}
      onSubmit={async () => {
        await financeiroApi.solicitarRestituicao(o.id, { valor: n, motivo: motivo.trim() });
        toast.success("Restituição solicitada. Falta a autorização de outra pessoa.");
        onFeito();
      }}>
      <div className="alert alert-info">
        Devolução <strong>financeira</strong>: devolve dinheiro ao cliente, na mesma forma da venda. É independente da
        devolução física do produto. Fluxo: solicitar &rarr; autorizar (outra pessoa) &rarr; efetivar.
      </div>
      <div className="row g-3">
        <div className="col-12">
          <CampoValor label="Valor a restituir" obrigatorio value={valor} onChange={setValor} max={max > 0 ? max : undefined}
            autoFocus ajuda={`Máximo restituível: ${fmtMoney(max)}.`} />
        </div>
        <div className="col-12">
          <label className="form-label">Motivo <span className="text-danger">*</span></label>
          <textarea className="form-control" rows={3} maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
        </div>
      </div>
    </FormModal>
  );
}

export function EfetivarModal({ r, onClose, onFeito }) {
  const [data, setData] = useState("");
  const chave = useChave();
  return (
    <FormModal titulo={`Efetivar restituição #${r.id}`} submitLabel="Efetivar restituição" variant="success" size="md"
      onClose={onClose}
      onSubmit={async () => {
        await financeiroApi.efetivarRestituicao(r.id, { data: data || null }, chave.obter(`${r.id}:${data}`));
        chave.limpar();
        toast.success("Restituição efetivada.");
        onFeito();
      }}>
      <div className="alert alert-warning">
        Efetivar registra a saída de <strong>{fmtMoney(r.valor)}</strong> ({FORMAS[r.forma] ?? r.forma}) e não pode ser
        desfeita por esta tela. {r.forma === "DINHEIRO" ? "Dinheiro exige o caixa aberto. " : ""}
        Autorizada por {r.autorizadaPor ?? "—"}.
      </div>
      <label className="form-label">Data da restituição</label>
      <input type="date" className="form-control" max={hojeIso()} value={data} onChange={(e) => setData(e.target.value)} />
      <div className="form-text">Vazio = hoje.</div>
    </FormModal>
  );
}

/** Ações (autorizar, efetivar, cancelar) de uma restituição; chama `onFeito` após cada operação. */
export function AcoesRestituicao({ r, onFeito }) {
  const { perm } = useFinanceiroPermissoes();
  const [modal, setModal] = useState(null);
  const [busy, setBusy] = useState(false);
  const [erro, setErro] = useState(null);

  async function autorizar() {
    if (busy) return;
    setBusy(true);
    setErro(null);
    try {
      await financeiroApi.autorizarRestituicao(r.id);
      toast.success("Restituição autorizada.");
      onFeito();
    } catch (e) {
      setErro(e);
    } finally {
      setBusy(false);
    }
  }

  const fim = () => {
    setModal(null);
    onFeito();
  };
  const aberta = r.status === "SOLICITADA" || r.status === "AUTORIZADA";
  if (!aberta || !perm.RESTITUIR) return null;
  return (
    <div className="mt-2">
      <ErroAlert erro={erro} onClose={() => setErro(null)} />
      <div className="d-grid d-sm-flex gap-2">
        {r.status === "SOLICITADA" && (
          <button type="button" className="btn btn-primary" disabled={busy} onClick={autorizar}>
            {busy ? "Aguarde..." : "Autorizar"}
          </button>
        )}
        {r.status === "AUTORIZADA" && (
          <button type="button" className="btn btn-success" onClick={() => setModal("efetivar")}>Efetivar</button>
        )}
        <button type="button" className="btn btn-outline-danger" disabled={busy} onClick={() => setModal("cancelar")}>Cancelar</button>
      </div>
      {r.status === "SOLICITADA" && (
        <div className="small text-muted mt-1">
          A autorização deve ser feita por outra pessoa que não {r.solicitadaPor ?? "o solicitante"}; o servidor valida.
        </div>
      )}
      {modal === "efetivar" && <EfetivarModal r={r} onClose={() => setModal(null)} onFeito={fim} />}
      {modal === "cancelar" && (
        <MotivoModal titulo={`Cancelar restituição #${r.id}`} submitLabel="Cancelar restituição" onClose={() => setModal(null)}
          onSubmit={async (motivo) => {
            await financeiroApi.cancelarRestituicao(r.id, motivo);
            toast.success("Restituição cancelada.");
            fim();
          }} />
      )}
    </div>
  );
}

export function RestituicaoResumo({ r }) {
  return (
    <div className="small">
      {r.solicitadaPor == null && r.solicitadaEm == null && (
        <div className="text-muted">Detalhes restritos a quem consulta o financeiro.</div>
      )}
      {(r.solicitadaPor != null || r.solicitadaEm != null) && (
        <div>Solicitada por {r.solicitadaPor ?? "—"} em {fmtDateTime(r.solicitadaEm)}</div>
      )}
      {r.autorizadaPor && <div>Autorizada por {r.autorizadaPor} em {fmtDateTime(r.autorizadaEm)}</div>}
      {r.efetivadaPor && (
        <div>Efetivada por {r.efetivadaPor} em {fmtDateTime(r.efetivadaEm)}{r.dataEfetiva ? ` (data ${fmtDate(r.dataEfetiva)})` : ""}</div>
      )}
      {r.motivoCancelamento && <div className="text-danger">Cancelada: {r.motivoCancelamento}</div>}
    </div>
  );
}

function CobrarDiferencaModal({ o, onClose, onFeito }) {
  const [venc, setVenc] = useState("");
  return (
    <FormModal titulo="Cobrar diferença da troca" submitLabel="Criar conta a receber" size="md" submitDisabled={!venc}
      onClose={onClose}
      onSubmit={async () => {
        await financeiroApi.cobrarDiferenca(o.id, venc);
        toast.success("Conta a receber criada.");
        onFeito();
      }}>
      <div className="alert alert-info">
        Cria uma <strong>conta a receber</strong> de {fmtMoney(o.diferencaCalculada)} (diferença da troca). O recebimento é
        feito depois, na tela Contas.
      </div>
      <label className="form-label">Vencimento <span className="text-danger">*</span></label>
      <input type="date" className="form-control" value={venc} onChange={(e) => setVenc(e.target.value)} autoFocus />
      <div className="form-text">Nenhuma data é presumida: informe o vencimento combinado com o cliente.</div>
    </FormModal>
  );
}

/** Bloco "Restituição" (devolução financeira) da ocorrência, separado da devolução física. */
export default function RestituicaoBloco({ o, onFeito }) {
  const { perm } = useFinanceiroPermissoes();
  const [modal, setModal] = useState(null);
  const restituicoes = o.restituicoes ?? [];
  if (o.tipo === "ASSISTENCIA" && restituicoes.length === 0) return null;
  const bloqueios = { ...(o.bloqueios ?? {}) };
  delete bloqueios.solucaoFinanceira; // já exibido no topo da página
  const fim = () => {
    setModal(null);
    onFeito();
  };
  return (
    <>
      <Secao titulo="Restituição (devolução financeira)">
        <div className="small text-muted mb-2">
          A devolução do produto (física) e a devolução do dinheiro (financeira) são controles separados: uma não
          dispara a outra.
        </div>
        {o.situacaoFinanceira && (
          <Linha rotulo="Situação financeira"><StatusBadge tipo="situacaoFinanceira" valor={o.situacaoFinanceira} /></Linha>
        )}
        <Linha rotulo="Valor ainda restituível">{fmtMoneyRestrito(o.valorRestituivel)}</Linha>
        <AvisosLista itens={bloqueios} titulo="Bloqueios" />

        {restituicoes.length === 0 ? (
          <div className="text-muted mb-2">Nenhuma restituição solicitada.</div>
        ) : (
          <div className="d-grid gap-2 mb-2">
            {restituicoes.map((r) => (
              <div key={r.id} className="border rounded p-2">
                <div className="d-flex flex-wrap justify-content-between gap-2">
                  <strong>
                    {fmtMoneyRestrito(r.valor)}
                    {r.forma && <span className="fw-normal small"> ({FORMAS[r.forma] ?? r.forma})</span>}
                  </strong>
                  <StatusBadge tipo="restituicao" valor={r.status} />
                </div>
                {r.motivo && <div>{r.motivo}</div>}
                <RestituicaoResumo r={r} />
                <AcoesRestituicao r={r} onFeito={onFeito} />
              </div>
            ))}
          </div>
        )}

        {o.podeSolicitarRestituicao && perm.RESTITUIR && (
          <div className="d-grid d-sm-flex">
            <button type="button" className="btn btn-outline-primary" onClick={() => setModal("solicitar")}>Solicitar restituição</button>
          </div>
        )}
      </Secao>

      {o.tipo === "TROCA" && (
        <Secao titulo="Diferença da troca">
          <Linha rotulo="Diferença calculada">{fmtMoneyRestrito(o.diferencaCalculada)}</Linha>
          {o.contaDiferenca ? (
            <div className="border rounded p-2 mt-2">
              Conta a receber #{o.contaDiferenca.id}: {fmtMoneyRestrito(o.contaDiferenca.valor)} · vencimento{" "}
              {fmtDate(o.contaDiferenca.vencimento)} · <StatusBadge tipo="conta" valor={o.contaDiferenca.situacao} />
              <div><Link to="/gestao/financeiro/contas">Abrir em Contas</Link></div>
            </div>
          ) : (
            <div className="text-muted mt-1">
              {o.situacaoFinanceira?.startsWith("DIFERENCA")
                ? "Detalhes da cobrança restritos a quem consulta o financeiro."
                : "Nenhuma cobrança de diferença criada."}
            </div>
          )}
          {o.podeCobrarDiferenca && perm.RESTITUIR && !o.contaDiferenca && (
            <div className="d-grid d-sm-flex mt-2">
              <button type="button" className="btn btn-outline-primary" onClick={() => setModal("cobrar")}>Cobrar diferença</button>
            </div>
          )}
        </Secao>
      )}

      {modal === "solicitar" && <SolicitarModal o={o} onClose={() => setModal(null)} onFeito={fim} />}
      {modal === "cobrar" && <CobrarDiferencaModal o={o} onClose={() => setModal(null)} onFeito={fim} />}
    </>
  );
}
