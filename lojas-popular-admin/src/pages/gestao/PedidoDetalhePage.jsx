import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { toast } from "react-toastify";
import { vendasApi } from "../../services/vendasApi";
import StatusBadge from "../../components/gestao/StatusBadge";
import ErroAlert from "../../components/gestao/ErroAlert";
import ConfirmModal from "../../components/gestao/ConfirmModal";
import { Secao, Linha } from "../../components/gestao/Secao";
import { EntregaSecao, MontagemSecao } from "../../components/gestao/AtendimentoExpedicao";
import { EncomendasSecao, MovimentacoesSecao, OcorrenciasSecao } from "../../components/gestao/AtendimentoPosVenda";
import { useAuth } from "../../context/AuthContext";
import {
  CANAIS,
  FORMAS,
  MODALIDADES_ITEM,
  TIPOS_ENTREGA,
  fmtDateTime,
  fmtMoney,
  fmtPercent,
  isGestor,
  novaChave,
} from "../../utils/format";

export default function PedidoDetalhePage() {
  const { id } = useParams();
  const { user } = useAuth();
  const gestor = isGestor(user);
  const [venda, setVenda] = useState(null);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState(null);
  const [erroAcao, setErroAcao] = useState(null);
  const [busy, setBusy] = useState(false);
  const [modal, setModal] = useState(null); // 'cancelar' | 'aprovar' | 'rejeitar'
  // Chave de idempotência da confirmação: mesma chave para reenvios da mesma tentativa.
  const chaveConfirmacao = useRef({ id: null, chave: null });

  const carregar = useCallback(async () => {
    setLoading(true);
    setErro(null);
    try {
      setVenda(await vendasApi.obter(id));
    } catch (err) {
      setErro(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  async function executar(fn, sucesso) {
    if (busy) return;
    setBusy(true);
    setErroAcao(null);
    try {
      const data = await fn();
      setVenda(data);
      setModal(null);
      if (sucesso) toast.success(sucesso);
      return true;
    } catch (err) {
      setErroAcao(err);
      setModal(null);
      return false;
    } finally {
      setBusy(false);
    }
  }

  // Recarrega o pedido sem tela de carregamento (ex.: após abrir uma ocorrência).
  async function carregarSilencioso() {
    try {
      setVenda(await vendasApi.obter(id));
    } catch (err) {
      setErroAcao(err);
    }
  }

  async function confirmar() {
    if (chaveConfirmacao.current.id !== id || !chaveConfirmacao.current.chave) {
      chaveConfirmacao.current = { id, chave: novaChave() };
    }
    const ok = await executar(
      () => vendasApi.confirmar(id, chaveConfirmacao.current.chave),
      "Venda confirmada.",
    );
    if (ok) chaveConfirmacao.current = { id: null, chave: null };
  }

  if (loading && !venda) return <div className="text-center text-muted py-5">Carregando pedido...</div>;
  if (erro && !venda) {
    return (
      <div>
        <ErroAlert erro={erro} />
        <Link to="/gestao/pedidos" className="btn btn-outline-secondary">Voltar aos pedidos</Link>
      </div>
    );
  }
  if (!venda) return null;

  const acoes = venda.acoes ?? {};
  const bloq = acoes.bloqueios ?? {};
  const cliente = venda.cliente;
  const end = venda.endereco;
  const itens = venda.itens ?? [];
  const freteGratis = !venda.frete || Number(venda.frete) === 0;

  return (
    <div>
      <div className="mb-2">
        <Link to="/gestao/pedidos" className="small">&larr; Pedidos</Link>
      </div>

      {/* Cabeçalho de status */}
      <div className="card mb-3">
        <div className="card-body">
          <div className="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-2">
            <h3 className="mb-0">Pedido #{venda.id}</h3>
            {venda.revisaoLegado && <span className="badge text-bg-danger">Revisar (pedido legado)</span>}
          </div>
          <div className="row g-2 text-center">
            <div className="col-6 col-md-3">
              <div className="small text-muted">Comercial</div>
              <StatusBadge tipo="comercial" valor={venda.statusComercial} />
            </div>
            <div className="col-6 col-md-3">
              <div className="small text-muted">Pagamento</div>
              <StatusBadge tipo="pagamento" valor={venda.statusPagamento} />
            </div>
            <div className="col-6 col-md-3">
              <div className="small text-muted">Entrega</div>
              <StatusBadge tipo="entrega" valor={venda.statusEntrega} />
            </div>
            <div className="col-6 col-md-3">
              <div className="small text-muted">Montagem</div>
              <StatusBadge tipo="montagem" valor={venda.statusMontagem} />
            </div>
          </div>
          {venda.statusComercial === "CANCELADA" && (
            <div className="alert alert-danger mt-3 mb-0">
              Cancelada em {fmtDateTime(venda.canceladoEm)}. Motivo: {venda.motivoCancelamento ?? "—"}
            </div>
          )}
        </div>
      </div>

      <ErroAlert erro={erroAcao} onClose={() => setErroAcao(null)} />

      {/* Ações */}
      <div className="card mb-3">
        <div className="card-body d-grid gap-2">
          {acoes.podeEditar && (
            <div className="d-grid d-sm-flex gap-2">
              <Link className="btn btn-outline-primary" to={`/gestao/vendas/${venda.id}/editar`}>
                Editar
              </Link>
              <button
                className="btn btn-outline-secondary"
                disabled={busy}
                onClick={() => executar(() => vendasApi.recalcular(venda.id), "Preços recalculados.")}
              >
                Recalcular preços
              </button>
            </div>
          )}

          {(acoes.podeConfirmar || bloq.confirmar) && (
            <div className="d-grid d-sm-flex align-items-sm-center gap-2">
              <button className="btn btn-success" disabled={busy || !acoes.podeConfirmar} onClick={confirmar}>
                {busy ? "Aguarde..." : "Confirmar venda"}
              </button>
              {!acoes.podeConfirmar && bloq.confirmar && <span className="text-danger small">{bloq.confirmar}</span>}
            </div>
          )}

          {(acoes.podeCancelar || bloq.cancelar) && (
            <div className="d-grid d-sm-flex align-items-sm-center gap-2">
              <button
                className="btn btn-outline-danger"
                disabled={busy || !acoes.podeCancelar}
                onClick={() => setModal("cancelar")}
              >
                Cancelar venda
              </button>
              {!acoes.podeCancelar && bloq.cancelar && <span className="text-danger small">{bloq.cancelar}</span>}
            </div>
          )}

          {(acoes.podeAprovarDesconto || bloq.aprovarDesconto) && (
            <div className="d-grid d-sm-flex align-items-sm-center gap-2">
              <button
                className="btn btn-success"
                disabled={busy || !acoes.podeAprovarDesconto}
                onClick={() => setModal("aprovar")}
              >
                Aprovar desconto
              </button>
              <button
                className="btn btn-outline-danger"
                disabled={busy || !acoes.podeAprovarDesconto}
                onClick={() => setModal("rejeitar")}
              >
                Rejeitar desconto
              </button>
              {!acoes.podeAprovarDesconto && bloq.aprovarDesconto && (
                <span className="text-danger small">{bloq.aprovarDesconto}</span>
              )}
            </div>
          )}

          {!acoes.podeEditar && !acoes.podeConfirmar && !acoes.podeCancelar && !acoes.podeAprovarDesconto &&
            !bloq.confirmar && !bloq.cancelar && !bloq.aprovarDesconto && (
              <span className="text-muted">
                Nenhuma ação geral neste momento. Entrega, montagem e pós-venda estão nas seções abaixo.
              </span>
            )}
        </div>
      </div>

      <div className="row">
        <div className="col-lg-6">
          <Secao titulo="Cliente e venda">
            <Linha rotulo="Cliente">
              {cliente?.nome ?? "—"}
              {cliente?.telefone ? ` · ${cliente.telefone}` : ""}
            </Linha>
            <Linha rotulo="Vendedor">{venda.vendedor?.nome ?? "—"}</Linha>
            <Linha rotulo="Canal">{CANAIS[venda.canal] ?? venda.canal ?? "—"}</Linha>
            <Linha rotulo="Criado em">{fmtDateTime(venda.criadoEm)}</Linha>
            {venda.confirmadoEm && <Linha rotulo="Confirmado em">{fmtDateTime(venda.confirmadoEm)}</Linha>}
            {venda.observacao && <Linha rotulo="Observação">{venda.observacao}</Linha>}
          </Secao>
        </div>
        <div className="col-lg-6">
          <Secao titulo="Tipo e endereço de entrega">
            <Linha rotulo="Tipo">{TIPOS_ENTREGA[venda.tipoEntrega] ?? venda.tipoEntrega ?? "—"}</Linha>
            {venda.tipoEntrega === "ENTREGA" && (
              end ? (
                <div className="mt-1">
                  {end.logradouro}
                  {end.numero ? `, ${end.numero}` : ""}
                  {end.complemento ? ` - ${end.complemento}` : ""}
                  <br />
                  {[end.bairro, end.cidade && `${end.cidade}/${end.uf ?? ""}`, end.cep].filter(Boolean).join(" · ")}
                </div>
              ) : (
                <div className="text-muted">Endereço não informado.</div>
              )
            )}
          </Secao>
        </div>
      </div>

      <Secao titulo="Itens">
        {itens.length === 0 ? (
          <div className="text-muted">Sem itens.</div>
        ) : (
          <>
            <div className="table-responsive d-none d-md-block">
              <table className="table align-middle mb-0">
                <thead>
                  <tr>
                    <th>Descrição</th>
                    <th>SKU</th>
                    <th>Modalidade</th>
                    <th className="text-end">Qtd</th>
                    <th className="text-end">Preço base</th>
                    <th className="text-end">Preço aplicado</th>
                    <th className="text-end">Total</th>
                    <th>Reserva</th>
                  </tr>
                </thead>
                <tbody>
                  {itens.map((i) => (
                    <tr key={i.id}>
                      <td>{i.descricao}</td>
                      <td>{i.sku ?? "—"}</td>
                      <td>{MODALIDADES_ITEM[i.modalidade] ?? i.modalidade}</td>
                      <td className="text-end">{i.quantidade}</td>
                      <td className="text-end">{fmtMoney(i.precoBase)}</td>
                      <td className="text-end">{fmtMoney(i.precoUnitario)}</td>
                      <td className="text-end">{fmtMoney(i.total)}</td>
                      <td>{i.reserva ? <StatusBadge tipo="reserva" valor={i.reserva} /> : "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="d-md-none d-grid gap-2">
              {itens.map((i) => (
                <div key={i.id} className="border rounded p-2">
                  <div className="fw-semibold">{i.descricao}</div>
                  <div className="small text-muted">
                    {i.sku ? `SKU ${i.sku} · ` : ""}
                    {MODALIDADES_ITEM[i.modalidade] ?? i.modalidade}
                  </div>
                  <div className="d-flex justify-content-between mt-1">
                    <span>{i.quantidade} x {fmtMoney(i.precoUnitario)}</span>
                    <strong>{fmtMoney(i.total)}</strong>
                  </div>
                  <div className="small text-muted">Preço base: {fmtMoney(i.precoBase)}</div>
                  {i.reserva && (
                    <div className="mt-1">Reserva: <StatusBadge tipo="reserva" valor={i.reserva} /></div>
                  )}
                </div>
              ))}
            </div>
          </>
        )}
      </Secao>

      <div className="row">
        <div className="col-lg-6">
          <Secao titulo="Totais e pagamento">
            <Linha rotulo="Subtotal">{fmtMoney(venda.subtotal)}</Linha>
            <Linha rotulo="Desconto">{fmtMoney(venda.desconto)}</Linha>
            <Linha rotulo="Frete">{freteGratis ? "Gratuito" : fmtMoney(venda.frete)}</Linha>
            <Linha rotulo="Total"><strong>{fmtMoney(venda.total)}</strong></Linha>
            <hr />
            <Linha rotulo="Forma">{FORMAS[venda.formaPagamento] ?? venda.formaPagamento ?? "—"}</Linha>
            <Linha rotulo="Parcelas">{venda.parcelas ?? "—"}</Linha>
            <Linha rotulo="Ajuste da condição">{fmtPercent(venda.ajusteCondicaoPercentual)}</Linha>
          </Secao>
        </div>
        <div className="col-lg-6">
          <Secao titulo="Reservas de estoque">
            {(venda.reservas ?? []).length === 0 ? (
              <div className="text-muted">Nenhuma reserva.</div>
            ) : (
              <ul className="list-unstyled mb-0">
                {venda.reservas.map((r) => {
                  const item = itens.find((i) => i.id === r.itemId);
                  return (
                    <li key={r.id} className="py-1 border-bottom">
                      {item?.descricao ?? `Item ${r.itemId}`} — {r.quantidade} un.{" "}
                      <StatusBadge tipo="reserva" valor={r.status} />
                      <div className="small text-muted">
                        Criada em {fmtDateTime(r.criadaEm)}
                        {r.liberadaEm ? ` · Liberada em ${fmtDateTime(r.liberadaEm)}` : ""}
                        {r.motivoLiberacao ? ` · ${r.motivoLiberacao}` : ""}
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </Secao>
        </div>
      </div>

      <Secao titulo="Solicitações de desconto">
        {(venda.descontos ?? []).length === 0 ? (
          <div className="text-muted">Nenhuma solicitação de desconto.</div>
        ) : (
          <div className="d-grid gap-2">
            {venda.descontos.map((d) => (
              <div key={d.id} className="border rounded p-2">
                <div className="d-flex flex-wrap justify-content-between gap-2">
                  <strong>{fmtMoney(d.valor)} ({fmtPercent(d.percentual)})</strong>
                  <StatusBadge tipo="desconto" valor={d.status} />
                </div>
                <div className="small">Solicitado por: {d.solicitante ?? "—"} em {fmtDateTime(d.criadoEm)}</div>
                {d.justificativa && <div className="small">Justificativa: {d.justificativa}</div>}
                {d.decididoPor && (
                  <div className="small">
                    Decidido por {d.decididoPor} em {fmtDateTime(d.decididoEm)}
                    {d.motivoDecisao ? ` — Motivo: ${d.motivoDecisao}` : ""}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </Secao>

      <div className="row">
        <div className="col-lg-6">
          <Secao titulo="Histórico">
            {(venda.historico ?? []).length === 0 ? (
              <div className="text-muted">Sem eventos registrados.</div>
            ) : (
              <ul className="gestao-timeline">
                {venda.historico.map((h, idx) => (
                  <li key={idx}>
                    <div className="fw-semibold">{h.descricao ?? h.tipo}</div>
                    <div className="small text-muted">
                      {fmtDateTime(h.data)}
                      {h.usuario ? ` · ${h.usuario}` : ""}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Secao>
        </div>
      </div>

      <EntregaSecao venda={venda} onVenda={setVenda} gestor={gestor} />
      <MontagemSecao venda={venda} onVenda={setVenda} gestor={gestor} />
      <EncomendasSecao venda={venda} gestor={gestor} />
      <OcorrenciasSecao venda={venda} gestor={gestor} onNovaOcorrencia={carregarSilencioso} />
      <MovimentacoesSecao venda={venda} />

      <ConfirmModal
        open={modal === "cancelar"}
        titulo="Cancelar venda"
        confirmLabel="Cancelar venda"
        variant="danger"
        motivo={{ label: "Motivo do cancelamento", obrigatorio: true, max: 300 }}
        busy={busy}
        onCancel={() => setModal(null)}
        onConfirm={(motivo) => executar(() => vendasApi.cancelar(venda.id, motivo), "Venda cancelada.")}
      >
        <p className="mb-0">As reservas de estoque desta venda serão liberadas. Esta ação não pode ser desfeita.</p>
      </ConfirmModal>

      <ConfirmModal
        open={modal === "aprovar"}
        titulo="Aprovar desconto"
        confirmLabel="Aprovar"
        variant="success"
        motivo={{ label: "Motivo (opcional)", obrigatorio: false, max: 300 }}
        busy={busy}
        onCancel={() => setModal(null)}
        onConfirm={(motivo) => executar(() => vendasApi.aprovarDesconto(venda.id, motivo), "Desconto aprovado.")}
      />

      <ConfirmModal
        open={modal === "rejeitar"}
        titulo="Rejeitar desconto"
        confirmLabel="Rejeitar"
        variant="danger"
        motivo={{ label: "Motivo da rejeição", obrigatorio: true, max: 300 }}
        busy={busy}
        onCancel={() => setModal(null)}
        onConfirm={(motivo) => executar(() => vendasApi.rejeitarDesconto(venda.id, motivo), "Desconto rejeitado.")}
      />
    </div>
  );
}
