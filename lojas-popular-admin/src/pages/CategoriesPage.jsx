import { useEffect, useMemo, useState } from "react";
import { categoriesApi } from "../services/categoriesApi";
import { useAuth } from "../context/AuthContext";
import CategoryForm from "../components/CategoryForm";
import ErroAlert from "../components/gestao/ErroAlert";
import FiltroAutocomplete, { SemResultados } from "../components/FiltroAutocomplete";
import { casaBusca } from "../utils/busca";

function Pendencias({ categoria }) {
  const [aberto, setAberto] = useState(false);
  const [lista, setLista] = useState(null);

  async function alternar() {
    const novo = !aberto;
    setAberto(novo);
    if (novo && lista === null) {
      try {
        setLista(await categoriesApi.pendencias(categoria.id));
      } catch {
        setLista([]);
      }
    }
  }

  const n = categoria.produtosPendentes;
  return (
    <div className="alert alert-warning py-2 px-3 mt-2 mb-0 small">
      <div className="d-flex flex-wrap align-items-center gap-2">
        <span>
          {n} {n === 1 ? "produto precisa" : "produtos precisam"} de complementação
        </span>
        <button type="button" className="btn btn-link btn-sm p-0" onClick={alternar}>
          {aberto ? "Esconder" : "Ver quais"}
        </button>
      </div>
      {aberto && (
        <ul className="mb-0 mt-2 ps-3">
          {lista === null && <li>Carregando...</li>}
          {lista?.map((p) => (
            <li key={p.id}>
              {p.nome}
              {p.faltando?.length ? <span className="text-muted"> - falta: {p.faltando.join(", ")}</span> : null}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default function CategoriesPage() {
  const { user } = useAuth() ?? {};
  const podeConfigurar = !!user?.roles?.some((r) => r === "ADMIN" || r === "GERENTE");

  const [items, setItems] = useState([]);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(null);
  const [erro, setErro] = useState(null);
  const [busca, setBusca] = useState("");

  async function load() {
    try {
      const resp = await categoriesApi.list();
      setItems(Array.isArray(resp) ? resp : resp.content ?? []);
    } catch (e) {
      console.error("Erro ao carregar categorias:", e);
      setItems([]);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const filtradas = useMemo(
    () => (items ?? []).filter((c) => casaBusca(busca, c.nome, c.descricao, (c.materiais ?? []).map((m) => m.nome))),
    [items, busca],
  );

  // Os formulários recebem o erro (rejeição) e o exibem sem fechar.
  async function onCreate(payload) {
    await categoriesApi.create(payload);
    setCreating(false);
    await load();
  }

  async function onUpdate(payload) {
    await categoriesApi.update(editing.id, payload);
    setEditing(null);
    await load();
  }

  async function onDelete(id) {
    if (!confirm("Confirma excluir a categoria?")) return;
    setErro(null);
    try {
      await categoriesApi.remove(id);
      await load();
    } catch (e) {
      setErro(e);
    }
  }

  async function abrirEdicao(c) {
    setErro(null);
    try {
      // dados completos e atualizados (inclui características e produtosComValor)
      setEditing(await categoriesApi.byId(c.id));
    } catch {
      setEditing(c);
    }
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  return (
    <div className="container-fluid px-2 px-md-3">
      <div className="d-flex justify-content-between align-items-center mb-3 gap-2 flex-wrap">
        <h3 className="mb-0">Categorias</h3>
        {!creating && !editing && (
          <button className="btn btn-success" onClick={() => setCreating(true)}>
            + Nova categoria
          </button>
        )}
      </div>

      <ErroAlert erro={erro} onClose={() => setErro(null)} />

      {creating && (
        <div className="card mb-3">
          <div className="card-body">
            <h5 className="card-title">Nova categoria</h5>
            <CategoryForm podeConfigurar={podeConfigurar} onSubmit={onCreate} onCancel={() => setCreating(false)} />
          </div>
        </div>
      )}

      {editing && (
        <div className="card mb-3">
          <div className="card-body">
            <h5 className="card-title">Editar categoria</h5>
            <CategoryForm
              key={editing.id}
              initial={editing}
              podeConfigurar={podeConfigurar}
              onSubmit={onUpdate}
              onCancel={() => setEditing(null)}
            />
          </div>
        </div>
      )}

      <div className="mb-3">
        <FiltroAutocomplete
          value={busca}
          onChange={setBusca}
          itens={items}
          getRotulo={(c) => c.nome}
          getDetalhe={(c) => (c.materiais ?? []).map((m) => m.nome).join(", ")}
          getTextoBusca={(c) => [c.nome, c.descricao, (c.materiais ?? []).map((m) => m.nome)]}
          placeholder="Buscar categoria por nome ou material"
        />
      </div>

      <div className="row g-3">
        {filtradas.map((c) => (
          <div className="col-12 col-lg-6" key={c.id}>
            <div className="card h-100">
              <div className="card-body">
                <div className="d-flex justify-content-between align-items-start gap-2">
                  <div className="min-w-0">
                    <h5 className="mb-1 text-break">{c.nome}</h5>
                    {c.descricao && <div className="text-muted small text-break">{c.descricao}</div>}
                  </div>
                  <div className="btn-group btn-group-sm flex-shrink-0">
                    <button className="btn btn-outline-primary" onClick={() => abrirEdicao(c)}>
                      Editar
                    </button>
                    <button className="btn btn-outline-danger" onClick={() => onDelete(c.id)}>
                      Excluir
                    </button>
                  </div>
                </div>

                {c.materiais?.length > 0 && (
                  <div className="d-flex flex-wrap gap-1 mt-2">
                    {c.materiais.map((m) => (
                      <span
                        key={m.id}
                        className="badge rounded-pill"
                        style={{ background: "var(--color-brand-yellow-light)", color: "var(--color-ink)" }}
                      >
                        {m.nome}
                        {m.ativo === false ? " (inativo)" : ""}
                      </span>
                    ))}
                  </div>
                )}

                {c.caracteristicas?.length > 0 && (
                  <div className="text-muted small mt-2">
                    Características: {c.caracteristicas.filter((x) => x.ativa !== false).map((x) => x.nome).join(", ") || "nenhuma ativa"}
                  </div>
                )}

                {c.produtosPendentes > 0 && <Pendencias categoria={c} />}
              </div>
            </div>
          </div>
        ))}
        {(items?.length ?? 0) === 0 && <div className="col-12 text-center text-muted">Nenhuma categoria cadastrada</div>}
        {(items?.length ?? 0) > 0 && filtradas.length === 0 && (
          <div className="col-12"><SemResultados busca={busca} /></div>
        )}
      </div>
    </div>
  );
}
