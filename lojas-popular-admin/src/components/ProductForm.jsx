import { useEffect, useMemo, useState } from "react";
import { categoriesApi } from "../services/categoriesApi";

const empty = {
  nome: "", descricao: "", preco: "", estoque: "", sku: "", categoriaId: ""
};

export default function ProductForm({ initial, onSubmit, onCancel }) {
  const [form, setForm] = useState(empty);
  const [cats, setCats] = useState([]);
  const [variacoes, setVariacoes] = useState([]);
  const [cover, setCover] = useState(null);
  const [coverPreview, setCoverPreview] = useState(null);
  const [gallery, setGallery] = useState([]);
  const [galleryPreview, setGalleryPreview] = useState([]);

  useEffect(() => {
    categoriesApi.list().then(r => setCats(r.data ?? r));
  }, []);

  useEffect(() => {
    if (initial) {
      setForm({
        nome: initial.nome ?? "",
        descricao: initial.descricao ?? "",
        preco: String(initial.preco ?? ""),
        estoque: String(initial.estoque ?? ""),
        sku: initial.sku ?? "",
        categoriaId: "" // vamos popular pelo nome se necessário
      });
      setVariacoes(initial.variacoes ?? []);
      // previews existentes (se quiser mostrar)
      setCoverPreview(initial.imagemUrl || null);
      // ignore galeria existente na edição por simplicidade
    }
  }, [initial]);

  function handleChange(e) {
    const { name, value } = e.target;
    setForm(f => ({ ...f, [name]: value }));
  }

  function addVar() {
    setVariacoes(v => [...v, { cor: "", tamanho: "", sku: "", adicionalPreco: "", estoque: "" }]);
  }

  function changeVar(i, field, value) {
    setVariacoes(v => v.map((item, idx) => idx === i ? { ...item, [field]: value } : item));
  }

  function removeVar(i) {
    setVariacoes(v => v.filter((_, idx) => idx !== i));
  }

  function onCoverChange(e) {
    const file = e.target.files?.[0];
    setCover(file || null);
    setCoverPreview(file ? URL.createObjectURL(file) : null);
  }

  function onGalleryChange(e) {
    const files = Array.from(e.target.files || []);
    setGallery(files);
    setGalleryPreview(files.map(f => URL.createObjectURL(f)));
  }

  function submit(e) {
    e.preventDefault();

    // payload para API JSON
    const payload = {
      nome: form.nome,
      descricao: form.descricao,
      preco: form.preco ? Number(form.preco) : 0,
      estoque: form.estoque ? Number(form.estoque) : 0,
      sku: form.sku || null,
      categoriaId: form.categoriaId ? Number(form.categoriaId) : null,
      variacoes: variacoes.map(v => ({
        cor: v.cor || null,
        tamanho: v.tamanho || null,
        sku: v.sku || null,
        adicionalPreco: v.adicionalPreco ? Number(v.adicionalPreco) : 0,
        estoque: v.estoque ? Number(v.estoque) : null
      }))
    };

    const files = { cover, gallery };
    onSubmit(payload, files);
  }

  const catsOptions = useMemo(() =>
    cats?.map(c => <option key={c.id} value={c.id}>{c.nome}</option>) ?? [],
    [cats]
  );

  return (
    <form onSubmit={submit}>
      <div className="row g-2">
        <div className="col-md-6">
          <label className="form-label">Nome</label>
          <input name="nome" className="form-control" value={form.nome} onChange={handleChange} required />
        </div>
        <div className="col-md-6">
          <label className="form-label">Categoria</label>
          <select name="categoriaId" className="form-select" value={form.categoriaId} onChange={handleChange}>
            <option value="">-- selecione --</option>
            {catsOptions}
          </select>
        </div>
        <div className="col-12">
          <label className="form-label">Descrição</label>
          <textarea name="descricao" className="form-control" value={form.descricao} onChange={handleChange} rows={2}/>
        </div>
        <div className="col-md-3">
          <label className="form-label">Preço (R$)</label>
          <input name="preco" type="number" step="0.01" className="form-control" value={form.preco} onChange={handleChange} required/>
        </div>
        <div className="col-md-3">
          <label className="form-label">Estoque</label>
          <input name="estoque" type="number" className="form-control" value={form.estoque} onChange={handleChange} required/>
        </div>
        <div className="col-md-3">
          <label className="form-label">SKU (base)</label>
          <input name="sku" className="form-control" value={form.sku} onChange={handleChange}/>
        </div>

        {/* Imagem de capa */}
        <div className="col-md-3">
          <label className="form-label">Imagem de capa</label>
          <input type="file" accept="image/*" className="form-control" onChange={onCoverChange}/>
          {coverPreview && <img src={coverPreview} alt="preview" style={{height:60, marginTop:6}} />}
        </div>

        {/* Galeria */}
        <div className="col-12">
          <label className="form-label">Galeria (múltiplas)</label>
          <input type="file" accept="image/*" multiple className="form-control" onChange={onGalleryChange}/>
          {!!galleryPreview.length && (
            <div className="d-flex gap-2 mt-2 flex-wrap">
              {galleryPreview.map((src, i) => <img key={i} src={src} style={{height:60}} />)}
            </div>
          )}
        </div>
      </div>

      {/* Variações */}
      <div className="mt-4">
        <div className="d-flex justify-content-between align-items-center">
          <h6 className="mb-2">Variações</h6>
          <button type="button" className="btn btn-outline-secondary btn-sm" onClick={addVar}>+ Adicionar variação</button>
        </div>

        {variacoes.length === 0 && (
          <div className="text-muted">Nenhuma variação adicionada.</div>
        )}

        {variacoes.map((v, i) => (
          <div key={i} className="row g-2 align-items-end mb-2">
            <div className="col-md-2">
              <label className="form-label">Cor</label>
              <input className="form-control" value={v.cor || ""} onChange={e => changeVar(i, "cor", e.target.value)} />
            </div>
            <div className="col-md-2">
              <label className="form-label">Tamanho</label>
              <input className="form-control" value={v.tamanho || ""} onChange={e => changeVar(i, "tamanho", e.target.value)} />
            </div>
            <div className="col-md-2">
              <label className="form-label">SKU</label>
              <input className="form-control" value={v.sku || ""} onChange={e => changeVar(i, "sku", e.target.value)} />
            </div>
            <div className="col-md-2">
              <label className="form-label">Adic. Preço</label>
              <input type="number" step="0.01" className="form-control" value={v.adicionalPreco ?? ""} onChange={e => changeVar(i, "adicionalPreco", e.target.value)} />
            </div>
            <div className="col-md-2">
              <label className="form-label">Estoque var.</label>
              <input type="number" className="form-control" value={v.estoque ?? ""} onChange={e => changeVar(i, "estoque", e.target.value)} />
            </div>
            <div className="col-md-2">
              <button type="button" className="btn btn-outline-danger w-100" onClick={() => removeVar(i)}>Remover</button>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-3 d-flex gap-2">
        <button className="btn btn-primary" type="submit">Salvar</button>
        <button className="btn btn-outline-secondary" type="button" onClick={onCancel}>Cancelar</button>
      </div>
    </form>
  );
}
