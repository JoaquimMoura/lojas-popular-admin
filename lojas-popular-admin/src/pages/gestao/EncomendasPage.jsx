import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import { encomendasApi } from "../../services/encomendasApi";
import ErroAlert from "../../components/gestao/ErroAlert";
import FormModal from "../../components/gestao/FormModal";
import StatusBadge from "../../components/gestao/StatusBadge";
import { useChave } from "../../components/gestao/useChave";
import { ROTULOS, fmtDate, fmtDateTime } from "../../utils/format";

const ABERTAS = ["AGUARDANDO_PEDIDO", "PEDIDO_REALIZADO", "PARCIALMENTE_RECEBIDA"];

function EditarModal({ e, onClose, onSalvo }) {
  const [fornecedor, setFornecedor] = useState(e.fornecedor ?? "");
  const [referencia, setReferencia] = useState(e.referenciaFornecedor ?? "");
  const [previsao, setPrevisao] = useState(e.previsaoChegada ?? "");
  const [obs, setObs] = useState(e.observacao ?? "");
  return (
    <FormModal titulo={`Editar encomenda #${e.id}`} submitLabel="Salvar" onClose={onClose}
      onSubmit={async () => {
        const r = await encomendasApi.atualizar(e.id, {
          fornecedor: fornecedor.trim() || null,
          referenciaFornecedor: referencia.trim() || null,
          previsaoChegada: previsao || null,
          observacao: obs.trim() || null,
        });
        toast.success("Encomenda atualizada.");
        onSalvo(r);
      }}>
      <div className="fw-semibold mb-2">{e.descricao}</div>
      <div className="row g-3">
        <div className="col-12 col-sm-6">
          <label className="form-label">Fornecedor</label>
          <input className="form-control" maxLength={150} value={fornecedor} onChange={(x) => setFornecedor(x.target.value)} />
        </div>
        <div className="col-12 col-sm-6">
          <label className="form-label">Referência do fornecedor</label>
          <input className="form-control" maxLength={100} value={referencia} onChange={(x) => setReferencia(x.target.value)} />
        </div>
        <div className="col-12 col-sm-6">
          <label className="form-label">Previsão de chegada</label>
          <input type="date" className="form-control" value={previsao} onChange={(x) => setPrevisao(x.target.value)} />
        </div>
        <div className="col-12">
          <label className="form-label">Observação</label>
          <textarea className="form-control" rows={2} maxLength={300} value={obs} onChange={(x) => setObs(x.target.value)} />
        </div>
      </div>
    </FormModal>
  );
}

function ReceberModal({ e, onClose, onRecebida }) {
  const jaRecebido = e.quantidadeRecebida ?? 0;
  const restante = Math.max(e.quantidade - jaRecebido, 0);
  const [qtd, setQtd] = useState(String(restante));
  const chave = useChave();
  const n = Number(qtd);
  const completa = Number.isInteger(n) && n >= restante;
  return (
    <FormModal titulo={`Receber encomenda #${e.id}`} submitLabel="Registrar recebimento" variant="success" size="md"
      submitDisabled={!Number.isInteger(n) || n < 1 || n > restante} onClose={onClose}
      onSubmit={async () => {
        const r = await encomendasApi.receber(e.id, n, chave.obter(`${e.id}:${jaRecebido}:${n}`));
        chave.limpar();
        toast.success(completa ? "Recebimento completo registrado." : "Recebimento parcial registrado.");
        onRecebida(r);
      }}>
      <div className="fw-semibold mb-2">
        {e.descricao} — vendido: {e.quantidade} un. · já recebido: {jaRecebido} un. · falta: {restante} un.
      </div>
      <div className="alert alert-info">
        Pode informar menos do que o vendido: o recebimento parcial do <strong>fornecedor</strong> gera a entrada no
        estoque e reserva as unidades ao cliente aos poucos. Isso <strong>não é entrega parcial ao cliente</strong>: a
        entrega só continua quando tudo estiver recebido e reservado.
      </div>
      <label className="form-label">Quantidade recebida nesta remessa <span className="text-danger">*</span></label>
      <input type="number" min="1" max={restante} className="form-control" value={qtd}
        onChange={(x) => setQtd(x.target.value)} autoFocus />
      <div className={`form-text ${n > restante ? "text-danger" : ""}`}>
        {n > restante ? `Máximo de ${restante} un. (o que ainda falta).` : completa ? "Completa o pedido do cliente." : `Ficarão faltando ${restante - n} un.`}
      </div>
    </FormModal>
  );
}

export default function EncomendasPage() {
  const [lista, setLista] = useState([]);
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState(null);
  const [modal, setModal] = useState(null); // { tipo: 'editar' | 'receber', e }

  const carregar = useCallback(async () => {
    setLoading(true);
    setErro(null);
    try {
      const d = await encomendasApi.listar(status);
      setLista(Array.isArray(d) ? d : []);
    } catch (err) {
      setErro(err);
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  function atualizado() {
    setModal(null);
    carregar();
  }

  const atrasadas = lista.filter((e) => e.atrasada).length;

  return (
    <div>
      <h3 className="mb-3">Encomendas</h3>

      <div className="row g-2 mb-3 align-items-end">
        <div className="col-12 col-md-4">
          <label className="form-label small mb-1">Situação</label>
          <select className="form-select" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Todas</option>
            {Object.entries(ROTULOS.encomenda).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
        </div>
        {atrasadas > 0 && (
          <div className="col-12 col-md-auto">
            <span className="badge text-bg-danger fs-6">{atrasadas} atrasada(s)</span>
          </div>
        )}
      </div>

      <ErroAlert erro={erro} />

      {loading ? (
        <div className="text-center text-muted py-5">Carregando encomendas...</div>
      ) : lista.length === 0 ? (
        <div className="text-center text-muted py-5">Nenhuma encomenda encontrada.</div>
      ) : (
        <div className="d-grid gap-2">
          {lista.map((e) => {
            const aberta = ABERTAS.includes(e.status);
            return (
              <div key={e.id} className={`card ${e.atrasada ? "border-danger" : ""}`}>
                <div className="card-body">
                  <div className="d-flex flex-wrap justify-content-between gap-2">
                    <div>
                      <div className="fw-semibold">{e.descricao} <span className="fw-normal">({e.quantidade} un.)</span></div>
                      <div className="small">
                        Pedido <Link to={`/gestao/pedidos/${e.pedidoId}`}>#{e.pedidoId}</Link> · {e.cliente ?? "—"}
                      </div>
                    </div>
                    <div>
                      <StatusBadge tipo="encomenda" valor={e.status} />
                      {e.atrasada && <span className="badge text-bg-danger ms-1">Atrasada</span>}
                    </div>
                  </div>
                  <div className="small mt-2">
                    Fornecedor: {e.fornecedor || "—"} · Referência: {e.referenciaFornecedor || "—"}
                  </div>
                  <div className={`small ${e.atrasada ? "text-danger fw-semibold" : ""}`}>
                    Previsão de chegada: {e.previsaoChegada ? fmtDate(e.previsaoChegada) : "—"}
                  </div>
                  {e.prazoPadrao && <div className="small text-muted">Prazo padrão: {e.prazoPadrao}</div>}
                  <div className="small">Reserva: {e.reserva ? <StatusBadge tipo="reserva" valor={e.reserva} /> : "—"}</div>
                  <div className="small">
                    Vendido: <strong>{e.quantidade}</strong> · Recebido do fornecedor: <strong>{e.quantidadeRecebida ?? 0}</strong>
                    {" "}· Reservado ao cliente: <strong>{e.quantidadeReservada ?? 0}</strong>
                    {" "}· Faltante: <strong className={(e.quantidadeFaltante ?? 0) > 0 ? "text-danger" : ""}>{e.quantidadeFaltante ?? 0}</strong>
                  </div>
                  {e.status === "PARCIALMENTE_RECEBIDA" && (
                    <div className="small text-muted">
                      Recebimento parcial do fornecedor: a entrega ao cliente continua só quando tudo estiver recebido e reservado.
                    </div>
                  )}
                  {e.recebidaEm && (
                    <div className="small text-muted">Recebida por completo em {fmtDateTime(e.recebidaEm)}</div>
                  )}
                  {(e.recebimentos ?? []).length > 0 && (
                    <details className="mt-1">
                      <summary className="small">Histórico de recebimentos ({e.recebimentos.length})</summary>
                      <ul className="small mb-0 mt-1 ps-3">
                        {e.recebimentos.map((r) => (
                          <li key={r.id}>
                            {r.quantidade} un. em {fmtDateTime(r.recebidoEm)}{r.usuario ? ` por ${r.usuario}` : ""}
                            {r.observacao ? ` — ${r.observacao}` : ""}
                          </li>
                        ))}
                      </ul>
                    </details>
                  )}
                  {e.observacao && <div className="small text-muted">{e.observacao}</div>}
                  {aberta && (
                    <div className="d-grid d-sm-flex gap-2 mt-3">
                      <button className="btn btn-outline-primary" onClick={() => setModal({ tipo: "editar", e })}>Editar</button>
                      <button className="btn btn-success" onClick={() => setModal({ tipo: "receber", e })}>Receber</button>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {modal?.tipo === "editar" && <EditarModal e={modal.e} onClose={() => setModal(null)} onSalvo={atualizado} />}
      {modal?.tipo === "receber" && <ReceberModal e={modal.e} onClose={() => setModal(null)} onRecebida={atualizado} />}
    </div>
  );
}
