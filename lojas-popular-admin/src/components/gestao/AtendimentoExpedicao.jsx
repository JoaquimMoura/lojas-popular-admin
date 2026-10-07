import { useState } from "react";
import { toast } from "react-toastify";
import { vendasApi } from "../../services/vendasApi";
import StatusBadge from "./StatusBadge";
import FormModal from "./FormModal";
import ArquivoInput from "./ArquivoInput";
import ArquivoLink from "./ArquivoLink";
import { Secao, Linha } from "./Secao";
import { useChave } from "./useChave";
import { ROTULOS, arquivoGrande, fmtDate, fmtDateTime, hojeIso } from "../../utils/format";

const PERIODOS = Object.entries(ROTULOS.periodo);

/** Formulário de data/período usado no agendamento e reagendamento de entrega e montagem. */
function AgendamentoModal({
  titulo,
  submitLabel,
  rotuloResponsavel,
  inicial,
  comMotivo,
  comObservacao,
  onClose,
  onSubmit,
}) {
  const [data, setData] = useState(inicial?.data ?? "");
  const [periodo, setPeriodo] = useState(inicial?.periodo ?? "");
  const [resp, setResp] = useState(inicial?.resp ?? "");
  const [obs, setObs] = useState("");
  const [motivo, setMotivo] = useState("");
  const invalido = !data || !periodo || (comMotivo && motivo.trim() === "");

  return (
    <FormModal
      titulo={titulo}
      submitLabel={submitLabel}
      submitDisabled={invalido}
      onClose={onClose}
      onSubmit={() => onSubmit({ data, periodo, resp: resp.trim(), obs: obs.trim(), motivo: motivo.trim() })}
    >
      <div className="row g-3">
        <div className="col-12 col-sm-6">
          <label className="form-label">Data <span className="text-danger">*</span></label>
          <input type="date" className="form-control" min={hojeIso()} value={data}
            onChange={(e) => setData(e.target.value)} required />
        </div>
        <div className="col-12 col-sm-6">
          <label className="form-label">Período <span className="text-danger">*</span></label>
          <select className="form-select" value={periodo} onChange={(e) => setPeriodo(e.target.value)} required>
            <option value="">— selecione —</option>
            {PERIODOS.map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
        </div>
        <div className="col-12">
          <label className="form-label">{rotuloResponsavel}</label>
          <input className="form-control" maxLength={150} value={resp} onChange={(e) => setResp(e.target.value)} />
        </div>
        {comObservacao && (
          <div className="col-12">
            <label className="form-label">Observação</label>
            <textarea className="form-control" rows={2} maxLength={300} value={obs}
              onChange={(e) => setObs(e.target.value)} />
          </div>
        )}
        {comMotivo && (
          <div className="col-12">
            <label className="form-label">Motivo do reagendamento <span className="text-danger">*</span></label>
            <textarea className="form-control" rows={2} maxLength={300} value={motivo}
              onChange={(e) => setMotivo(e.target.value)} />
          </div>
        )}
      </div>
    </FormModal>
  );
}

function MotivoModal({ titulo, rotulo, submitLabel, variant = "primary", onClose, onSubmit, children }) {
  const [motivo, setMotivo] = useState("");
  return (
    <FormModal titulo={titulo} submitLabel={submitLabel} variant={variant} size="md"
      submitDisabled={motivo.trim() === ""} onClose={onClose} onSubmit={() => onSubmit(motivo.trim())}>
      {children}
      <label className="form-label">{rotulo} <span className="text-danger">*</span></label>
      <textarea className="form-control" rows={3} maxLength={300} value={motivo}
        onChange={(e) => setMotivo(e.target.value)} autoFocus />
    </FormModal>
  );
}

function ConcluirEntregaModal({ onClose, onSubmit }) {
  const [recebedor, setRecebedor] = useState("");
  const [obs, setObs] = useState("");
  const [arquivo, setArquivo] = useState(null);
  const invalido = (recebedor.trim() === "" && !arquivo) || arquivoGrande(arquivo);
  return (
    <FormModal titulo="Concluir entrega" submitLabel="Concluir entrega" variant="success" submitDisabled={invalido}
      onClose={onClose} onSubmit={() => onSubmit({ recebedor: recebedor.trim(), obs: obs.trim(), arquivo })}>
      <p className="text-muted">Informe o nome de quem recebeu ou anexe o comprovante (foto/PDF). Pelo menos um é obrigatório.</p>
      <div className="row g-3">
        <div className="col-12">
          <label className="form-label">Nome de quem recebeu</label>
          <input className="form-control" maxLength={150} value={recebedor}
            onChange={(e) => setRecebedor(e.target.value)} />
        </div>
        <div className="col-12">
          <ArquivoInput label="Comprovante (foto ou PDF)" arquivo={arquivo} onChange={setArquivo} />
        </div>
        <div className="col-12">
          <label className="form-label">Observação</label>
          <textarea className="form-control" rows={2} maxLength={300} value={obs}
            onChange={(e) => setObs(e.target.value)} />
        </div>
      </div>
    </FormModal>
  );
}

function ConcluirMontagemModal({ onClose, onSubmit }) {
  const [obs, setObs] = useState("");
  const [arquivo, setArquivo] = useState(null);
  const invalido = (obs.trim() === "" && !arquivo) || arquivoGrande(arquivo);
  return (
    <FormModal titulo="Concluir montagem" submitLabel="Concluir montagem" variant="success" submitDisabled={invalido}
      onClose={onClose} onSubmit={() => onSubmit({ obs: obs.trim(), arquivo })}>
      <p className="text-muted">Registre uma observação e/ou anexe a evidência (foto/PDF). Pelo menos um é obrigatório.</p>
      <div className="row g-3">
        <div className="col-12">
          <label className="form-label">Observação</label>
          <textarea className="form-control" rows={3} maxLength={300} value={obs}
            onChange={(e) => setObs(e.target.value)} />
        </div>
        <div className="col-12">
          <ArquivoInput label="Evidência (foto ou PDF)" arquivo={arquivo} onChange={setArquivo} />
        </div>
      </div>
    </FormModal>
  );
}

function BotaoComBloqueio({ habilitado, bloqueio, onClick, className = "btn btn-primary", children }) {
  return (
    <div className="d-grid d-sm-flex align-items-sm-center gap-2">
      <button type="button" className={className} disabled={!habilitado} onClick={onClick}>
        {children}
      </button>
      {!habilitado && bloqueio && <span className="text-danger small">{bloqueio}</span>}
    </div>
  );
}

export function EntregaSecao({ venda, onVenda, gestor }) {
  const [modal, setModal] = useState(null);
  const chaveSaida = useChave();
  const entrega = venda.entrega;
  const acoes = venda.acoes ?? {};
  const bloq = acoes.bloqueios ?? {};
  const retirada = venda.tipoEntrega === "RETIRADA";
  const termo = retirada ? "retirada" : "entrega";

  async function rodar(fn, sucesso) {
    onVenda(await fn());
    setModal(null);
    if (sucesso) toast.success(sucesso);
  }

  const mostrarSaida = gestor && (acoes.podeRegistrarSaida || !!bloq.saida);
  const temAcao =
    gestor &&
    (acoes.podeAgendarEntrega || acoes.podeReagendarEntrega || mostrarSaida ||
      acoes.podeRegistrarTentativaFrustrada || acoes.podeConcluirEntrega);

  return (
    <Secao titulo="Entrega">
      {entrega ? (
        <>
          <Linha rotulo="Situação"><StatusBadge tipo="entregaRegistro" valor={entrega.status} /></Linha>
          <Linha rotulo="Data prevista">
            {fmtDate(entrega.dataPrevista)}
            {entrega.periodo ? ` · ${ROTULOS.periodo[entrega.periodo] ?? entrega.periodo}` : ""}
          </Linha>
          <Linha rotulo="Equipe">{entrega.equipe || "—"}</Linha>
          <Linha rotulo="Frete">{entrega.frete ?? "Gratuito"}</Linha>
          {entrega.observacao && <Linha rotulo="Observação">{entrega.observacao}</Linha>}
          {entrega.saidaEm && <Linha rotulo="Saída registrada em">{fmtDateTime(entrega.saidaEm)}</Linha>}
          {entrega.tentativasFrustradas > 0 && (
            <Linha rotulo="Tentativas frustradas">{entrega.tentativasFrustradas}</Linha>
          )}
          {entrega.concluidaEm && (
            <>
              <Linha rotulo="Concluída em">{fmtDateTime(entrega.concluidaEm)}</Linha>
              <Linha rotulo="Recebido por">{entrega.recebedorNome || "—"}</Linha>
              {entrega.observacaoConclusao && <Linha rotulo="Observação da conclusão">{entrega.observacaoConclusao}</Linha>}
              {entrega.comprovanteArquivo && (
                <Linha rotulo="Comprovante">
                  <ArquivoLink caminho={entrega.comprovanteArquivo}>Abrir comprovante</ArquivoLink>
                </Linha>
              )}
            </>
          )}
          {(entrega.eventos ?? []).length > 0 && (
            <div className="mt-3">
              <div className="fw-semibold mb-2">Linha do tempo</div>
              <ul className="gestao-timeline">
                {entrega.eventos.map((ev) => (
                  <li key={ev.id}>
                    <div className="fw-semibold">
                      <StatusBadge tipo="eventoEntrega" valor={ev.tipo} />
                      {ev.dataPrevista && (
                        <span className="ms-2 small fw-normal">
                          {fmtDate(ev.dataPrevista)}
                          {ev.periodo ? ` · ${ROTULOS.periodo[ev.periodo] ?? ev.periodo}` : ""}
                        </span>
                      )}
                    </div>
                    {ev.equipe && <div className="small">Equipe: {ev.equipe}</div>}
                    {ev.motivo && <div className="small">Motivo: {ev.motivo}</div>}
                    {ev.arquivo && <div className="small"><ArquivoLink caminho={ev.arquivo}>Abrir arquivo</ArquivoLink></div>}
                    <div className="small text-muted">
                      {fmtDateTime(ev.criadoEm)}
                      {ev.usuario ? ` · ${ev.usuario}` : ""}
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      ) : (
        <>
          <Linha rotulo="Situação"><StatusBadge tipo="entrega" valor={venda.statusEntrega} /></Linha>
          <Linha rotulo="Frete">Gratuito</Linha>
          <div className="text-muted">Ainda não há {termo} agendada.</div>
        </>
      )}

      {temAcao && (
        <div className="d-grid gap-2 mt-3">
          {acoes.podeAgendarEntrega && (
            <button className="btn btn-primary" onClick={() => setModal("agendar")}>
              {retirada ? "Agendar retirada" : "Agendar entrega"}
            </button>
          )}
          {acoes.podeReagendarEntrega && (
            <button className="btn btn-outline-primary" onClick={() => setModal("reagendar")}>
              Reagendar
            </button>
          )}
          {mostrarSaida && (
            <BotaoComBloqueio habilitado={!!acoes.podeRegistrarSaida} bloqueio={bloq.saida}
              className="btn btn-success" onClick={() => setModal("saida")}>
              Registrar saída
            </BotaoComBloqueio>
          )}
          {acoes.podeRegistrarTentativaFrustrada && (
            <button className="btn btn-outline-danger" onClick={() => setModal("frustrada")}>
              Tentativa frustrada
            </button>
          )}
          {acoes.podeConcluirEntrega && (
            <button className="btn btn-success" onClick={() => setModal("concluir")}>
              Concluir {termo}
            </button>
          )}
        </div>
      )}

      {modal === "agendar" && (
        <AgendamentoModal titulo={retirada ? "Agendar retirada" : "Agendar entrega"} submitLabel="Agendar"
          rotuloResponsavel="Equipe" comObservacao onClose={() => setModal(null)}
          onSubmit={(f) => rodar(() => vendasApi.agendarEntrega(venda.id, {
            data: f.data, periodo: f.periodo, equipe: f.resp || null, observacao: f.obs || null,
          }), "Agendamento registrado.")} />
      )}
      {modal === "reagendar" && (
        <AgendamentoModal titulo="Reagendar" submitLabel="Reagendar" rotuloResponsavel="Equipe" comMotivo
          inicial={{ data: "", periodo: entrega?.periodo ?? "", resp: entrega?.equipe ?? "" }}
          onClose={() => setModal(null)}
          onSubmit={(f) => rodar(() => vendasApi.reagendarEntrega(venda.id, {
            data: f.data, periodo: f.periodo, equipe: f.resp || null, motivo: f.motivo,
          }), "Reagendado.")} />
      )}
      {modal === "saida" && (
        <FormModal titulo="Registrar saída" submitLabel="Registrar saída" variant="success" size="md"
          onClose={() => setModal(null)}
          onSubmit={async () => {
            await rodar(() => vendasApi.registrarSaida(venda.id, chaveSaida.obter(String(venda.id))), "Saída registrada.");
            chaveSaida.limpar();
          }}>
          <p className="mb-2">
            Confirma a saída da venda #{venda.id}? A saída é da venda inteira (não há entrega parcial): as reservas
            serão baixadas do estoque.
          </p>
          <p className="text-muted small mb-0">Se a conexão falhar, tente novamente: a operação não será duplicada.</p>
        </FormModal>
      )}
      {modal === "frustrada" && (
        <MotivoModal titulo="Tentativa frustrada" rotulo="Motivo" submitLabel="Registrar" variant="danger"
          onClose={() => setModal(null)}
          onSubmit={(motivo) => rodar(() => vendasApi.tentativaFrustrada(venda.id, { motivo }), "Tentativa registrada.")} />
      )}
      {modal === "concluir" && (
        <ConcluirEntregaModal onClose={() => setModal(null)}
          onSubmit={(f) => {
            const fd = new FormData();
            if (f.recebedor) fd.append("recebedor", f.recebedor);
            if (f.obs) fd.append("observacao", f.obs);
            if (f.arquivo) fd.append("arquivo", f.arquivo);
            return rodar(() => vendasApi.concluirEntrega(venda.id, fd), "Entrega concluída.");
          }} />
      )}
    </Secao>
  );
}

export function MontagemSecao({ venda, onVenda, gestor }) {
  const [modal, setModal] = useState(null);
  const montagem = venda.montagem;
  const acoes = venda.acoes ?? {};
  const bloq = acoes.bloqueios ?? {};

  async function rodar(fn, sucesso) {
    onVenda(await fn());
    setModal(null);
    if (sucesso) toast.success(sucesso);
  }

  const temAcao =
    gestor &&
    (acoes.podeAgendarMontagem || acoes.podeConcluirMontagem || acoes.podeDispensarMontagem || !!bloq.concluirMontagem);

  return (
    <Secao titulo="Montagem">
      <div className="alert alert-light border py-2">
        {montagem?.cobranca ?? "Montagem inclusa, sem cobrança adicional"}
      </div>
      {montagem ? (
        <>
          <Linha rotulo="Situação"><StatusBadge tipo="montagem" valor={montagem.status} /></Linha>
          {montagem.dataPrevista && (
            <Linha rotulo="Data prevista">
              {fmtDate(montagem.dataPrevista)}
              {montagem.periodo ? ` · ${ROTULOS.periodo[montagem.periodo] ?? montagem.periodo}` : ""}
            </Linha>
          )}
          <Linha rotulo="Responsável">{montagem.responsavel || "—"}</Linha>
          {montagem.observacao && <Linha rotulo="Observação">{montagem.observacao}</Linha>}
          {montagem.concluidaEm && <Linha rotulo="Concluída em">{fmtDateTime(montagem.concluidaEm)}</Linha>}
          {montagem.evidenciaArquivo && (
            <Linha rotulo="Evidência">
              <ArquivoLink caminho={montagem.evidenciaArquivo}>Abrir evidência</ArquivoLink>
            </Linha>
          )}
          {montagem.motivoDispensa && <Linha rotulo="Motivo (não necessária)">{montagem.motivoDispensa}</Linha>}
        </>
      ) : (
        <div className="text-muted">
          Situação: <StatusBadge tipo="montagem" valor={venda.statusMontagem} /> — montagem ainda não agendada.
        </div>
      )}

      {temAcao && (
        <div className="d-grid gap-2 mt-3">
          {acoes.podeAgendarMontagem && (
            <button className="btn btn-primary" onClick={() => setModal("agendar")}>
              {montagem?.status === "AGENDADA" ? "Reagendar montagem" : "Agendar montagem"}
            </button>
          )}
          {(acoes.podeConcluirMontagem || bloq.concluirMontagem) && (
            <BotaoComBloqueio habilitado={!!acoes.podeConcluirMontagem} bloqueio={bloq.concluirMontagem}
              className="btn btn-success" onClick={() => setModal("concluir")}>
              Concluir montagem
            </BotaoComBloqueio>
          )}
          {acoes.podeDispensarMontagem && (
            <button className="btn btn-outline-secondary" onClick={() => setModal("dispensar")}>
              Montagem não necessária
            </button>
          )}
        </div>
      )}

      {modal === "agendar" && (
        <AgendamentoModal titulo="Agendar montagem" submitLabel="Agendar" rotuloResponsavel="Responsável" comObservacao
          onClose={() => setModal(null)}
          onSubmit={(f) => rodar(() => vendasApi.agendarMontagem(venda.id, {
            data: f.data, periodo: f.periodo, responsavel: f.resp || null, observacao: f.obs || null,
          }), "Montagem agendada.")} />
      )}
      {modal === "concluir" && (
        <ConcluirMontagemModal onClose={() => setModal(null)}
          onSubmit={(f) => {
            const fd = new FormData();
            if (f.obs) fd.append("observacao", f.obs);
            if (f.arquivo) fd.append("arquivo", f.arquivo);
            return rodar(() => vendasApi.concluirMontagem(venda.id, fd), "Montagem concluída.");
          }} />
      )}
      {modal === "dispensar" && (
        <MotivoModal titulo="Montagem não necessária" rotulo="Motivo" submitLabel="Confirmar"
          onClose={() => setModal(null)}
          onSubmit={(motivo) => rodar(() => vendasApi.montagemNaoNecessaria(venda.id, { motivo }), "Montagem dispensada.")} />
      )}
    </Secao>
  );
}
