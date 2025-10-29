import { useEffect, useState } from "react";
import { categoriesApi } from "../services/categoriesApi";
import CategoryForm from "../components/CategoryForm";

export default function CategoriesPage() {
  const [items, setItems] = useState([]);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(null);

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
    await categoriesApi.remove(id);
    await load();
  }

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h3>Categorias</h3>
        {!creating && !editing && (
          <button className="btn btn-success" onClick={() => setCreating(true)}>
            + Nova Categoria
          </button>
        )}
      </div>

      {creating && (
        <div className="card mb-3">
          <div className="card-body">
            <h5 className="card-title">Nova Categoria</h5>
            <CategoryForm
              onSubmit={onCreate}
              onCancel={() => setCreating(false)}
            />
          </div>
        </div>
      )}

      {editing && (
        <div className="card mb-3">
          <div className="card-body">
            <h5 className="card-title">Editar Categoria</h5>
            <CategoryForm
              initial={editing}
              onSubmit={onUpdate}
              onCancel={() => setEditing(null)}
            />
          </div>
        </div>
      )}

      <div className="card">
        <div className="card-body">
          <div className="table-responsive">
            <table className="table table-sm align-middle">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Nome</th>
                  <th>Descrição</th>
                  <th>Material</th>
                  <th style={{ width: 120 }}>Ações</th>
                </tr>
              </thead>
              <tbody>
                {items?.map((c) => (
                  <tr key={c.id}>
                    <td>{c.id}</td>
                    <td>{c.nome}</td>
                    <td>{c.descricao}</td>
                    <td>{c.material}</td>
                    <td>
                      <div className="btn-group btn-group-sm">
                        <button
                          className="btn btn-outline-primary"
                          onClick={() => setEditing(c)}
                        >
                          Editar
                        </button>
                        <button
                          className="btn btn-outline-danger"
                          onClick={() => onDelete(c.id)}
                        >
                          Excluir
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {(items?.length ?? 0) === 0 && (
                  <tr>
                    <td colSpan={5} className="text-center text-muted">
                      Nenhuma categoria cadastrada
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}
