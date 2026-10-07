import { useEffect, useState } from "react";
import { toast } from "react-toastify";
import { clientesApi } from "../../services/clientesApi";
import ClienteForm from "../../components/gestao/ClienteForm";
import ErroAlert from "../../components/gestao/ErroAlert";
import { useAuth } from "../../context/AuthContext";
import { isGestor } from "../../utils/format";

export default function ClientesPage() {
  const { user } = useAuth();
  const gestor = isGestor(user);
  const [busca, setBusca] = useState("");
  const [q, setQ] = useState("");
  const [lista, setLista] = useState([]);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState(null);
  const [form, setForm] = useState(null); // null | { cliente: obj|null }
  const [recarga, setRecarga] = useState(0);

  useEffect(() => {
    let ativo = true;
    setLoading(true);
    setErro(null);
    clientesApi
      .buscar(q)
      .then((d) => ativo && setLista(Array.isArray(d) ? d : []))
      .catch((err) => ativo && setErro(err))
      .finally(() => ativo && setLoading(false));
    return () => {
      ativo = false;
    };
  }, [q, recarga]);

  function buscar(e) {
    e.preventDefault();
    setQ(busca.trim());
  }

  function salvo(c) {
    toast.success(`Cliente ${c.nome} salvo.`);
    setForm(null);
    setRecarga((n) => n + 1);
  }

  async function desativar(c) {
    if (!window.confirm(`Desativar o cliente ${c.nome}?`)) return;
    try {
      await clientesApi.desativar(c.id);
      toast.success("Cliente desativado.");
      setRecarga((n) => n + 1);
    } catch (err) {
      setErro(err);
    }
  }

  if (form) {
    return (
      <div className="card">
        <div className="card-header">{form.cliente ? "Editar cliente" : "Novo cliente"}</div>
        <div className="card-body">
          <ClienteForm initial={form.cliente} onSaved={salvo} onCancel={() => setForm(null)} />
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-3 gap-2">
        <h3 className="mb-0">Clientes</h3>
        <button className="btn btn-success" onClick={() => setForm({ cliente: null })}>
          + Novo cliente
        </button>
      </div>

      <form className="mb-1" onSubmit={buscar}>
        <div className="input-group">
          <input
            type="search"
            className="form-control"
            placeholder="Buscar por nome, CPF ou telefone"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
          <button className="btn btn-primary" type="submit">Buscar</button>
        </div>
      </form>
      <div className="form-text mb-3">Busque antes de cadastrar para evitar clientes duplicados.</div>

      <ErroAlert erro={erro} onClose={() => setErro(null)} />

      {loading ? (
        <div className="text-center text-muted py-5">Carregando clientes...</div>
      ) : lista.length === 0 ? (
        <div className="text-center text-muted py-5">Nenhum cliente encontrado.</div>
      ) : (
        <div className="row g-2">
          {lista.map((c) => {
            const principal = (c.enderecos ?? []).find((e) => e.principal) ?? c.enderecos?.[0];
            return (
              <div key={c.id} className="col-12 col-md-6 col-xl-4">
                <div className="card h-100">
                  <div className="card-body">
                    <div className="d-flex justify-content-between">
                      <strong>{c.nome}</strong>
                      {!c.ativo && <span className="badge text-bg-secondary">Inativo</span>}
                    </div>
                    <div className="small text-muted">
                      {c.telefone || "Sem telefone"}
                      {c.cpf ? ` · CPF ${c.cpf}` : ""}
                    </div>
                    {c.email && <div className="small text-muted">{c.email}</div>}
                    {principal && (
                      <div className="small mt-1">
                        {principal.logradouro}
                        {principal.numero ? `, ${principal.numero}` : ""} — {principal.cidade}/{principal.uf}
                      </div>
                    )}
                    <div className="d-grid d-sm-flex gap-2 mt-3">
                      <button className="btn btn-outline-primary btn-sm" onClick={() => setForm({ cliente: c })}>
                        Editar
                      </button>
                      {c.ativo && gestor && (
                        <button className="btn btn-outline-danger btn-sm" onClick={() => desativar(c)}>
                          Desativar
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
