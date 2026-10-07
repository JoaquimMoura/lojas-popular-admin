import { useState } from "react";
import { toast } from "react-toastify";
import { financeiroApi } from "../../../services/financeiroApi";
import { useAuth } from "../../../context/AuthContext";
import ConfirmModal from "../../../components/gestao/ConfirmModal";
import ErroAlert from "../../../components/gestao/ErroAlert";
import StatusBadge from "../../../components/gestao/StatusBadge";
import { Secao, Linha } from "../../../components/gestao/Secao";
import { useCarga } from "../../../components/gestao/useCarga";
import MotivoModal from "../../../components/gestao/financeiro/MotivoModal";
import { AvisosLista, Carregando, Pendente, TabelaCards } from "../../../components/gestao/financeiro/Comuns";
import { ORIGENS_LANCAMENTO, fmtDateTime, fmtMes, fmtMoney, isAdmin, mesAtual } from "../../../utils/format";

function CartaoValores({ titulo, nota, linhas }) {
  return (
    <div className="col-md-6">
      <div className="card h-100">
        <div className="card-header">{titulo}</div>
        <div className="card-body">
          <div className="small text-muted mb-2">{nota}</div>
          {linhas.map(([r, v, forte]) => (
            <Linha key={r} rotulo={r}>{forte ? <strong>{fmtMoney(v)}</strong> : fmtMoney(v)}</Linha>
          ))}
        </div>
      </div>
    </div>
  );
}

export default function FechamentoPage() {
  const { user } = useAuth();
  const admin = isAdmin(user);
  const [mes, setMes] = useState(mesAtual());
  const [modal, setModal] = useState(null); // 'aprovar' | 'reabrir'
  const [busy, setBusy] = useState(false);
  const [erroAcao, setErroAcao] = useState(null);
  const { dados: f, setDados, loading, erro, recarregar } = useCarga(() => financeiroApi.previaFechamento(mes), [mes]);

  async function aprovar() {
    if (busy) return;
    setBusy(true);
    setErroAcao(null);
    try {
      setDados(await financeiroApi.aprovarFechamento(mes));
      toast.success(`Fechamento de ${fmtMes(mes)} aprovado.`);
      setModal(null);
    } catch (e) {
      setErroAcao(e);
      setModal(null);
    } finally {
      setBusy(false);
    }
  }

  const r = f?.resultado;
  const c = f?.caixa;
  const bloqueantes = (f?.pendencias ?? []).filter((p) => p.bloqueante && Number(p.quantidade) > 0);

  return (
    <div>
      <div className="mb-3" style={{ maxWidth: 240 }}>
        <label className="form-label small mb-1">Mês de competência</label>
        <input type="month" className="form-control" value={mes} onChange={(e) => e.target.value && setMes(e.target.value)} />
      </div>
      <ErroAlert erro={erroAcao} onClose={() => setErroAcao(null)} />
      <ErroAlert erro={erro} />
      {loading && !f && <Carregando texto="Calculando prévia..." />}

      {f && (
        <div aria-live="polite">
          <div className="card mb-3">
            <div className="card-body d-flex flex-wrap justify-content-between align-items-center gap-2">
              <div>
                <h5 className="mb-0">{fmtMes(f.mes)}</h5>
                {f.fechado ? (
                  <span className="badge text-bg-success">Mês fechado{f.versao ? ` — versão ${f.versao}` : ""}</span>
                ) : (
                  <span className="badge text-bg-warning text-dark">Mês aberto — prévia{f.ultimoStatus === "REABERTO" ? " (reaberto)" : ""}</span>
                )}
              </div>
              <button className="btn btn-outline-secondary" onClick={recarregar} disabled={loading}>Atualizar prévia</button>
            </div>
            {f.fechado && (
              <div className="alert alert-info rounded-0 mb-0 border-0 border-top small">
                Mês fechado: novos lançamentos com data neste mês são <strong>bloqueados</strong>. Para corrigir, o mês precisa ser reaberto com justificativa.
              </div>
            )}
          </div>

          <div className="row g-3 mb-3">
            <CartaoValores titulo="Caixa físico (dinheiro)" nota="Dinheiro em espécie. Não se mistura com o banco."
              linhas={[["Entradas", c?.entradasCaixa], ["Saídas", c?.saidasCaixa], ["Saldo do mês", c?.saldoCaixa, true]]} />
            <CartaoValores titulo="Banco" nota="Pix, liquidação de cartão e movimentos bancários. Cartão só entra na liquidação."
              linhas={[["Entradas", c?.entradasBanco], ["Saídas", c?.saidasBanco], ["Saldo do mês", c?.saldoBanco, true]]} />
          </div>
          {c?.porOrigem && Object.keys(c.porOrigem).length > 0 && (
            <details className="mb-3">
              <summary>Movimento por origem</summary>
              <div className="mt-2">
                {Object.entries(c.porOrigem).map(([k, v]) => (
                  <Linha key={k} rotulo={ORIGENS_LANCAMENTO[k] ?? k}>{fmtMoney(v)}</Linha>
                ))}
              </div>
            </details>
          )}

          <Secao titulo="Resultado do mês">
            {r && !r.definitivo && (
              <div className="alert alert-warning" role="status">
                <strong>Resultado parcial/provisório — não é lucro.</strong> O lucro só é apurado quando não houver
                itens faltantes. Nenhum lucro é calculado nesta tela.
              </div>
            )}
            <div className="row">
              <div className="col-md-6">
                <Linha rotulo="Receita bruta">{fmtMoney(r?.receitaBruta)}</Linha>
                <Linha rotulo="Restituições">{fmtMoney(r?.restituicoes)}</Linha>
                <Linha rotulo="Taxas de cartão">{fmtMoney(r?.taxasCartao)}</Linha>
                <Linha rotulo="Despesas">{fmtMoney(r?.despesas)}</Linha>
              </div>
              <div className="col-md-6">
                <Linha rotulo="Comissões">{fmtMoney(r?.comissoes)}</Linha>
                <Linha rotulo="Comissões previstas">{fmtMoney(r?.comissoesPrevistas)}</Linha>
                <Linha rotulo="Custos conhecidos">{fmtMoney(r?.custosConhecidos)}</Linha>
                <Linha rotulo="Itens sem custo">{r?.itensSemCusto ?? "—"}</Linha>
              </div>
            </div>
            <hr />
            <Linha rotulo="Resultado parcial"><strong>{fmtMoney(r?.resultadoParcial)}</strong></Linha>
            <Linha rotulo="Lucro apurado">
              {r?.lucroApurado == null ? <span className="text-muted">Não apurado</span> : <strong>{fmtMoney(r.lucroApurado)}</strong>}
            </Linha>
            <Linha rotulo="Critério de competência da receita">
              <Pendente codigo="D06">{r?.criterioCompetencia}</Pendente>
            </Linha>
            <AvisosLista itens={r?.faltantes} titulo="O que falta para o resultado definitivo" />
          </Secao>

          <Secao titulo="Pendências do mês">
            <TabelaCards
              linhas={f.pendencias ?? []}
              chave={(p) => p.codigo}
              vazio="Nenhuma pendência registrada."
              colunas={[
                { titulo: "Código", render: (p) => <strong>{p.codigo}</strong> },
                { titulo: "Descrição", render: (p) => p.descricao },
                { titulo: "Quantidade", fim: true, render: (p) => p.quantidade },
                { titulo: "Bloqueia a aprovação?", render: (p) => (
                  Number(p.quantidade) > 0 && p.bloqueante
                    ? <span className="badge text-bg-danger">Bloqueia</span>
                    : <span className="badge text-bg-secondary">{Number(p.quantidade) > 0 ? "Não bloqueia" : "Sem ocorrências"}</span>
                ) },
              ]}
            />
            {bloqueantes.length > 0 && (
              <div className="small text-danger mt-2">{bloqueantes.length} pendência(s) com ocorrências bloqueiam a aprovação.</div>
            )}
          </Secao>

          <Secao titulo="Aprovação e reabertura">
            {admin ? (
              <>
                <AvisosLista itens={f.bloqueiosAprovacao} titulo="Aprovação bloqueada" />
                <div className="d-grid d-sm-flex gap-2">
                  <button className="btn btn-success" disabled={!f.podeAprovar || busy} onClick={() => setModal("aprovar")}>
                    Aprovar fechamento
                  </button>
                </div>
              </>
            ) : (
              <div className="text-muted small mb-2">Somente o proprietário aprova o fechamento.</div>
            )}
            <hr />
            <AvisosLista itens={f.bloqueiosReabertura} titulo="Reabertura bloqueada" />
            <div className="d-grid d-sm-flex gap-2">
              <button className="btn btn-outline-danger" disabled={!f.podeReabrir} onClick={() => setModal("reabrir")}>
                Reabrir mês
              </button>
            </div>
            <div className="form-text">A reabertura exige justificativa e fica registrada nas versões.</div>
          </Secao>

          <Secao titulo="Versões do fechamento">
            <TabelaCards
              linhas={f.versoes ?? []}
              chave={(v) => v.versao}
              vazio="Este mês ainda não foi fechado."
              colunas={[
                { titulo: "Versão", render: (v) => `v${v.versao}` },
                { titulo: "Situação", render: (v) => <StatusBadge tipo="fechamento" valor={v.status} /> },
                { titulo: "Aprovado", render: (v) => `${fmtDateTime(v.aprovadoEm)}${v.aprovadoPor ? ` · ${v.aprovadoPor}` : ""}` },
                { titulo: "Reaberto", render: (v) => (v.reabertoEm ? `${fmtDateTime(v.reabertoEm)}${v.reabertoPor ? ` · ${v.reabertoPor}` : ""}` : "—") },
                { titulo: "Justificativa", render: (v) => v.justificativaReabertura ?? "—" },
              ]}
            />
          </Secao>
        </div>
      )}

      <ConfirmModal open={modal === "aprovar"} titulo={`Aprovar fechamento de ${fmtMes(mes)}`} confirmLabel="Aprovar fechamento"
        variant="success" busy={busy} onCancel={() => setModal(null)} onConfirm={aprovar}>
        <p className="mb-0">
          Após a aprovação, novos lançamentos neste mês serão bloqueados. O resultado continua
          {r && !r.definitivo ? " parcial (sem lucro apurado)" : ""}. Para alterar, será preciso reabrir o mês.
        </p>
      </ConfirmModal>

      {modal === "reabrir" && (
        <MotivoModal titulo={`Reabrir ${fmtMes(mes)}`} rotulo="Justificativa" submitLabel="Reabrir mês" onClose={() => setModal(null)}
          onSubmit={async (justificativa) => {
            setDados(await financeiroApi.reabrirFechamento(mes, justificativa));
            toast.success("Mês reaberto.");
            setModal(null);
          }}>
          <div className="alert alert-warning">A versão atual fica no histórico e o mês volta a aceitar lançamentos.</div>
        </MotivoModal>
      )}
    </div>
  );
}
