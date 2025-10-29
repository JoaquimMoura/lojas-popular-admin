// src/pages/ProductForm.jsx
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { productsApi } from "../services/productsApi";
import { categoriesApi } from "../services/categoriesApi";

export default function ProductForm() {
  const { id } = useParams(); // se existir, modo edição
  const navigate = useNavigate();

  const [form, setForm] = useState({
    nome: "",
    descricao: "",
    preco: "",
    estoque: "",
    categoriaId: null,
  });
  const [imgFile, setImgFile] = useState(null);
  const [imgUrl, setImgUrl] = useState("");
  const [categorias, setCategorias] = useState([]);

  useEffect(() => {
    categoriesApi.listAll().then(setCategorias).catch(console.error);
  }, []);

  useEffect(() => {
    if (!id) return;
    productsApi.byId(id)
      .then((p) => {
        setForm({
          nome: p.nome || "",
          descricao: p.descricao || "",
          preco: p.preco ?? "",
          estoque: p.estoque ?? "",
          categoriaId: p.categoriaId ?? null,
        });
        setImgUrl(p.imagemUrl || "");
      })
      .catch(console.error);
  }, [id]);

  function onChange(e) {
    const { name, value } = e.target;
    setForm((f) => ({ ...f, [name]: value }));
  }

  async function onSubmit(e) {
    e.preventDefault();
    const payload = {
      nome: form.nome,
      descricao: form.descricao,
      preco: Number(form.preco || 0),
      estoque: Number(form.estoque || 0),
      categoriaId: form.categoriaId ? Number(form.categoriaId) : null,
    };

    let saved;
    if (id) {
      saved = await ProductsApi.update(id, payload);
    } else {
      saved = await ProductsApi.create(payload);
    }

    if (imgFile) {
      const url = await ProductsApi.uploadImage(saved.id, imgFile);
      setImgUrl(url);
    }

    navigate("/admin/produtos");
  }

  return (
    <div className="p-4 space-y-4 max-w-3xl">
      <h1 className="text-xl font-semibold">{id ? "Editar produto" : "Novo produto"}</h1>
      <form onSubmit={onSubmit} className="space-y-3">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <label className="block text-sm mb-1">Nome</label>
            <input
              name="nome"
              value={form.nome}
              onChange={onChange}
              required
              className="border rounded px-3 py-2 w-full"
            />
          </div>

          <div>
            <label className="block text-sm mb-1">Preço</label>
            <input
              name="preco"
              type="number"
              step="0.01"
              value={form.preco}
              onChange={onChange}
              required
              className="border rounded px-3 py-2 w-full"
            />
          </div>

          <div>
            <label className="block text-sm mb-1">Estoque</label>
            <input
              name="estoque"
              type="number"
              value={form.estoque}
              onChange={onChange}
              required
              className="border rounded px-3 py-2 w-full"
            />
          </div>

          <div>
            <label className="block text-sm mb-1">Categoria</label>
            <select
              name="categoriaId"
              value={form.categoriaId ?? ""}
              onChange={onChange}
              className="border rounded px-3 py-2 w-full"
            >
              <option value="">— Selecionar —</option>
              {categorias.map((c) => (
                <option key={c.id} value={c.id}>{c.nome}</option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <label className="block text-sm mb-1">Descrição</label>
          <textarea
            name="descricao"
            value={form.descricao}
            onChange={onChange}
            rows={4}
            className="border rounded px-3 py-2 w-full"
          />
        </div>

        <div className="flex items-center gap-3">
          <div>
            <label className="block text-sm mb-1">Imagem</label>
            <input
              type="file"
              accept="image/*"
              onChange={(e) => setImgFile(e.target.files?.[0] || null)}
            />
          </div>
          {imgUrl && (
            <img
              src={imgUrl}
              alt="produto"
              className="w-20 h-20 object-cover border rounded"
            />
          )}
        </div>

        <div className="flex gap-2">
          <button className="px-3 py-2 rounded bg-black text-white" type="submit">
            {id ? "Salvar alterações" : "Criar produto"}
          </button>
          <button type="button" className="px-3 py-2 rounded border" onClick={() => history.back()}>
            Voltar
          </button>
        </div>
      </form>
    </div>
  );
}
