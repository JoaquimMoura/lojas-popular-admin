import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { toast } from "react-toastify";
import { posVendaApi } from "../../services/posVendaApi";
import ErroAlert from "../../components/gestao/ErroAlert";
import FormModal from "../../components/gestao/FormModal";
import StatusBadge from "../../components/gestao/StatusBadge";
import ArquivoInput from "../../components/gestao/ArquivoInput";
import ArquivoLink from "../../components/gestao/ArquivoLink";
import { Secao, Linha } from "../../components/gestao/Secao";
import { useChave } from "../../components/gestao/useChave";
import RestituicaoBloco from "../../components/gestao/financeiro/RestituicaoBloco";
import { ROTULOS, arquivoGrande, fmtDateTime, fmtMoney } from "../../utils/format";

function EvidenciaModal({ o, onClose, onSalvo }) {
  const [arquivo, setArquivo] = useState(null);
  const [descricao, setDescricao] = useState("");
  return (
    <FormModal titulo="Anexar evidência" submitLabel="Anexar" size="md"
      submitDisabled={!arquivo || arquivoGrande(arquivo)} onClose={onClose}
      onSubmit={async () => {
        const fd = new FormData();
        fd.append("arquivo", arquivo);
        if (descricao.trim()) fd.append("descricao", descricao.trim());
        const r = await posVendaApi.anexarEvidencia(o.id, fd);
        toast.success("Evidência anexada.");
        onSalvo(r);
      }}>
      <div className="row g-3">
        <div className="col-12">
          <ArquivoInput label="Arquivo (foto ou PDF)" arquivo={arquivo} onChange={setArquivo} obrigatorio />
        </div>
        <div className="col-12">
          <label className="form-label">Descrição</label>
          <input className="form-control" maxLength={300} value={descricao} onChange={(e) => setDescricao(e.target.value)} />
        </div>
      </div>
    </FormModal>
  );
}

function DevolucaoModal({ o, onClose, onSalvo }) {
  const [condicao, setCondicao] = useState("");
  const [avaliacao, setAvaliacao] = useState("");
  const chave = useChave();
  const naoApta = condicao === "NAO_APTA";
  return (
    <FormModal titulo="Receber devolução" submitLabel="Registrar recebimento" variant="success"
      submitDisabled={!condicao || (naoApta && avaliacao.trim() === "")} onClose={onClose}
      onSubmit={async () => {
        const r = await posVendaApi.receberDevolucao(
          o.id,
          { condicao, avaliacao: avaliacao.trim() || null },
          chave.obter(`${o.id}:${condicao}:${avaliacao.trim()}`),
        );
        chave.limpar();
        toast.success("Devolução recebida.");
        onSalvo(r);
      }}>
      <div className="alert alert-info">
        Somente produtos <strong>aptos para revenda</strong> voltam ao estoque. Produtos não aptos não geram entrada.
      </div>
      <div className="row g-3">
        <div className="col-12">
          <label className="form-label">Condição física <span className="text-danger">*</span></label>
          <select className="form-select" value={condicao} onChange={(e) => setCondicao(e.target.value)}>
            <option value="">— selecione —</option>
            {Object.entries(ROTULOS.condicao).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
        </div>
        <div className="col-12">
          <label className="form-label">Avaliação{naoApta && <span className="text-danger"> *</span>}</label>
          <textarea className="form-control" rows={3} maxLength={300} value={avaliacao}
            onChange={(e) => setAvaliacao(e.target.value)} />
          {naoApta && <div className="form-text">Obrigatória quando o produto não está apto para revenda.</div>}
        </div>
      </div>
    </FormModal>
  );
}

export default function PosVendaDetalhePage() {
  const { id } = useParams();
  const [o, setO] = useState(null);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState(null);
  const [modal, setModal] = useState(null);

  const carregar = useCallback(async () => {
    setLoading(true);
    setErro(null);
    try {
      setO(await posVendaApi.obter(id));
    } catch (err) {
      setErro(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  function atualizado(r) {
    setO(r);
    setModal(null);
  }

  if (loading && !o) return <div className="text-center text-muted py-5">Carregando ocorrência...</div>;
  if (erro && !o) {
    return (
      <div>
        <ErroAlert erro={erro} />
        <Link to="/gestao/pos-venda" className="btn btn-outline-secondary">Voltar ao pós-venda</Link>
      </div>
    );
  }
  if (!o) return null;

  const aberta = o.status === "ABERTA";
  const devolucaoPendente = aberta && (o.tipo === "TROCA" || o.tipo === "DEVOLUCAO");
  const encerrada = o.status === "RESOLVIDA" || o.status === "CANCELADA";
  const financeiro = o.bloqueios?.solucaoFinanceira;

  return (
    <div>
      <div className="mb-2">
        <Link to="/gestao/pos-venda" className="small">&larr; Pós-venda</Link>
      </div>

      <div className="card mb-3">
        <div className="card-body">
          <div className="d-flex flex-wrap justify-content-between align-items-start gap-2">
            <h3 className="mb-0">Ocorrência #{o.id}</h3>
            <span>
              <StatusBadge tipo="tipoOcorrencia" valor={o.tipo} className="me-1" />
              <StatusBadge tipo="ocorrencia" valor={o.status} />
            </span>
          </div>
          <div className="mt-1">
            Pedido <Link to={`/gestao/pedidos/${o.pedidoId}`}>#{o.pedidoId}</Link> · {o.cliente ?? "—"}
          </div>
        </div>
      </div>

      {financeiro && <div className="alert alert-warning">{financeiro}</div>}

      <div className="row">
        <div className="col-lg-6">
          <Secao titulo="Dados">
            <Linha rotulo="Descrição">{o.descricao}</Linha>
            <Linha rotulo="Item">{o.item ?? "—"}</Linha>
            <Linha rotulo="Quantidade">{o.quantidade ?? "—"}</Linha>
            <Linha rotulo="Aberta por">{o.abertaPor ?? "—"}</Linha>
            <Linha rotulo="Aberta em">{fmtDateTime(o.criadaEm)}</Linha>
            {o.solucao && <Linha rotulo="Solução">{o.solucao}</Linha>}
            {o.resolvidaEm && <Linha rotulo="Resolvida em">{fmtDateTime(o.resolvidaEm)}</Linha>}
            {o.motivoCancelamento && <Linha rotulo="Motivo do cancelamento">{o.motivoCancelamento}</Linha>}
          </Secao>
        </div>

        <div className="col-lg-6">
          {o.tipo === "TROCA" && (
            <Secao titulo="Troca">
              <Linha rotulo="Produto da troca">{o.trocaItem ?? "—"}</Linha>
              <Linha rotulo="Quantidade">{o.trocaQuantidade ?? "—"}</Linha>
              <Linha rotulo="Diferença calculada">
                {fmtMoney(o.diferencaCalculada)}
                <div className="small text-muted">cálculo informativo — a cobrança ou restituição é feita no bloco financeiro abaixo</div>
              </Linha>
            </Secao>
          )}
          {(o.condicaoFisica || o.devolucaoRecebidaEm) && (
            <Secao titulo="Devolução recebida">
              <Linha rotulo="Condição física">
                <StatusBadge tipo="condicao" valor={o.condicaoFisica} />
              </Linha>
              {o.avaliacao && <Linha rotulo="Avaliação">{o.avaliacao}</Linha>}
              <Linha rotulo="Recebida em">{fmtDateTime(o.devolucaoRecebidaEm)}</Linha>
              <Linha rotulo="Estoque reposto">{o.estoqueReposto ? "Sim" : "Não"}</Linha>
            </Secao>
          )}
        </div>
      </div>

      <RestituicaoBloco o={o} onFeito={carregar} />

      <Secao titulo="Evidências">
        {(o.evidencias ?? []).length === 0 ? (
          <div className="text-muted">Nenhuma evidência anexada.</div>
        ) : (
          <ul className="list-unstyled mb-0">
            {o.evidencias.map((e) => (
              <li key={e.id} className="py-1 border-bottom">
                <ArquivoLink caminho={e.arquivo}>{e.descricao || "Abrir arquivo"}</ArquivoLink>
                <div className="small text-muted">
                  {e.enviadoPor ? `${e.enviadoPor} · ` : ""}
                  {fmtDateTime(e.criadoEm)}
                </div>
              </li>
            ))}
          </ul>
        )}
        {o.status !== "CANCELADA" && (
          <div className="d-grid d-sm-flex mt-3">
            <button className="btn btn-outline-primary" onClick={() => setModal("evidencia")}>Anexar evidência</button>
          </div>
        )}
      </Secao>

      {!encerrada && (
        <div className="card mb-3">
          <div className="card-body d-grid gap-2">
            {devolucaoPendente && (
              <button className="btn btn-success" onClick={() => setModal("devolucao")}>Receber devolução</button>
            )}
            <div className="d-grid d-sm-flex align-items-sm-center gap-2">
              <button className="btn btn-primary" disabled={devolucaoPendente} onClick={() => setModal("resolver")}>
                Resolver
              </button>
              {devolucaoPendente && (
                <span className="text-danger small">Receba e avalie a devolução física antes de resolver a ocorrência.</span>
              )}
            </div>
            {aberta && (
              <button className="btn btn-outline-danger" onClick={() => setModal("cancelar")}>Cancelar ocorrência</button>
            )}
          </div>
        </div>
      )}

      {modal === "evidencia" && <EvidenciaModal o={o} onClose={() => setModal(null)} onSalvo={atualizado} />}
      {modal === "devolucao" && <DevolucaoModal o={o} onClose={() => setModal(null)} onSalvo={atualizado} />}
      {modal === "resolver" && <TextoModal titulo="Resolver ocorrência" rotulo="Solução" submitLabel="Resolver"
        onClose={() => setModal(null)} max={500}
        onSubmit={async (solucao) => {
          const r = await posVendaApi.resolver(o.id, { solucao });
          toast.success("Ocorrência resolvida.");
          atualizado(r);
        }}>
        {financeiro && <div className="alert alert-warning">{financeiro}</div>}
      </TextoModal>}
      {modal === "cancelar" && <TextoModal titulo="Cancelar ocorrência" rotulo="Motivo" submitLabel="Cancelar ocorrência"
        variant="danger" onClose={() => setModal(null)} max={300}
        onSubmit={async (motivo) => {
          const r = await posVendaApi.cancelar(o.id, { motivo });
          toast.success("Ocorrência cancelada.");
          atualizado(r);
        }} />}
    </div>
  );
}

function TextoModal({ titulo, rotulo, submitLabel, variant = "primary", max, onClose, onSubmit, children }) {
  const [texto, setTexto] = useState("");
  return (
    <FormModal titulo={titulo} submitLabel={submitLabel} variant={variant} size="md"
      submitDisabled={texto.trim() === ""} onClose={onClose} onSubmit={() => onSubmit(texto.trim())}>
      {children}
      <label className="form-label">{rotulo} <span className="text-danger">*</span></label>
      <textarea className="form-control" rows={4} maxLength={max} value={texto}
        onChange={(e) => setTexto(e.target.value)} autoFocus />
    </FormModal>
  );
}
