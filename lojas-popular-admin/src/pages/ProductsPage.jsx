// src/pages/ProductsPage.jsx
import { useEffect, useState } from "react";
import { productsApi } from "../services/productsApi";
import ProductForm from "../components/ProductForm";
import ProductGalleryModal from "../components/ProductGalleryModal";

export default function ProductsPage() {
  const [galleryProd, setGalleryProd] = useState(null);
  const [items, setItems] = useState([]);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(null);

  async function load() {
    const resp = await productsApi.list({ page: 0, size: 20 });
    const content = resp.data.content ?? resp.data;
    setItems(content);
  }

  useEffect(() => { load(); }, []);

  function onEdit(prod) { setEditing(prod); }

  async function openGallery(prod) {
    const { data } = await productsApi.get(prod.id); // garante galeria atualizada
    setGalleryProd(data);
  }

  async function onCreate(payload, files) {
    const { data } = await productsApi.create(payload);
    if (files?.cover) await productsApi.uploadCover(data.id, files.cover);
    if (files?.gallery?.length) await productsApi.uploadGallery(data.id, files.gallery);
    setCreating(false);
    await load();
  }

  async function onUpdate(produtoId, payload, files) {
    await productsApi.update(produtoId, payload);
    if (files?.cover) await productsApi.uploadCover(produtoId, files.cover);
    if (files?.gallery?.length) await productsApi.uploadGallery(produtoId, files.gallery);
    setEditing(null);
    await load();
  }

  async function onDelete(id) {
    if (!confirm("Excluir este produto?")) return;
    await productsApi.remove(id);
    await load();
  }

  const resolveImageUrl = (url) => {
    if (!url) return null;
    if (url.startsWith("http")) return url;
    if (!url.startsWith("/")) url = "/" + url;
    return `http://localhost:8080${url}`;
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h3>Produtos</h3>
        {!creating && !editing && (
          <button className="btn btn-success" onClick={() => setCreating(true)}>+ Novo Produto</button>
        )}
      </div>

      {creating && (
        <div className="card mb-3">
          <div className="card-body">
            <h5 className="card-title">Novo Produto</h5>
            <ProductForm onSubmit={(payload, files) => onCreate(payload, files)} onCancel={() => setCreating(false)} />
          </div>
        </div>
      )}

      {editing && (
        <div className="card mb-3">
          <div className="card-body">
            <h5 className="card-title">Editar Produto</h5>
            <ProductForm initial={editing} onSubmit={(payload, files) => onUpdate(editing.id, payload, files)} onCancel={() => setEditing(null)} />
          </div>
        </div>
      )}

      <div className="card">
        <div className="card-body">
          <div className="table-responsive">
            <table className="table table-sm align-middle">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Imagem</th>
                  <th>Nome</th>
                  <th>Preço</th>
                  <th>Estoque</th>
                  <th>Categoria</th>
                  <th style={{ width: 220 }}>Ações</th>
                </tr>
              </thead>
              <tbody>
                {items.map((p) => (
                  <tr key={p.id}>
                    <td>{p.id}</td>
                    <td>
                      {p.imagemUrl ? (
                        <img
                          src={resolveImageUrl(p.imagemUrl)}
                          alt=""
                          width={46}
                          height={46}
                          className="rounded"
                          loading="lazy"
                          onError={(e) => { e.currentTarget.src = "data:image/gif;base64,R0lGODlhAQABAIAAAAUEBA=="; }}
                        />
                      ) : <span className="text-muted">—</span>}
                    </td>
                    <td>{p.nome}</td>
                    <td>R$ {Number(p.preco).toFixed(2)}</td>
                    <td>{p.estoque}</td>
                    <td>{p.categoria}</td>
                    <td>
                      <div className="btn-group btn-group-sm">
                        <button className="btn btn-outline-secondary" onClick={() => onEdit(p)}>Editar</button>
                        <button className="btn btn-outline-dark" onClick={() => openGallery(p)}>Galeria</button>
                        <button className="btn btn-outline-danger" onClick={() => onDelete(p.id)}>Excluir</button>
                      </div>
                    </td>
                  </tr>
                ))}
                {items.length === 0 && (
                  <tr><td colSpan={7} className="text-center text-muted py-4">Nenhum produto</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {galleryProd && (
        <ProductGalleryModal
          produto={galleryProd}
          onClose={() => setGalleryProd(null)}
          onUpdated={async () => { await load(); setGalleryProd(null); }}
        />
      )}
    </div>
  );
}
