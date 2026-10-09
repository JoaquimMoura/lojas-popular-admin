import { useCallback, useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { toast } from "react-toastify";
import { clientesApi } from "../../services/clientesApi";
import ClienteForm from "../../components/gestao/ClienteForm";
import ClienteHistorico from "../../components/gestao/ClienteHistorico";
import ErroAlert from "../../components/gestao/ErroAlert";

export default function ClienteDetalhePage() {
  const { id } = useParams();
  const [params, setParams] = useSearchParams();
  const aba = params.get("aba") === "historico" ? "historico" : "dados";
  const [cliente, setCliente] = useState(null);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState(null);

  const carregar = useCallback(async () => {
    setLoading(true);
    setErro(null);
    try {
      setCliente(await clientesApi.obter(id));
    } catch (err) {
      setErro(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  function trocarAba(nova) {
    setParams(nova === "historico" ? { aba: "historico" } : {}, { replace: true });
  }

  if (loading && !cliente) return <div className="text-center text-muted py-5">Carregando cliente...</div>;
  if (!cliente) {
    return (
      <div>
        <ErroAlert erro={erro} />
        <Link to="/gestao/clientes" className="btn btn-outline-secondary">Voltar aos clientes</Link>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-2">
        <Link to="/gestao/clientes" className="small">&larr; Clientes</Link>
      </div>
      <div className="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-1">
        <h3 className="mb-0">{cliente.nome}</h3>
        {!cliente.ativo && <span className="badge text-bg-secondary">Inativo</span>}
      </div>
      <div className="small text-muted mb-3">
        {cliente.telefone || "Sem telefone"}
        {cliente.email ? ` · ${cliente.email}` : ""}
        {cliente.cpf ? ` · CPF ${cliente.cpf}` : ""}
      </div>

      <ul className="nav nav-tabs gestao-abas mb-3" role="tablist">
        <li className="nav-item" role="presentation">
          <button type="button" role="tab" aria-selected={aba === "dados"}
            className={`nav-link${aba === "dados" ? " active" : ""}`} onClick={() => trocarAba("dados")}>
            Dados
          </button>
        </li>
        <li className="nav-item" role="presentation">
          <button type="button" role="tab" aria-selected={aba === "historico"}
            className={`nav-link${aba === "historico" ? " active" : ""}`} onClick={() => trocarAba("historico")}>
            Histórico de compras
          </button>
        </li>
      </ul>

      {aba === "dados" ? (
        <div className="card">
          <div className="card-header">Cadastro</div>
          <div className="card-body">
            <ClienteForm
              key={cliente.id}
              initial={cliente}
              onSaved={(c) => {
                toast.success(`Cliente ${c.nome} salvo.`);
                setCliente(c);
              }}
              onCancel={() => trocarAba("historico")}
            />
          </div>
        </div>
      ) : (
        <ClienteHistorico cliente={cliente} />
      )}
    </div>
  );
}
