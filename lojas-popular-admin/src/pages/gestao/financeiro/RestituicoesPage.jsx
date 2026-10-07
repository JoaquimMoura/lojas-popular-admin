import { useState } from "react";
import { Link } from "react-router-dom";
import { financeiroApi } from "../../../services/financeiroApi";
import ErroAlert from "../../../components/gestao/ErroAlert";
import StatusBadge from "../../../components/gestao/StatusBadge";
import { useCarga } from "../../../components/gestao/useCarga";
import { AcoesRestituicao, RestituicaoResumo } from "../../../components/gestao/financeiro/RestituicaoBloco";
import { Carregando } from "../../../components/gestao/financeiro/Comuns";
import { FORMAS, ROTULOS, fmtMoney } from "../../../utils/format";

export default function RestituicoesPage() {
  const [status, setStatus] = useState("");
  const { dados, loading, erro, recarregar } = useCarga(() => financeiroApi.restituicoes(status), [status]);
  const lista = Array.isArray(dados) ? dados : [];
  return (
    <div>
      <div className="alert alert-info small">
        Restituição é a devolução do <strong>dinheiro</strong> ao cliente, na forma da venda. Fluxo: solicitar (na ocorrência
        de pós-venda) &rarr; autorizar (outra pessoa) &rarr; efetivar. A devolução física do produto é um controle separado.
      </div>
      <div className="row g-2 mb-3">
        <div className="col-12 col-md-4">
          <label className="form-label small mb-1">Situação</label>
          <select className="form-select" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Todas</option>
            {Object.entries(ROTULOS.restituicao).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
      </div>
      <ErroAlert erro={erro} />
      {loading && !dados ? <Carregando /> : lista.length === 0 ? (
        <div className="text-center text-muted py-4">Nenhuma restituição encontrada.</div>
      ) : (
        <div className="d-grid gap-2">
          {lista.map((r) => (
            <div key={r.id} className="card">
              <div className="card-body">
                <div className="d-flex flex-wrap justify-content-between gap-2">
                  <div>
                    <strong>{fmtMoney(r.valor)}</strong> <span className="small">({FORMAS[r.forma] ?? r.forma})</span>
                    <div className="small">
                      Pedido <Link to={`/gestao/pedidos/${r.pedidoId}`}>#{r.pedidoId}</Link> · Ocorrência{" "}
                      <Link to={`/gestao/pos-venda/${r.ocorrenciaId}`}>#{r.ocorrenciaId}</Link>
                    </div>
                  </div>
                  <StatusBadge tipo="restituicao" valor={r.status} />
                </div>
                <div className="mt-1">{r.motivo}</div>
                <RestituicaoResumo r={r} />
                <AcoesRestituicao r={r} onFeito={recarregar} />
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
