// src/components/ProductForm.jsx
import { useEffect, useMemo, useRef, useState } from "react";
import { categoriesApi } from "../services/categoriesApi";
import { resolveImageUrl } from "../utils/url";
import ImageCropModal from "./ImageCropModal";

const EMPTY = {
  nome: "", codigo: "", descricao: "",
  preco: "", precoOriginal: "",
  estoque: "", sku: "",
  categoriaId: "",
  largura: "", altura: "", profundidade: "", peso: "", volumes: "",
  diferenciais: [],
};

/**
 * cropModal state shape:
 *   null → closed
 *   { type: "cover" | "variation", varIndex?: number,
 *     src: string, queue: [] }          ← for gallery
 *   { type: "gallery", src, queue: [{src, file}] }
 */
export default function ProductForm({ initial, onSubmit, onCancel }) {
  const [form, setForm]               = useState(EMPTY);
  const [cats, setCats]               = useState([]);
  const [variacoes, setVariacoes]     = useState([]);
  const [cover, setCover]             = useState(null);
  const [coverPreview, setCoverPreview] = useState(null);
  const [gallery, setGallery]         = useState([]);
  const [galleryPreview, setGalleryPreview] = useState([]);
  const [difInput, setDifInput]       = useState("");
  const [cropModal, setCropModal]     = useState(null);

  // refs to reset file inputs after crop so the same file can be re-selected
  const coverInputRef    = useRef(null);
  const galleryInputRef  = useRef(null);
  const varInputRefs     = useRef({});

  useEffect(() => {
    categoriesApi.list().then(data => setCats(Array.isArray(data) ? data : []));
  }, []);

  useEffect(() => {
    if (initial) {
      setForm({
        nome:          initial.nome          ?? "",
        codigo:        initial.codigo        ?? "",
        descricao:     initial.descricao     ?? "",
        preco:         String(initial.preco  ?? ""),
        precoOriginal: String(initial.precoOriginal ?? ""),
        estoque:       String(initial.estoque ?? ""),
        sku:           initial.sku           ?? "",
        categoriaId:   String(initial.categoriaId ?? ""),
        largura:       String(initial.largura    ?? ""),
        altura:        String(initial.altura     ?? ""),
        profundidade:  String(initial.profundidade ?? ""),
        peso:          String(initial.peso        ?? ""),
        volumes:       String(initial.volumes     ?? ""),
        diferenciais:  initial.diferenciais ?? [],
      });
      setVariacoes(
        (initial.variacoes ?? []).map(v => ({
          ...v,
          _preview: v.imagemUrl ? resolveImageUrl(v.imagemUrl) : null,
          _file: null,
        }))
      );
      setCoverPreview(initial.imagemUrl ? resolveImageUrl(initial.imagemUrl) : null);
    }
  }, [initial]);

  // ── Form ─────────────────────────────────────────────────────────
  function handleChange(e) {
    const { name, value } = e.target;
    setForm(f => ({ ...f, [name]: value }));
  }

  // ── Diferenciais ─────────────────────────────────────────────────
  function addDif() {
    const txt = difInput.trim();
    if (!txt) return;
    setForm(f => ({ ...f, diferenciais: [...f.diferenciais, txt] }));
    setDifInput("");
  }
  function removeDif(i) {
    setForm(f => ({ ...f, diferenciais: f.diferenciais.filter((_, idx) => idx !== i) }));
  }

  // ── Variações ─────────────────────────────────────────────────────
  function addVar() {
    setVariacoes(v => [...v, { cor: "", tamanho: "", sku: "", adicionalPreco: "", estoque: "", imagemUrl: null, _preview: null, _file: null }]);
  }
  function changeVar(i, field, value) {
    setVariacoes(v => v.map((item, idx) => idx === i ? { ...item, [field]: value } : item));
  }
  function removeVar(i) {
    setVariacoes(v => v.filter((_, idx) => idx !== i));
  }

  // ── Crop handlers ─────────────────────────────────────────────────

  // Cover
  function onCoverChange(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    if (coverInputRef.current) coverInputRef.current.value = "";
    setCropModal({ type: "cover", src: URL.createObjectURL(file) });
  }

  function onCoverCropped(croppedFile) {
    setCover(croppedFile);
    setCoverPreview(URL.createObjectURL(croppedFile));
    setCropModal(null);
  }

  // Gallery — may be multiple files; queue them one by one
  function onGalleryChange(e) {
    const files = Array.from(e.target.files || []);
    if (!files.length) return;
    if (galleryInputRef.current) galleryInputRef.current.value = "";
    // Reset gallery for this batch selection
    setGallery([]);
    setGalleryPreview([]);
    const queue = files.map(f => ({ src: URL.createObjectURL(f), file: f }));
    openNextGallery(queue);
  }

  function openNextGallery(queue) {
    if (!queue.length) { setCropModal(null); return; }
    const [current, ...rest] = queue;
    setCropModal({ type: "gallery", src: current.src, queue: rest });
  }

  function onGalleryCropped(croppedFile) {
    const preview = URL.createObjectURL(croppedFile);
    setGallery(g => [...g, croppedFile]);
    setGalleryPreview(p => [...p, preview]);
    const nextQueue = cropModal.queue;
    openNextGallery(nextQueue);
  }

  // Variation image
  function onVarImage(i, e) {
    const file = e.target.files?.[0];
    if (!file) return;
    if (varInputRefs.current[i]) varInputRefs.current[i].value = "";
    setCropModal({ type: "variation", varIndex: i, src: URL.createObjectURL(file) });
  }

  function onVariationCropped(croppedFile) {
    const i = cropModal.varIndex;
    setVariacoes(v => v.map((item, idx) => idx === i
      ? { ...item, _file: croppedFile, _preview: URL.createObjectURL(croppedFile) }
      : item));
    setCropModal(null);
  }

  function handleCropDone(croppedFile) {
    if (cropModal.type === "cover")      onCoverCropped(croppedFile);
    else if (cropModal.type === "gallery") onGalleryCropped(croppedFile);
    else if (cropModal.type === "variation") onVariationCropped(croppedFile);
  }

  function removeCoverPreview() {
    setCover(null);
    setCoverPreview(null);
  }

  function removeGalleryItem(i) {
    setGallery(g => g.filter((_, idx) => idx !== i));
    setGalleryPreview(p => p.filter((_, idx) => idx !== i));
  }

  // ── Submit ────────────────────────────────────────────────────────
  function submit(e) {
    e.preventDefault();
    const n = (v) => v !== "" && v !== null && v !== undefined ? Number(v) : null;

    const payload = {
      nome:          form.nome,
      codigo:        form.codigo || null,
      descricao:     form.descricao || null,
      preco:         n(form.preco) ?? 0,
      precoOriginal: n(form.precoOriginal),
      estoque:       n(form.estoque) ?? 0,
      sku:           form.sku || null,
      categoriaId:   form.categoriaId ? Number(form.categoriaId) : null,
      largura:       n(form.largura),
      altura:        n(form.altura),
      profundidade:  n(form.profundidade),
      peso:          n(form.peso),
      volumes:       n(form.volumes),
      diferenciais:  form.diferenciais,
      variacoes: variacoes.map(v => ({
        cor:           v.cor || null,
        tamanho:       v.tamanho || null,
        sku:           v.sku || null,
        adicionalPreco: n(v.adicionalPreco) ?? 0,
        estoque:       n(v.estoque),
        imagemUrl:     v.imagemUrl || null,
      })),
    };

    const variacaoImages = {};
    variacoes.forEach((v, i) => {
      if (v._file) variacaoImages[i] = v._file;
    });

    onSubmit(payload, { cover, gallery, variacaoImages });
  }

  const catsOptions = useMemo(() =>
    cats.map(c => <option key={c.id} value={c.id}>{c.nome}</option>),
    [cats]
  );

  const cropLabel = cropModal
    ? cropModal.type === "cover"     ? "Foto Principal (Capa)"
    : cropModal.type === "gallery"   ? `Foto de Detalhe${cropModal.queue.length > 0 ? ` — ${cropModal.queue.length + 1} restante(s)` : ""}`
    : `Foto da variação`
    : "";

  return (
    <>
      {/* ── Crop Modal ── */}
      {cropModal && (
        <ImageCropModal
          src={cropModal.src}
          label={cropLabel}
          remaining={cropModal.type === "gallery" ? cropModal.queue.length : 0}
          onCrop={handleCropDone}
          onCancel={() => setCropModal(null)}
        />
      )}

      <form onSubmit={submit}>
        {/* ── Seção 1: Identificação ── */}
        <div className="card mb-3">
          <div className="card-header fw-semibold text-danger">Identificação</div>
          <div className="card-body">
            <div className="row g-2">
              <div className="col-md-6">
                <label className="form-label">Nome do produto *</label>
                <input name="nome" className="form-control" value={form.nome} onChange={handleChange} required />
              </div>
              <div className="col-md-2">
                <label className="form-label">Código catálogo</label>
                <input name="codigo" className="form-control" placeholder="Ex: 08848" value={form.codigo} onChange={handleChange} />
              </div>
              <div className="col-md-2">
                <label className="form-label">SKU</label>
                <input name="sku" className="form-control" value={form.sku} onChange={handleChange} />
              </div>
              <div className="col-md-2">
                <label className="form-label">Categoria</label>
                <select name="categoriaId" className="form-select" value={form.categoriaId} onChange={handleChange}>
                  <option value="">— selecione —</option>
                  {catsOptions}
                </select>
              </div>
              <div className="col-12">
                <label className="form-label">Descrição</label>
                <textarea name="descricao" className="form-control" value={form.descricao} onChange={handleChange} rows={2} />
              </div>
            </div>
          </div>
        </div>

        {/* ── Seção 2: Preço e Estoque ── */}
        <div className="card mb-3">
          <div className="card-header fw-semibold text-danger">Preço e Estoque</div>
          <div className="card-body">
            <div className="row g-2">
              <div className="col-md-3">
                <label className="form-label">Preço (R$) *</label>
                <input name="preco" type="number" step="0.01" min="0" className="form-control"
                  value={form.preco} onChange={handleChange} required />
              </div>
              <div className="col-md-3">
                <label className="form-label">Preço original (De:)</label>
                <input name="precoOriginal" type="number" step="0.01" min="0" className="form-control"
                  placeholder="Deixe vazio se sem desconto" value={form.precoOriginal} onChange={handleChange} />
              </div>
              <div className="col-md-3">
                <label className="form-label">Estoque *</label>
                <input name="estoque" type="number" min="0" className="form-control"
                  value={form.estoque} onChange={handleChange} required />
              </div>
            </div>
          </div>
        </div>

        {/* ── Seção 3: Fotos ── */}
        <div className="card mb-3">
          <div className="card-header fw-semibold text-danger">
            Fotos
            <small className="fw-normal text-muted ms-2">
              — Ao selecionar, abrirá o recortador para remover logotipos externos
            </small>
          </div>
          <div className="card-body">
            <div className="row g-3">
              {/* Capa */}
              <div className="col-md-4">
                <div className="border rounded p-3 h-100">
                  <label className="form-label fw-semibold d-block">
                    Foto Principal (Capa)
                    <small className="text-muted d-block fw-normal">Aparece no card da listagem</small>
                  </label>
                  <input
                    ref={coverInputRef}
                    type="file" accept="image/*"
                    className="form-control form-control-sm mb-2"
                    onChange={onCoverChange}
                  />
                  {coverPreview ? (
                    <div className="position-relative" style={{ display: "inline-block" }}>
                      <img src={coverPreview} alt="capa" className="img-thumbnail"
                        style={{ height: 140, width: "100%", objectFit: "cover" }} />
                      <button
                        type="button"
                        className="btn btn-sm btn-danger position-absolute top-0 end-0 m-1 py-0 px-1"
                        onClick={removeCoverPreview}
                        title="Remover foto"
                      >&times;</button>
                    </div>
                  ) : (
                    <div className="border rounded d-flex align-items-center justify-content-center text-muted"
                      style={{ height: 140, background: "#f8f9fa", fontSize: "0.85rem" }}>
                      Sem foto de capa
                    </div>
                  )}
                </div>
              </div>

              {/* Galeria */}
              <div className="col-md-8">
                <div className="border rounded p-3 h-100">
                  <label className="form-label fw-semibold d-block">
                    Fotos de Detalhe (Galeria)
                    <small className="text-muted d-block fw-normal">Medidas, ferragens, ambiente montado</small>
                  </label>
                  <input
                    ref={galleryInputRef}
                    type="file" accept="image/*" multiple
                    className="form-control form-control-sm mb-2"
                    onChange={onGalleryChange}
                  />
                  {galleryPreview.length > 0 ? (
                    <div className="d-flex gap-2 flex-wrap">
                      {galleryPreview.map((src, i) => (
                        <div key={i} className="position-relative">
                          <img src={src} alt="" className="img-thumbnail"
                            style={{ height: 80, width: 80, objectFit: "cover" }} />
                          <button
                            type="button"
                            className="btn btn-sm btn-danger position-absolute top-0 end-0 py-0 px-1"
                            style={{ fontSize: "0.7rem" }}
                            onClick={() => removeGalleryItem(i)}
                          >&times;</button>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="text-muted small">Selecione múltiplas fotos de detalhes</div>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* ── Seção 4: Dimensões ── */}
        <div className="card mb-3">
          <div className="card-header fw-semibold text-danger">Dimensões</div>
          <div className="card-body">
            <div className="row g-2">
              <div className="col-md-2">
                <label className="form-label">Largura (m)</label>
                <input name="largura" type="number" step="0.001" min="0" className="form-control"
                  placeholder="Ex: 1.200" value={form.largura} onChange={handleChange} />
              </div>
              <div className="col-md-2">
                <label className="form-label">Altura (m)</label>
                <input name="altura" type="number" step="0.001" min="0" className="form-control"
                  placeholder="Ex: 2.170" value={form.altura} onChange={handleChange} />
              </div>
              <div className="col-md-2">
                <label className="form-label">Profundidade (m)</label>
                <input name="profundidade" type="number" step="0.001" min="0" className="form-control"
                  placeholder="Ex: 0.540" value={form.profundidade} onChange={handleChange} />
              </div>
              <div className="col-md-2">
                <label className="form-label">Peso (kg)</label>
                <input name="peso" type="number" step="0.1" min="0" className="form-control"
                  placeholder="Ex: 78.5" value={form.peso} onChange={handleChange} />
              </div>
              <div className="col-md-2">
                <label className="form-label">Volumes (caixas)</label>
                <input name="volumes" type="number" min="1" className="form-control"
                  placeholder="Ex: 3" value={form.volumes} onChange={handleChange} />
              </div>
            </div>
          </div>
        </div>

        {/* ── Seção 5: Diferenciais ── */}
        <div className="card mb-3">
          <div className="card-header fw-semibold text-danger">Diferenciais do Produto</div>
          <div className="card-body">
            <div className="d-flex gap-2 mb-2">
              <input
                className="form-control"
                placeholder="Ex: Dobradiças com amortecedor"
                value={difInput}
                onChange={e => setDifInput(e.target.value)}
                onKeyDown={e => e.key === "Enter" && (e.preventDefault(), addDif())}
              />
              <button type="button" className="btn btn-outline-danger" onClick={addDif}>+ Adicionar</button>
            </div>
            {form.diferenciais.length > 0 ? (
              <ul className="list-group">
                {form.diferenciais.map((d, i) => (
                  <li key={i} className="list-group-item d-flex justify-content-between align-items-center py-1">
                    <span>&#10003; {d}</span>
                    <button type="button" className="btn btn-sm btn-outline-danger" onClick={() => removeDif(i)}>&times;</button>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-muted small mb-0">Nenhum diferencial adicionado. Digite e pressione Enter ou clique em "+ Adicionar".</p>
            )}
          </div>
        </div>

        {/* ── Seção 6: Cores / Variações ── */}
        <div className="card mb-3">
          <div className="card-header d-flex justify-content-between align-items-center">
            <span className="fw-semibold text-danger">Cores / Variações</span>
            <button type="button" className="btn btn-outline-danger btn-sm" onClick={addVar}>+ Adicionar cor</button>
          </div>
          <div className="card-body">
            {variacoes.length === 0 && (
              <p className="text-muted small mb-0">Produto sem variações de cor. Clique em "+ Adicionar cor" para incluir.</p>
            )}
            {variacoes.map((v, i) => (
              <div key={i} className="border rounded p-3 mb-2">
                <div className="row g-2 align-items-end">
                  <div className="col-md-2">
                    <label className="form-label">Foto da cor</label>
                    <input
                      ref={el => varInputRefs.current[i] = el}
                      type="file" accept="image/*"
                      className="form-control form-control-sm"
                      onChange={e => onVarImage(i, e)}
                    />
                    {v._preview && (
                      <img src={v._preview} alt="cor" className="mt-1 rounded"
                        style={{ height: 60, width: 60, objectFit: "cover" }} />
                    )}
                  </div>
                  <div className="col-md-2">
                    <label className="form-label">Cor / Acabamento</label>
                    <input className="form-control" placeholder="Ex: Branco"
                      value={v.cor || ""} onChange={e => changeVar(i, "cor", e.target.value)} />
                  </div>
                  <div className="col-md-2">
                    <label className="form-label">Tamanho</label>
                    <input className="form-control" placeholder="Ex: Casal"
                      value={v.tamanho || ""} onChange={e => changeVar(i, "tamanho", e.target.value)} />
                  </div>
                  <div className="col-md-2">
                    <label className="form-label">SKU variação</label>
                    <input className="form-control"
                      value={v.sku || ""} onChange={e => changeVar(i, "sku", e.target.value)} />
                  </div>
                  <div className="col-md-2">
                    <label className="form-label">Adic. preço</label>
                    <input type="number" step="0.01" className="form-control"
                      value={v.adicionalPreco ?? ""} onChange={e => changeVar(i, "adicionalPreco", e.target.value)} />
                  </div>
                  <div className="col-md-1">
                    <label className="form-label">Estoque</label>
                    <input type="number" className="form-control"
                      value={v.estoque ?? ""} onChange={e => changeVar(i, "estoque", e.target.value)} />
                  </div>
                  <div className="col-md-1">
                    <button type="button" className="btn btn-outline-danger w-100" onClick={() => removeVar(i)}>&times;</button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* ── Ações ── */}
        <div className="d-flex gap-2">
          <button className="btn btn-danger px-4" type="submit">
            {initial ? "Salvar alterações" : "Criar produto"}
          </button>
          <button className="btn btn-outline-secondary" type="button" onClick={onCancel}>Cancelar</button>
        </div>
      </form>
    </>
  );
}
