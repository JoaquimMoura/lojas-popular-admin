import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { vendasApi } from "../../services/vendasApi";
import ErroAlert from "../../components/gestao/ErroAlert";
import { FORMAS, fmtDate, fmtDateTime, fmtMoney } from "../../utils/format";
import { corrigirEndereco, corrigirTexto } from "../../utils/textoLoja";
import { absUrl } from "../../utils/url";
import "../../styles/comprovante.css";

const NI = "Não informado";
const PERIODOS = { MANHA: "manhã", TARDE: "tarde", DIA_INTEIRO: "dia inteiro" };
const PAGAMENTO = { NAO_INFORMADO: NI, PENDENTE: "Pendente", PARCIAL: "Parcial", PAGO: "Pago" };
const ENTREGA = {
  NAO_INFORMADO: NI, NAO_AGENDADA: "Não agendada — a combinar", AGENDADA: "Agendada", SAIU: "Saiu para entrega",
  TENTATIVA_FRUSTRADA: "Tentativa sem sucesso — a reagendar", ENTREGUE: "Entregue",
};
const MONTAGEM = {
  NAO_INFORMADO: NI, NAO_AGENDADA: "Não agendada — a combinar", AGENDADA: "Agendada", CONCLUIDA: "Concluída",
  NAO_NECESSARIA: "Não necessária",
};

function enderecoLinhas(e) {
  if (!e || !e.logradouro) return null;
  const l1 = [e.logradouro, e.numero].filter(Boolean).join(", ");
  const l2 = [e.complemento, e.bairro].filter(Boolean).join(" — ");
  const l3 = [[e.cidade, e.uf].filter(Boolean).join("/"), e.cep ? `CEP ${String(e.cep).replace(/^(\d{5})(\d{3})$/, "$1-$2")}` : null].filter(Boolean).join(" · ");
  return [l1, l2, l3].filter(Boolean);
}

function Endereco({ e, vazio = NI }) {
  const l = enderecoLinhas(e);
  if (!l) return <span>{vazio}</span>;
  return (
    <>
      {l.map((t, i) => (
        <div key={i}>{corrigirEndereco(t)}</div>
      ))}
    </>
  );
}

function quando(data, periodo) {
  if (!data) return null;
  return `${fmtDate(data)}${periodo ? ` (${PERIODOS[periodo] ?? periodo})` : ""}`;
}

export default function ComprovantePage() {
  const { id } = useParams();
  const [c, setC] = useState(null);
  const [erro, setErro] = useState(null);

  useEffect(() => {
    document.body.classList.add("comprovante-aberto");
    return () => document.body.classList.remove("comprovante-aberto");
  }, []);

  useEffect(() => {
    let vivo = true;
    vendasApi
      .comprovante(id)
      .then((d) => vivo && setC(d))
      .catch((e) => vivo && setErro(e));
    return () => {
      vivo = false;
    };
  }, [id]);

  if (erro) {
    return (
      <div className="container py-4 no-print">
        <ErroAlert erro={erro} />
        <Link to={`/gestao/pedidos/${id}`} className="btn btn-outline-secondary">Voltar ao pedido</Link>
      </div>
    );
  }
  if (!c) return <div className="container py-4 no-print">Gerando comprovante…</div>;

  const loja = c.loja ?? {};
  const pag = c.pagamento ?? {};
  const ent = c.entrega ?? {};
  const mon = c.montagem ?? {};
  const encomendas = c.itens.filter((i) => i.modalidade === "ENCOMENDA");
  const entregaAgendada = quando(ent.dataAgendada, ent.periodo);
  const montagemAgendada = quando(mon.dataAgendada, mon.periodo);
  const cadastral = enderecoLinhas(c.cliente?.enderecoCadastral);
  const mesmoEndereco = cadastral && ent.enderecoDestaEntrega
    && JSON.stringify(cadastral) === JSON.stringify(enderecoLinhas(ent.enderecoDestaEntrega));
  const ajuste = Number(pag.ajusteFormaPagamentoPercentual ?? 0);
  const freteTexto = c.frete == null ? NI : Number(c.frete) === 0 ? "Gratuito" : fmtMoney(c.frete);

  return (
    <div className="comprovante-tela">
      <div className="comprovante-barra no-print">
        <Link to={`/gestao/pedidos/${c.pedidoId}`} className="btn btn-outline-secondary">&larr; Voltar ao pedido</Link>
        <div className="small text-muted flex-grow-1">
          Visualização para impressão (A4). Use “Imprimir” e, no destino, escolha uma impressora ou “Salvar como PDF”.
        </div>
        <button className="btn btn-primary" onClick={() => window.print()}>Imprimir</button>
      </div>

      <article className="comprovante" aria-label={`Pedido de venda ${c.pedidoId}`}>
        {c.cancelado && (
          <div className="comp-cancelado" role="alert">
            PEDIDO CANCELADO{c.canceladoEm ? ` em ${fmtDateTime(c.canceladoEm)}` : ""}
          </div>
        )}

        <header className="comp-cabecalho">
          <div className="comp-loja">
            {loja.logoUrl && <img src={absUrl(loja.logoUrl)} alt="" className="comp-logo" />}
            <div>
              <div className="comp-loja-nome">{corrigirTexto(loja.nome) ?? ""}</div>
              {loja.endereco && <div>{corrigirEndereco(loja.endereco)}</div>}
              {loja.telefone && <div>Telefone/WhatsApp: {loja.telefone}</div>}
            </div>
          </div>
          <div className="comp-titulo">
            <h1>PEDIDO DE VENDA</h1>
            <div className="comp-naofiscal">Não é documento fiscal</div>
            <dl className="comp-meta">
              <div><dt>Pedido nº</dt><dd>{c.pedidoId}</dd></div>
              <div><dt>Data da venda</dt><dd>{c.dataVenda ? fmtDateTime(c.dataVenda) : NI}</dd></div>
              <div><dt>Vendedor</dt><dd>{c.vendedor ?? NI}</dd></div>
            </dl>
          </div>
        </header>

        {c.legado && (
          <div className="comp-aviso">Pedido anterior ao sistema atual: alguns dados podem estar incompletos (indicados como “{NI}”).</div>
        )}

        <section className="comp-bloco">
          <h2>Cliente</h2>
          <div className="comp-grade">
            <div><span className="comp-rotulo">Nome</span> {c.cliente?.nome ?? "Cliente não identificado"}</div>
            <div><span className="comp-rotulo">Telefone</span> {c.cliente?.telefone ?? NI}</div>
          </div>
          {ent.tipo === "ENTREGA" ? (
            <div className="comp-grade">
              <div>
                <span className="comp-rotulo">Endereço desta entrega</span>
                <Endereco e={ent.enderecoDestaEntrega} />
              </div>
              {cadastral && !mesmoEndereco && (
                <div>
                  <span className="comp-rotulo">Endereço cadastral do cliente</span>
                  <Endereco e={c.cliente?.enderecoCadastral} />
                </div>
              )}
            </div>
          ) : (
            <div>
              <span className="comp-rotulo">Entrega</span> {ent.tipo === "RETIRADA" ? "Retirada na loja" : NI}
            </div>
          )}
        </section>

        <section className="comp-bloco">
          <h2>Produtos</h2>
          <table className="comp-tabela">
            <thead>
              <tr>
                <th>Código</th>
                <th>Produto / variação</th>
                <th className="num">Qtd.</th>
                <th className="num">Preço unit.</th>
                <th className="num">Total</th>
              </tr>
            </thead>
            <tbody>
              {c.itens.map((i, n) => (
                <tr key={n}>
                  <td>{i.sku ?? NI}</td>
                  <td>
                    {i.descricao ?? NI}
                    <div className="comp-peq">
                      {i.modalidade === "ENCOMENDA"
                        ? `Encomenda — previsão de chegada: ${i.previsaoChegada ? fmtDate(i.previsaoChegada) : "a combinar"}`
                        : "Pronta entrega"}
                    </div>
                  </td>
                  <td className="num">{i.quantidade}</td>
                  <td className="num">{fmtMoney(i.precoUnitario)}</td>
                  <td className="num">{fmtMoney(i.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <dl className="comp-totais">
            <div><dt>Subtotal</dt><dd>{fmtMoney(c.subtotal)}</dd></div>
            {Number(c.desconto) > 0 && <div><dt>Desconto</dt><dd>− {fmtMoney(c.desconto)}</dd></div>}
            <div><dt>Frete</dt><dd>{freteTexto}</dd></div>
            <div className="total"><dt>Total da venda</dt><dd>{fmtMoney(c.total)}</dd></div>
          </dl>
          {ajuste !== 0 && (
            <div className="comp-peq">
              Os preços unitários já incluem o ajuste da forma de pagamento ({ajuste > 0 ? "+" : "−"}{Math.abs(ajuste).toLocaleString("pt-BR")}%).
            </div>
          )}
        </section>

        <section className="comp-bloco">
          <h2>Pagamento</h2>
          <div className="comp-grade">
            <div>
              <span className="comp-rotulo">Forma</span>
              {pag.forma ? FORMAS[pag.forma] ?? pag.forma : NI}
              {pag.forma === "CARTAO" && pag.parcelas ? ` em ${pag.parcelas}x` : ""}
            </div>
            <div><span className="comp-rotulo">Situação do pagamento</span> {PAGAMENTO[pag.situacao] ?? NI}</div>
            {pag.financeiroVisivel && (
              <>
                <div><span className="comp-rotulo">Valor pago pelo cliente</span> {fmtMoney(pag.valorPago)}</div>
                <div><span className="comp-rotulo">Saldo pendente</span> {fmtMoney(pag.saldo)}</div>
              </>
            )}
          </div>
          {pag.financeiroVisivel && pag.parcelasRegistradas?.length > 0 && (
            <div className="comp-peq">
              Parcelas: {pag.parcelasRegistradas.map((p) => `${p.numero}ª ${fmtMoney(p.valor)}`).join(" · ")}
            </div>
          )}
        </section>

        <section className="comp-bloco">
          <h2>Entrega e montagem</h2>
          <div className="comp-grade">
            <div><span className="comp-rotulo">Modalidade</span> {ent.tipo === "ENTREGA" ? "Entrega no endereço" : ent.tipo === "RETIRADA" ? "Retirada na loja" : NI}</div>
            <div><span className="comp-rotulo">Frete</span> {freteTexto}</div>
            <div>
              <span className="comp-rotulo">{ent.tipo === "RETIRADA" ? "Retirada" : "Entrega"}</span>
              {entregaAgendada ? `Agendada para ${entregaAgendada}` : ENTREGA[ent.situacao] ?? NI}
              {entregaAgendada && ent.situacao && ent.situacao !== "AGENDADA" ? ` — ${ENTREGA[ent.situacao]}` : ""}
            </div>
            <div>
              <span className="comp-rotulo">Montagem</span>
              {mon.situacao === "NAO_INFORMADO" ? NI : `Inclusa, sem cobrança. ${montagemAgendada ? `Agendada para ${montagemAgendada}.` : `${MONTAGEM[mon.situacao] ?? NI}.`}`}
            </div>
          </div>
          {encomendas.length > 0 && (
            <div className="comp-peq">
              Produtos sob encomenda: a previsão de chegada é informada por item e é diferente do agendamento da entrega.
            </div>
          )}
          {c.observacaoCliente && (
            <div className="comp-obs"><span className="comp-rotulo">Observações</span> {c.observacaoCliente}</div>
          )}
        </section>

        <footer className="comp-rodape">
          <div>Emitido em {fmtDateTime(c.emitidoEm)} · Pedido de venda sem valor fiscal: não é NF-e nem DANFE.</div>
        </footer>
      </article>
    </div>
  );
}
