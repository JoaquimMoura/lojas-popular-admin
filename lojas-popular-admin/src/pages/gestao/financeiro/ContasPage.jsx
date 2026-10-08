import { useState } from "react";
import { useFinanceiroPermissoes } from "../../../components/gestao/financeiro/useFinanceiroPermissoes";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import { financeiroApi } from "../../../services/financeiroApi";
import ErroAlert from "../../../components/gestao/ErroAlert";
import FormModal from "../../../components/gestao/FormModal";
import ModalShell from "../../../components/gestao/ModalShell";
import StatusBadge from "../../../components/gestao/StatusBadge";
import { useChave } from "../../../components/gestao/useChave";
import { useCarga } from "../../../components/gestao/useCarga";
import MotivoModal from "../../../components/gestao/financeiro/MotivoModal";
import { CampoValor, Carregando, TabelaCards, Totais } from "../../../components/gestao/financeiro/Comuns";
import { EVENTOS_CONTA, LIVROS, ORIGENS_CONTA, ROTULOS, TIPOS_CONTA, fmtDate, fmtDateTime, fmtMoney, hojeIso } from "../../../utils/format";

function ContaForm({ conta, onClose, onFeito }) {
  const [tipo, setTipo] = useState(conta?.tipo ?? "PAGAR");
  const [descricao, setDescricao] = useState(conta?.descricao ?? "");
  const [categoria, setCategoria] = useState(conta?.categoria ?? "");
  const [mes, setMes] = useState(conta?.competencia ? String(conta.competencia).slice(0, 7) : hojeIso().slice(0, 7));
  const [valor, setValor] = useState(conta?.valor != null ? String(conta.valor) : "");
  const [venc, setVenc] = useState(conta?.vencimento ?? "");
  const [obs, setObs] = useState(conta?.observacao ?? "");
  const invalido = descricao.trim() === "" || categoria.trim() === "" || !mes || !(Number(valor) > 0) || !venc;
  return (
    <FormModal titulo={conta ? `Editar conta #${conta.id}` : "Nova conta"} submitLabel="Salvar" submitDisabled={invalido}
      onClose={onClose}
      onSubmit={async () => {
        const body = {
          tipo,
          descricao: descricao.trim(),
          categoria: categoria.trim(),
          competencia: `${mes}-01`,
          valor: Number(valor),
          vencimento: venc,
          observacao: obs.trim() || null,
        };
        if (conta) await financeiroApi.atualizarConta(conta.id, body);
        else await financeiroApi.criarConta(body);
        toast.success(conta ? "Conta atualizada." : "Conta criada.");
        onFeito();
      }}>
      <div className="row g-3">
        <div className="col-12 col-sm-4">
          <label className="form-label">Tipo <span className="text-danger">*</span></label>
          <select className="form-select" value={tipo} onChange={(e) => setTipo(e.target.value)}>
            {Object.entries(TIPOS_CONTA).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div className="col-12 col-sm-8">
          <label className="form-label">Descrição <span className="text-danger">*</span></label>
          <input className="form-control" maxLength={200} value={descricao} onChange={(e) => setDescricao(e.target.value)} autoFocus />
        </div>
        <div className="col-12 col-sm-6">
          <label className="form-label">Categoria <span className="text-danger">*</span></label>
          <input className="form-control" maxLength={60} value={categoria} onChange={(e) => setCategoria(e.target.value)} />
        </div>
        <div className="col-12 col-sm-6">
          <label className="form-label">Competência (mês) <span className="text-danger">*</span></label>
          <input type="month" className="form-control" value={mes} onChange={(e) => setMes(e.target.value)} />
          <div className="form-text">Mês já fechado não aceita lançamentos.</div>
        </div>
        <div className="col-12 col-sm-6"><CampoValor label="Valor" obrigatorio value={valor} onChange={setValor} /></div>
        <div className="col-12 col-sm-6">
          <label className="form-label">Vencimento <span className="text-danger">*</span></label>
          <input type="date" className="form-control" value={venc} onChange={(e) => setVenc(e.target.value)} />
        </div>
        <div className="col-12">
          <label className="form-label">Observação</label>
          <textarea className="form-control" rows={2} maxLength={300} value={obs} onChange={(e) => setObs(e.target.value)} />
        </div>
      </div>
    </FormModal>
  );
}

function BaixarModal({ conta, onClose, onFeito }) {
  const [meio, setMeio] = useState("");
  const [data, setData] = useState("");
  const chave = useChave();
  const pagar = conta.tipo === "PAGAR";
  return (
    <FormModal titulo={`${pagar ? "Pagar" : "Receber"} conta #${conta.id}`} submitLabel="Dar baixa" variant="success" size="md"
      submitDisabled={!meio} onClose={onClose}
      onSubmit={async () => {
        await financeiroApi.baixarConta(conta.id, { meio, data: data || null }, chave.obter(`${conta.id}:${meio}:${data}`));
        chave.limpar();
        toast.success("Baixa registrada.");
        onFeito();
      }}>
      <div className="mb-2"><strong>{conta.descricao}</strong> — {fmtMoney(conta.valor)} (venc. {fmtDate(conta.vencimento)})</div>
      <div className="row g-3">
        <div className="col-12 col-sm-6">
          <label className="form-label">{pagar ? "Saiu pelo" : "Entrou pelo"} <span className="text-danger">*</span></label>
          <select className="form-select" value={meio} onChange={(e) => setMeio(e.target.value)} autoFocus>
            <option value="">— selecione —</option>
            {Object.entries(LIVROS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
          <div className="form-text">Caixa exige o caixa físico aberto.</div>
        </div>
        <div className="col-12 col-sm-6">
          <label className="form-label">Data da baixa</label>
          <input type="date" className="form-control" max={hojeIso()} value={data} onChange={(e) => setData(e.target.value)} />
          <div className="form-text">Vazio = hoje.</div>
        </div>
      </div>
    </FormModal>
  );
}

function HistoricoModal({ id, onClose }) {
  const { dados, loading, erro } = useCarga(() => financeiroApi.conta(id), [id]);
  return (
    <ModalShell titulo={`Histórico da conta #${id}`} onClose={onClose}>
      {loading && <Carregando />}
      <ErroAlert erro={erro} />
      {dados && (
        <>
          <div className="mb-2">
            <strong>{dados.descricao}</strong> · {fmtMoney(dados.valor)} · <StatusBadge tipo="conta" valor={dados.situacao} />
            {dados.pagoEm && <div className="small">Baixada em {fmtDate(dados.pagoEm)} ({LIVROS[dados.meio] ?? dados.meio})</div>}
            {dados.pedidoId && <div className="small">Venda <Link to={`/gestao/pedidos/${dados.pedidoId}`}>#{dados.pedidoId}</Link></div>}
          </div>
          {(dados.eventos ?? []).length === 0 ? <div className="text-muted">Sem eventos.</div> : (
            <ul className="gestao-timeline">
              {dados.eventos.map((e) => (
                <li key={e.id}>
                  <div className="fw-semibold">{EVENTOS_CONTA[e.tipo] ?? e.tipo}{e.motivo ? ` — ${e.motivo}` : ""}</div>
                  <div className="small text-muted">{fmtDateTime(e.criadoEm)}{e.usuario ? ` · ${e.usuario}` : ""}</div>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </ModalShell>
  );
}

export default function ContasPage() {
  const { perm } = useFinanceiroPermissoes();
  const [tipo, setTipo] = useState("");
  const [situacao, setSituacao] = useState("");
  const [de, setDe] = useState("");
  const [ate, setAte] = useState("");
  const [modal, setModal] = useState(null); // { acao, conta }
  const { dados, loading, erro, recarregar } = useCarga(
    () => financeiroApi.contas({ tipo, situacao, de, ate }),
    [tipo, situacao, de, ate],
  );
  const lista = Array.isArray(dados) ? dados : [];
  const abertas = (t) => lista.filter((c) => c.tipo === t && c.situacao === "ABERTA");
  const soma = (l) => l.reduce((s, c) => s + Number(c.valor ?? 0), 0);
  const vencidas = lista.filter((c) => c.vencida && c.situacao === "ABERTA");

  function feito() {
    setModal(null);
    recarregar();
  }
  const abrir = (acao, conta) => setModal({ acao, conta });

  return (
    <div>
      <div className="row g-2 mb-3 align-items-end">
        <div className="col-6 col-md-2">
          <label className="form-label small mb-1">Tipo</label>
          <select className="form-select" value={tipo} onChange={(e) => setTipo(e.target.value)}>
            <option value="">Todos</option>
            {Object.entries(TIPOS_CONTA).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div className="col-6 col-md-2">
          <label className="form-label small mb-1">Situação</label>
          <select className="form-select" value={situacao} onChange={(e) => setSituacao(e.target.value)}>
            <option value="">Todas</option>
            {Object.entries(ROTULOS.conta).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div className="col-6 col-md-2">
          <label className="form-label small mb-1">Vencimento de</label>
          <input type="date" className="form-control" value={de} onChange={(e) => setDe(e.target.value)} />
        </div>
        <div className="col-6 col-md-2">
          <label className="form-label small mb-1">até</label>
          <input type="date" className="form-control" value={ate} onChange={(e) => setAte(e.target.value)} />
        </div>
        <div className="col-12 col-md-4 d-grid d-md-flex justify-content-md-end">
          {perm.PAGAR && <button className="btn btn-primary" onClick={() => abrir("form", null)}>Nova conta</button>}
        </div>
      </div>

      <Totais itens={[
        { rotulo: "A pagar (em aberto)", valor: soma(abertas("PAGAR")), nota: `${abertas("PAGAR").length} conta(s)` },
        { rotulo: "A receber (em aberto)", valor: soma(abertas("RECEBER")), nota: `${abertas("RECEBER").length} conta(s)` },
        { rotulo: "Vencidas", texto: String(vencidas.length), classe: vencidas.length ? "text-danger" : "", nota: fmtMoney(soma(vencidas)) },
      ]} />

      <ErroAlert erro={erro} />
      {loading && !dados ? <Carregando texto="Carregando contas..." /> : (
        <TabelaCards
          linhas={lista}
          vazio="Nenhuma conta encontrada."
          destaque={(c) => (c.vencida && c.situacao === "ABERTA" ? "border-danger bg-danger-subtle" : "")}
          colunas={[
            { titulo: "Conta", render: (c) => (
              <span>
                <strong>#{c.id}</strong> {c.descricao}
                <div className="small text-muted">{c.categoria} · {ORIGENS_CONTA[c.origem] ?? c.origem}{c.vendedor ? ` · ${c.vendedor}` : ""}</div>
              </span>
            ) },
            { titulo: "Tipo", render: (c) => TIPOS_CONTA[c.tipo] ?? c.tipo },
            { titulo: "Vencimento", render: (c) => (
              <span className={c.vencida && c.situacao === "ABERTA" ? "text-danger fw-semibold" : ""}>
                {fmtDate(c.vencimento)}{c.vencida && c.situacao === "ABERTA" ? " (vencida)" : ""}
              </span>
            ) },
            { titulo: "Valor", fim: true, render: (c) => fmtMoney(c.valor) },
            { titulo: "Situação", render: (c) => (
              <span><StatusBadge tipo="conta" valor={c.situacao} />{c.pagoEm ? <div className="small">{fmtDate(c.pagoEm)} · {LIVROS[c.meio] ?? c.meio}</div> : null}</span>
            ) },
            { titulo: "Ações", render: (c) => (
              <div className="d-flex flex-wrap gap-1 justify-content-end">
                {c.situacao === "ABERTA" && (c.tipo === "PAGAR" ? perm.PAGAR : perm.RECEBER) && <button className="btn btn-sm btn-success" onClick={() => abrir("baixar", c)}>{c.tipo === "PAGAR" ? "Pagar" : "Receber"}</button>}
                {c.situacao === "ABERTA" && perm.PAGAR && <button className="btn btn-sm btn-outline-primary" onClick={() => abrir("form", c)}>Editar</button>}
                {c.situacao === "PAGA" && perm.ESTORNAR && <button className="btn btn-sm btn-outline-warning" onClick={() => abrir("estornar", c)}>Estornar baixa</button>}
                {c.situacao === "ABERTA" && perm.PAGAR && <button className="btn btn-sm btn-outline-danger" onClick={() => abrir("cancelar", c)}>Cancelar</button>}
                <button className="btn btn-sm btn-outline-secondary" onClick={() => abrir("historico", c)}>Histórico</button>
              </div>
            ) },
          ]}
        />
      )}

      {modal?.acao === "form" && <ContaForm conta={modal.conta} onClose={() => setModal(null)} onFeito={feito} />}
      {modal?.acao === "baixar" && <BaixarModal conta={modal.conta} onClose={() => setModal(null)} onFeito={feito} />}
      {modal?.acao === "historico" && <HistoricoModal id={modal.conta.id} onClose={() => setModal(null)} />}
      {modal?.acao === "estornar" && (
        <EstornarBaixa conta={modal.conta} onClose={() => setModal(null)} onFeito={feito} />
      )}
      {modal?.acao === "cancelar" && (
        <MotivoModal titulo={`Cancelar conta #${modal.conta.id}`} submitLabel="Cancelar conta" onClose={() => setModal(null)}
          onSubmit={async (motivo) => {
            await financeiroApi.cancelarConta(modal.conta.id, motivo);
            toast.success("Conta cancelada.");
            feito();
          }}>
          <div className="alert alert-warning">Cancelar a conta {modal.conta.descricao} ({fmtMoney(modal.conta.valor)}).</div>
        </MotivoModal>
      )}
    </div>
  );
}

function EstornarBaixa({ conta, onClose, onFeito }) {
  const chave = useChave();
  return (
    <MotivoModal titulo={`Estornar baixa da conta #${conta.id}`} submitLabel="Estornar baixa" onClose={onClose}
      onSubmit={async (motivo) => {
        await financeiroApi.estornarConta(conta.id, motivo, chave.obter(`${conta.id}:${motivo}`));
        chave.limpar();
        toast.success("Baixa estornada.");
        onFeito();
      }}>
      <div className="alert alert-warning">
        A baixa de {fmtMoney(conta.valor)} ({LIVROS[conta.meio] ?? conta.meio}) será estornada e a conta volta a ficar em aberto.
      </div>
    </MotivoModal>
  );
}
