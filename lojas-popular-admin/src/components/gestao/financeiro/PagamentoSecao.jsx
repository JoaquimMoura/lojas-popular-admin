import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import { financeiroApi } from "../../../services/financeiroApi";
import FormModal from "../FormModal";
import ModalShell from "../ModalShell";
import StatusBadge from "../StatusBadge";
import { Secao, Linha } from "../Secao";
import { useChave } from "../useChave";
import MotivoModal from "./MotivoModal";
import { AvisosLista, CampoValor, TabelaCards } from "./Comuns";
import { FORMAS, ORIGENS_LANCAMENTO, LIVROS, fmtDate, fmtDateTime, fmtMoney, fmtPercent, hojeIso } from "../../../utils/format";

/** Parcelas que a operadora vai pagar: bruto, taxa, líquido e previsão. */
export function RecebiveisMini({ recebiveis }) {
  return (
    <TabelaCards
      linhas={recebiveis ?? []}
      vazio="Nenhuma parcela de cartão."
      colunas={[
        { titulo: "Parcela", render: (r) => `${r.parcela}/${r.totalParcelas}` },
        { titulo: "Operadora", render: (r) => r.operadora ?? "—" },
        { titulo: "Bruto", fim: true, render: (r) => fmtMoney(r.valorBruto) },
        { titulo: "Taxa", fim: true, render: (r) => `${fmtMoney(r.valorTaxa)} (${fmtPercent(r.taxaPercentual)})` },
        { titulo: "Líquido", fim: true, render: (r) => fmtMoney(r.valorLiquido) },
        { titulo: "Previsão", render: (r) => fmtDate(r.dataPrevista) },
        { titulo: "Situação", render: (r) => <StatusBadge tipo="recebivel" valor={r.status} /> },
      ]}
    />
  );
}

export function NotaTresConceitos() {
  return (
    <div className="alert alert-info small">
      <strong>Pagamento do cliente ≠ recebível da operadora ≠ entrada efetiva no banco.</strong> O recebimento registra
      que o cliente pagou. No cartão, a operadora paga depois (parcelas abaixo, já com a taxa descontada) e o dinheiro só
      entra no banco quando a parcela é liquidada na tela Cartão.
    </div>
  );
}

function RegistrarModal({ venda, pag, onClose, onVenda }) {
  const forma = pag.forma;
  const cartao = forma === "CARTAO";
  const dinheiro = forma === "DINHEIRO";
  const saldo = Number(pag.saldo ?? 0);
  const [valor, setValor] = useState(cartao ? String(pag.total ?? "") : saldo > 0 ? saldo.toFixed(2) : "");
  const [data, setData] = useState("");
  const [referencia, setReferencia] = useState("");
  const [operadora, setOperadora] = useState("");
  const [obs, setObs] = useState("");
  const [caixa, setCaixa] = useState(null); // null = carregando
  const [sugestoes, setSugestoes] = useState([]);
  const [novo, setNovo] = useState(null);
  const chave = useChave();

  useEffect(() => {
    let ativo = true;
    if (dinheiro) {
      financeiroApi.caixaAtual().then((c) => ativo && setCaixa(c)).catch(() => ativo && setCaixa({ aberta: true, erroLeitura: true }));
    }
    if (cartao) {
      financeiroApi.taxas().then((t) => ativo && setSugestoes([...new Set((t ?? []).map((x) => x.operadora))])).catch(() => {});
    }
    return () => {
      ativo = false;
    };
  }, [dinheiro, cartao]);

  const n = Number(valor);
  const caixaFechado = dinheiro && caixa && caixa.aberta === false;
  const invalido =
    !(n > 0) ||
    (cartao && operadora.trim() === "") ||
    (cartao && Math.abs(n - Number(pag.total)) > 0.004) ||
    caixaFechado ||
    (dinheiro && caixa === null);

  if (novo) {
    return (
      <ModalShell titulo="Recebimento registrado" onClose={onClose}>
        <div className="alert alert-success" role="status">
          Recebimento de {fmtMoney(novo.valor)} registrado em {FORMAS[novo.forma] ?? novo.forma}.
        </div>
        {novo.forma === "CARTAO" ? (
          <>
            <NotaTresConceitos />
            <div className="fw-semibold mb-2">Parcelas geradas pela operadora</div>
            <RecebiveisMini recebiveis={novo.recebiveis} />
            <div className="small text-muted mt-2">
              Para dar baixa quando a operadora pagar, use <Link to="/gestao/financeiro/cartao">Financeiro &rsaquo; Cartão</Link>.
            </div>
          </>
        ) : (
          <div>O lançamento já consta nos lançamentos da venda.</div>
        )}
        <div className="d-grid d-sm-flex justify-content-sm-end mt-3">
          <button type="button" className="btn btn-primary" onClick={onClose}>Fechar</button>
        </div>
      </ModalShell>
    );
  }

  return (
    <FormModal titulo={`Registrar recebimento — pedido #${venda.id}`} submitLabel="Registrar recebimento" variant="success"
      submitDisabled={invalido} onClose={onClose}
      onSubmit={async () => {
        const body = {
          valor: n,
          dataPagamento: dinheiro ? null : data || null,
          referencia: referencia.trim() || null,
          operadora: cartao ? operadora.trim() : null,
          observacao: obs.trim() || null,
        };
        const idsAntes = new Set((pag.recebimentos ?? []).map((r) => r.id));
        const atualizado = await financeiroApi.registrarRecebimento(
          venda.id,
          body,
          chave.obter(JSON.stringify([venda.id, body])),
        );
        chave.limpar();
        onVenda(atualizado);
        const criado = (atualizado.pagamento?.recebimentos ?? []).find((r) => !idsAntes.has(r.id));
        toast.success("Recebimento registrado.");
        if (criado) setNovo(criado);
        else onClose();
      }}>
      <div className="mb-3">
        <div className="d-flex justify-content-between"><span className="text-muted">Forma da venda</span>
          <strong>{FORMAS[forma] ?? forma}{cartao ? ` em ${pag.parcelas}x` : ""}</strong></div>
        <div className="d-flex justify-content-between"><span className="text-muted">Total</span><span>{fmtMoney(pag.total)}</span></div>
        <div className="d-flex justify-content-between"><span className="text-muted">Já recebido</span><span>{fmtMoney(pag.recebido)}</span></div>
        <div className="d-flex justify-content-between"><span className="text-muted">Saldo a receber</span><strong>{fmtMoney(pag.saldo)}</strong></div>
        <div className="form-text">A forma de pagamento é a da venda; uma venda tem uma única forma.</div>
      </div>

      {caixaFechado && (
        <div className="alert alert-warning" role="alert">
          O caixa está fechado. O dinheiro só pode ser recebido com o caixa aberto.{" "}
          <Link to="/gestao/financeiro/caixa">Abrir o caixa</Link>
        </div>
      )}
      {cartao && <NotaTresConceitos />}

      <div className="row g-3">
        <div className="col-12 col-sm-6">
          <CampoValor label="Valor recebido" obrigatorio value={valor} onChange={setValor} max={saldo > 0 ? saldo : undefined}
            readOnly={cartao} autoFocus={!cartao}
            ajuda={cartao ? "No cartão o valor é o total da venda." : "Pode ser parcial (Pix e dinheiro); não pode exceder o saldo."} />
        </div>
        {!dinheiro && (
          <div className="col-12 col-sm-6">
            <label className="form-label">Data do pagamento</label>
            <input type="date" className="form-control" max={hojeIso()} value={data} onChange={(e) => setData(e.target.value)} />
            <div className="form-text">Vazio = hoje. Não pode ser futura.</div>
          </div>
        )}
        {cartao && (
          <div className="col-12 col-sm-6">
            <label className="form-label">Operadora <span className="text-danger">*</span></label>
            <input className="form-control" list="operadoras-cadastradas" maxLength={60} value={operadora}
              onChange={(e) => setOperadora(e.target.value)} autoFocus />
            <datalist id="operadoras-cadastradas">
              {sugestoes.map((o) => <option key={o} value={o} />)}
            </datalist>
            <div className="form-text">Use o nome cadastrado em Cartão &rsaquo; Taxas; sem taxa cadastrada o servidor recusa.</div>
          </div>
        )}
        <div className="col-12 col-sm-6">
          <label className="form-label">Referência (comprovante, NSU...)</label>
          <input className="form-control" maxLength={100} value={referencia} onChange={(e) => setReferencia(e.target.value)} />
        </div>
        <div className="col-12">
          <label className="form-label">Observação</label>
          <textarea className="form-control" rows={2} maxLength={300} value={obs} onChange={(e) => setObs(e.target.value)} />
        </div>
      </div>
    </FormModal>
  );
}

function EstornarModal({ rec, onClose, onVenda }) {
  const chave = useChave();
  return (
    <MotivoModal titulo={`Estornar recebimento #${rec.id}`} submitLabel="Estornar recebimento" onClose={onClose}
      onSubmit={async (motivo) => {
        const atualizado = await financeiroApi.estornarRecebimento(rec.id, motivo, chave.obter(`${rec.id}:${motivo}`));
        chave.limpar();
        toast.success("Recebimento estornado.");
        onVenda(atualizado);
        onClose();
      }}>
      <div className="alert alert-warning">
        Estorna {fmtMoney(rec.valor)} ({FORMAS[rec.forma] ?? rec.forma}). {rec.forma === "CARTAO"
          ? "As parcelas da operadora serão canceladas; se alguma já foi liquidada, estorne primeiro a liquidação (tela Cartão)."
          : "O lançamento de entrada será estornado."} Esta ação fica registrada com o seu usuário.
      </div>
    </MotivoModal>
  );
}

/** Seção "Pagamento" do detalhe da venda (recebimentos, recebíveis, restituições e lançamentos). */
export default function PagamentoSecao({ venda, onVenda }) {
  const [modal, setModal] = useState(null); // 'registrar' | { estornar: rec }
  const pag = venda.pagamento;
  const acoes = venda.acoes ?? {};
  if (!pag) return null;
  const recs = pag.recebimentos ?? [];
  const podeRegistrar = acoes.podeRegistrarRecebimento ?? pag.podeRegistrar;
  const podeEstornar = acoes.podeEstornarRecebimento ?? pag.podeEstornar;
  const bloqueios = pag.bloqueios ?? {};

  return (
    <Secao titulo="Pagamento">
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
        <span>Situação: <StatusBadge tipo="pagamento" valor={venda.statusPagamento} /></span>
        {venda.statusPagamento === "PARCIAL" && <span className="badge text-bg-info">Saldo restante: {fmtMoney(pag.saldo)}</span>}
      </div>
      <div className="row">
        <div className="col-md-6">
          <Linha rotulo="Forma (da venda)">{FORMAS[pag.forma] ?? pag.forma ?? "—"}</Linha>
          <Linha rotulo="Parcelas">{pag.forma === "CARTAO" ? `${pag.parcelas}x` : "—"}</Linha>
          <Linha rotulo="Total da venda">{fmtMoney(pag.total)}</Linha>
        </div>
        <div className="col-md-6">
          <Linha rotulo="Recebido">{fmtMoney(pag.recebido)}</Linha>
          <Linha rotulo="Saldo a receber"><strong>{fmtMoney(pag.saldo)}</strong></Linha>
          <Linha rotulo="Restituído ao cliente">{fmtMoney(pag.restituido)}</Linha>
        </div>
      </div>

      {pag.forma === "CARTAO" && <NotaTresConceitos />}
      <AvisosLista itens={Object.fromEntries(Object.entries(bloqueios).filter(([k]) => k !== "caixa"))}
        titulo="Bloqueios financeiros desta venda" />
      {bloqueios.caixa && <div className="small text-muted mb-2">{bloqueios.caixa}</div>}

      {(podeRegistrar || podeEstornar) && (
        <div className="d-grid d-sm-flex gap-2 mb-3">
          {podeRegistrar && (
            <button type="button" className="btn btn-success" onClick={() => setModal("registrar")}>
              Registrar recebimento
            </button>
          )}
        </div>
      )}

      <h6 className="mt-3">Recebimentos</h6>
      {recs.length === 0 ? (
        <div className="text-muted">Nenhum recebimento registrado.</div>
      ) : (
        <div className="d-grid gap-2">
          {recs.map((r) => (
            <div key={r.id} className={`border rounded p-2 ${r.status === "ESTORNADO" ? "bg-light text-muted" : ""}`}>
              <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
                <span>
                  <strong className={r.status === "ESTORNADO" ? "text-decoration-line-through" : ""}>{fmtMoney(r.valor)}</strong>{" "}
                  <StatusBadge tipo="recebimento" valor={r.status} />
                </span>
                {podeEstornar && r.status === "REGISTRADO" && (
                  <button type="button" className="btn btn-sm btn-outline-danger" onClick={() => setModal({ estornar: r })}>
                    Estornar
                  </button>
                )}
              </div>
              <div className="small">
                {FORMAS[r.forma] ?? r.forma}
                {r.forma === "CARTAO" ? ` ${r.parcelas}x${r.operadora ? ` · ${r.operadora}` : ""}` : ""} · pago em {fmtDate(r.dataPagamento)}
                {r.referencia ? ` · ref. ${r.referencia}` : ""}
              </div>
              <div className="small">Registrado por {r.registradoPor ?? "—"} em {fmtDateTime(r.criadoEm)}</div>
              {r.observacao && <div className="small">{r.observacao}</div>}
              {r.status === "ESTORNADO" && (
                <div className="small text-danger">
                  Estornado por {r.estornadoPor ?? "—"} em {fmtDateTime(r.estornadoEm)} — {r.motivoEstorno}
                </div>
              )}
              {r.forma === "CARTAO" && (r.recebiveis ?? []).length > 0 && (
                <details className="mt-2">
                  <summary className="small">Recebíveis da operadora ({r.recebiveis.length})</summary>
                  <div className="mt-2"><RecebiveisMini recebiveis={r.recebiveis} /></div>
                </details>
              )}
            </div>
          ))}
        </div>
      )}

      {(pag.restituicoes ?? []).length > 0 && (
        <>
          <h6 className="mt-3">Restituições ao cliente</h6>
          <div className="d-grid gap-2">
            {pag.restituicoes.map((r) => (
              <div key={r.id} className="border rounded p-2">
                <div className="d-flex flex-wrap justify-content-between gap-2">
                  <strong>{fmtMoney(r.valor)}</strong>
                  <StatusBadge tipo="restituicao" valor={r.status} />
                </div>
                <div className="small">{r.motivo}</div>
                <div className="small text-muted">
                  Ocorrência <Link to={`/gestao/pos-venda/${r.ocorrenciaId}`}>#{r.ocorrenciaId}</Link> · solicitada por{" "}
                  {r.solicitadaPor ?? "—"}
                  {r.efetivadaEm ? ` · efetivada em ${fmtDateTime(r.efetivadaEm)}` : ""}
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      <h6 className="mt-3">Lançamentos da venda (entradas e saídas efetivas)</h6>
      <TabelaCards
        linhas={pag.lancamentos ?? []}
        vazio="Nenhum lançamento efetivo (no cartão, a entrada ocorre só na liquidação da operadora)."
        destaque={(l) => (l.estornado ? "text-muted" : "")}
        colunas={[
          { titulo: "Data", render: (l) => fmtDate(l.dataEfetiva) },
          { titulo: "Livro", render: (l) => LIVROS[l.conta] ?? l.conta },
          { titulo: "Origem", render: (l) => ORIGENS_LANCAMENTO[l.origem] ?? l.origem },
          { titulo: "Descrição", render: (l) => `${l.descricao ?? ""}${l.estornado ? " (estornado)" : ""}` },
          { titulo: "Valor", fim: true, render: (l) => `${l.tipo === "SAIDA" ? "−" : "+"} ${fmtMoney(l.valor)}` },
        ]}
      />

      {modal === "registrar" && <RegistrarModal venda={venda} pag={pag} onClose={() => setModal(null)} onVenda={onVenda} />}
      {modal?.estornar && <EstornarModal rec={modal.estornar} onClose={() => setModal(null)} onVenda={onVenda} />}
    </Secao>
  );
}
