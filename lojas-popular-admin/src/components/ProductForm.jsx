// src/components/ProductForm.jsx
import { useEffect, useMemo, useRef, useState } from "react";
import { categoriesApi } from "../services/categoriesApi";
import { productsApi } from "../services/productsApi";
import { useAuth } from "../context/AuthContext";
import CaracteristicasProduto from "./CaracteristicasProduto";
import { valorVazio } from "../utils/caracteristicas";
import { resolveImageUrl } from "../utils/url";
import ImageCropModal from "./ImageCropModal";

const EMPTY = {
  nome: "", codigo: "", descricao: "",
  preco: "", precoOriginal: "",
  estoque: "", sku: "",
  categoriaId: "",
  largura: "", altura: "", profundidade: "", peso: "", volumes: "",
  diferenciais: [],
  version: null,
  modalidade: "PRONTA_ENTREGA",
  prazoEncomendaDias: "",
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
  const [catsError, setCatsError]     = useState(false);
  const [saving, setSaving]           = useState(false);
  const [saveError, setSaveError]     = useState(null);

  // ── Catálogo dinâmico (materiais e características da categoria) ──
  const { user } = useAuth() ?? {};
  const roles = user?.roles ?? [];
  const podeConfigurar = roles.some(r => r === "ADMIN" || r === "GERENTE");
  const [categoria, setCategoria]     = useState(null);   // detalhe da categoria escolhida
  const [catCarregando, setCatCarregando] = useState(false);
  const [catErro, setCatErro]         = useState(false);
  const [materialIds, setMaterialIds] = useState([]);     // seleção bruta (permite desfazer a troca de categoria)
  const [valores, setValores]         = useState({});     // caracteristicaId -> {opcaoIds, valorTexto, valorNumero}
  const [novosMat, setNovosMat]       = useState([]);     // materiais cadastrados agora, nesta tela
  const [impacto, setImpacto]         = useState(null);   // { materiais, caracteristicas } da troca de categoria
  const [impactoCarregando, setImpactoCarregando] = useState(false);
  const [impactoErro, setImpactoErro] = useState(false);
  const [tentarImpacto, setTentarImpacto] = useState(0);
  const [tentou, setTentou]           = useState(false);
  const [addCatBusy, setAddCatBusy]   = useState(false);
  const [addCatErro, setAddCatErro]   = useState(null);

  // refs to reset file inputs after crop so the same file can be re-selected
  const coverInputRef    = useRef(null);
  const galleryInputRef  = useRef(null);
  const varInputRefs     = useRef({});

  function loadCats() {
    setCatsError(false);
    categoriesApi.list()
      .then(data => setCats(Array.isArray(data) ? data : []))
      .catch(() => setCatsError(true));
  }

  useEffect(() => {
    loadCats();
  }, []);

  function carregarCategoria(id) {
    setCatErro(false);
    setCatCarregando(true);
    return categoriesApi.byId(id)
      .then(c => { setCategoria(c); return c; })
      .catch(() => { setCategoria(null); setCatErro(true); })
      .finally(() => setCatCarregando(false));
  }

  // Ao escolher a categoria, traz os materiais e as características dela
  useEffect(() => {
    if (!form.categoriaId) { setCategoria(null); setCatErro(false); setCatCarregando(false); return; }
    let vivo = true;
    setCategoria(null);
    setCatErro(false);
    setCatCarregando(true);
    categoriesApi.byId(form.categoriaId)
      .then(c => { if (vivo) setCategoria(c); })
      .catch(() => { if (vivo) setCatErro(true); })
      .finally(() => { if (vivo) setCatCarregando(false); });
    return () => { vivo = false; };
  }, [form.categoriaId]);

  // Produto já salvo trocando de categoria: avisa o que será descartado
  const catOriginal = initial ? String(initial.categoriaId ?? "") : null;
  const mudouCategoria = !!initial?.id && !!form.categoriaId && form.categoriaId !== catOriginal;
  useEffect(() => {
    setImpacto(null);
    setImpactoErro(false);
    if (!mudouCategoria) { setImpactoCarregando(false); return; }
    let vivo = true;
    setImpactoCarregando(true);
    productsApi.impactoCategoria(initial.id, Number(form.categoriaId))
      .then(r => { if (vivo) setImpacto({ materiais: r?.materiais ?? [], caracteristicas: r?.caracteristicas ?? [] }); })
      .catch(() => { if (vivo) setImpactoErro(true); })
      .finally(() => { if (vivo) setImpactoCarregando(false); });
    return () => { vivo = false; };
  }, [mudouCategoria, form.categoriaId, initial?.id, tentarImpacto]);

  useEffect(() => {
    if (initial) {
      setMaterialIds((initial.materiais ?? []).map(m => m.id));
      const mapa = {};
      (initial.caracteristicas ?? []).forEach(v => {
        mapa[v.caracteristicaId] = {
          opcaoIds: (v.opcoes ?? []).map(o => o.id),
          valorTexto: v.valorTexto ?? "",
          valorNumero: v.valorNumero == null ? "" : String(v.valorNumero),
        };
      });
      setValores(mapa);
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
        version:       initial.version ?? null,
        modalidade:    initial.modalidade ?? "PRONTA_ENTREGA",
        prazoEncomendaDias: String(initial.prazoEncomendaDias ?? ""),
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

  // ── Materiais e características ──────────────────────────────────
  const categoriaAtual = categoria && String(categoria.id) === form.categoriaId ? categoria : null;
  const idsNovos = novosMat.map(m => m.id);
  const idsDaCategoria = new Set((categoriaAtual?.materiais ?? []).map(m => m.id));
  // Em produto novo ou com categoria trocada, só valem os materiais da nova categoria.
  const filtrarMateriais = !!categoriaAtual && (!initial || mudouCategoria);
  const materiaisEfetivos = filtrarMateriais
    ? materialIds.filter(id => idsDaCategoria.has(id) || idsNovos.includes(id))
    : materialIds;
  const materiaisDisponiveis = [
    ...(categoriaAtual?.materiais ?? []),
    ...(!filtrarMateriais ? (initial?.materiais ?? []) : []),
  ].filter((m, i, a) => a.findIndex(x => x.id === m.id) === i);
  const carsAtivas = (categoriaAtual?.caracteristicas ?? []).filter(c => c.ativa);
  const opcoesDoProduto = {};
  (initial?.caracteristicas ?? []).forEach(v => { opcoesDoProduto[v.caracteristicaId] = v.opcoes ?? []; });
  const obrigatoriasVazias = carsAtivas.filter(c => c.obrigatoria && valorVazio(c, valores[c.id]));
  const pend = initial?.pendencias ?? [];
  const destacar = new Set(
    obrigatoriasVazias.filter(c => tentou || pend.includes(c.nome)).map(c => c.id)
  );
  // materiais criados agora que a categoria ainda não oferece
  const materiaisSemCategoria = novosMat.filter(m => materialIds.includes(m.id) && !idsDaCategoria.has(m.id));
  const descartaAlgo = mudouCategoria && !!impacto && (impacto.materiais.length > 0 || impacto.caracteristicas.length > 0);

  function mudarValor(id, parcial) {
    setValores(v => ({ ...v, [id]: { opcaoIds: [], valorTexto: "", valorNumero: "", ...v[id], ...parcial } }));
  }

  function mudarMateriais(ids) {
    // mantém (sem mostrar) o que a nova categoria não oferece, para poder desfazer a troca
    const escondidos = materialIds.filter(id => !materiaisEfetivos.includes(id));
    setMaterialIds([...escondidos, ...ids]);
  }

  async function adicionarNaCategoria(m) {
    if (!categoriaAtual || addCatBusy) return;
    setAddCatBusy(true);
    setAddCatErro(null);
    try {
      await categoriesApi.update(categoriaAtual.id, {
        nome: categoriaAtual.nome,
        descricao: categoriaAtual.descricao ?? "",
        materialIds: [...(categoriaAtual.materiais ?? []).map(x => x.id), m.id],
      });
      await carregarCategoria(categoriaAtual.id);
    } catch (err) {
      setAddCatErro(err?.response?.data?.message || err?.message || "Não foi possível adicionar o material à categoria.");
    } finally {
      setAddCatBusy(false);
    }
  }

  // ── Submit ────────────────────────────────────────────────────────
  async function submit(e) {
    e.preventDefault();
    if (saving) return;
    if (catCarregando || impactoCarregando) return;
    if (form.categoriaId && catErro) {
      setSaveError("Não foi possível carregar os materiais e características da categoria. Tente carregar de novo antes de salvar.");
      return;
    }
    if (impactoErro) {
      setSaveError("Não foi possível conferir o que a troca de categoria vai descartar. Tente conferir de novo antes de salvar.");
      return;
    }
    setTentou(true);
    if (materiaisSemCategoria.length > 0) {
      setSaveError(`O material "${materiaisSemCategoria[0].nome}" ainda não está liberado para esta categoria. Adicione-o à categoria ou tire-o da lista.`);
      return;
    }
    if (obrigatoriasVazias.length > 0) {
      setSaveError(`Preencha os campos obrigatórios: ${obrigatoriasVazias.map(c => c.nome).join(", ")}.`);
      return;
    }
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
      version:       form.version,
      modalidade:    form.modalidade || "PRONTA_ENTREGA",
      prazoEncomendaDias: n(form.prazoEncomendaDias),
      materialIds: materiaisEfetivos,
      caracteristicas: carsAtivas.map(c => {
        const v = valores[c.id];
        const sel = c.tipo === "SELECAO_UNICA" || c.tipo === "SELECAO_MULTIPLA";
        return {
          caracteristicaId: c.id,
          opcaoIds:   sel ? (v?.opcaoIds ?? []) : [],
          valorTexto: c.tipo === "TEXTO" ? ((v?.valorTexto ?? "").trim() || null) : null,
          valorNumero: c.tipo === "NUMERO" && v?.valorNumero !== "" && v?.valorNumero != null ? Number(v.valorNumero) : null,
        };
      }),
      confirmarDescarte: descartaAlgo,
      variacoes: variacoes.map(v => ({
        id:            v.id ?? null,
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

    setSaving(true);
    setSaveError(null);
    try {
      await onSubmit(payload, { cover, gallery, variacaoImages });
    } catch (err) {
      setSaveError(err?.response?.data?.message || err?.message || "Falha ao salvar o produto. Tente novamente.");
    } finally {
      setSaving(false);
    }
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
                <label className="form-label">Nome do produto <span className="text-danger">*</span></label>
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
                <label className="form-label">Categoria <span className="text-danger">*</span></label>
                <select name="categoriaId" className="form-select" value={form.categoriaId} onChange={handleChange} required>
                  <option value="">— selecione —</option>
                  {catsOptions}
                </select>
                {catsError && (
                  <div className="text-danger small mt-1">
                    Falha ao carregar categorias.{" "}
                    <button type="button" className="btn btn-link btn-sm p-0 align-baseline" onClick={loadCats}>
                      Tentar novamente
                    </button>
                  </div>
                )}
              </div>
              <div className="col-12">
                <label className="form-label">Descrição</label>
                <textarea name="descricao" className="form-control" value={form.descricao} onChange={handleChange} rows={2} />
              </div>
            </div>
          </div>
        </div>

        {/* ── Características (materiais + campos da categoria) ── */}
        {!!initial && pend.length > 0 && (
          <div className="alert alert-warning py-2" role="alert">
            <strong>Complementar cadastro:</strong> falta preencher {pend.join(", ")}.
          </div>
        )}
        {form.categoriaId && catCarregando && (
          <p className="text-muted small">Carregando materiais e características da categoria…</p>
        )}
        {form.categoriaId && catErro && (
          <div className="alert alert-danger py-2" role="alert">
            Não foi possível carregar os materiais e características da categoria.{" "}
            <button type="button" className="btn btn-link btn-sm p-0 align-baseline" onClick={() => carregarCategoria(form.categoriaId)}>
              Tentar novamente
            </button>
          </div>
        )}
        {mudouCategoria && impactoCarregando && (
          <p className="text-muted small">Conferindo o que muda com a nova categoria…</p>
        )}
        {mudouCategoria && impactoErro && (
          <div className="alert alert-danger py-2" role="alert">
            Não foi possível conferir o que a troca de categoria vai descartar.{" "}
            <button type="button" className="btn btn-link btn-sm p-0 align-baseline" onClick={() => setTentarImpacto(t => t + 1)}>
              Conferir de novo
            </button>
          </div>
        )}
        {descartaAlgo && (
          <div className="alert alert-warning" role="alert">
            <p className="fw-semibold mb-1">Atenção: ao salvar, estas informações do produto serão apagadas, porque a nova categoria não as usa.</p>
            <ul className="mb-2">
              {impacto.materiais.length > 0 && <li>Materiais: {impacto.materiais.join(", ")}</li>}
              {impacto.caracteristicas.map((c, i) => <li key={i}>{c}</li>)}
            </ul>
            <button type="button" className="btn btn-sm btn-outline-dark"
              onClick={() => setForm(f => ({ ...f, categoriaId: catOriginal }))}>
              Desfazer e voltar à categoria anterior
            </button>
          </div>
        )}
        {categoriaAtual && (
          <CaracteristicasProduto
            categoria={categoriaAtual}
            materialIds={materiaisEfetivos}
            onMaterialIds={mudarMateriais}
            materiaisDisponiveis={materiaisDisponiveis}
            valores={valores}
            onValor={mudarValor}
            destacar={destacar}
            opcoesDoProduto={opcoesDoProduto}
            podeCadastrar={podeConfigurar}
            onMaterialCriado={m => setNovosMat(l => [...l, { id: m.id, nome: m.nome, ativo: m.ativo !== false }])}
            avisoMaterial={materiaisSemCategoria.length > 0 && (
              <div className="alert alert-warning py-2 mt-2 mb-0" role="alert">
                {materiaisSemCategoria.map(m => (
                  <div key={m.id} className="mb-1">
                    O material <strong>{m.nome}</strong> foi cadastrado, mas só pode ser usado no produto se a categoria "{categoriaAtual.nome}" o oferecer.
                    {podeConfigurar ? (
                      <button type="button" className="btn btn-sm btn-outline-dark ms-2" disabled={addCatBusy}
                        onClick={() => adicionarNaCategoria(m)}>
                        {addCatBusy ? "Aguarde..." : "Adicionar à categoria"}
                      </button>
                    ) : (
                      <span> Peça a um gerente para liberar na categoria.</span>
                    )}
                  </div>
                ))}
                {addCatErro && <div className="text-danger small">{addCatErro}</div>}
              </div>
            )}
          />
        )}

        {/* ── Seção 2: Preço e Estoque ── */}
        <div className="card mb-3">
          <div className="card-header fw-semibold text-danger">Preço e Estoque</div>
          <div className="card-body">
            <div className="row g-2">
              <div className="col-md-3">
                <label className="form-label">Preço (R$) <span className="text-danger">*</span></label>
                <input name="preco" type="number" step="0.01" min="0" className="form-control"
                  value={form.preco} onChange={handleChange} required />
              </div>
              <div className="col-md-3">
                <label className="form-label">Preço original (De:)</label>
                <input name="precoOriginal" type="number" step="0.01" min="0" className="form-control"
                  placeholder="Deixe vazio se sem desconto" value={form.precoOriginal} onChange={handleChange} />
              </div>
              <div className="col-md-3">
                <label className="form-label">Estoque <span className="text-danger">*</span></label>
                <input name="estoque" type="number" min="0" className="form-control"
                  value={form.estoque} onChange={handleChange} required />
              </div>
              <div className="col-md-3">
                <label className="form-label">Modalidade de venda</label>
                <select name="modalidade" className="form-select" value={form.modalidade} onChange={handleChange}>
                  <option value="PRONTA_ENTREGA">Pronta entrega</option>
                  <option value="ENCOMENDA">Encomenda</option>
                  <option value="AMBAS">Pronta entrega e encomenda</option>
                </select>
              </div>
              <div className="col-md-3">
                <label className="form-label">Prazo de encomenda (dias)</label>
                <input name="prazoEncomendaDias" type="number" min="0" className="form-control"
                  value={form.prazoEncomendaDias} onChange={handleChange} />
                <div className="form-text">Opcional — prazo ainda a definir.</div>
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
        {saveError && (
          <div className="alert alert-danger" role="alert">{saveError}</div>
        )}
        <div className="d-flex gap-2">
          <button className="btn btn-danger px-4" type="submit" disabled={saving || catCarregando || impactoCarregando}>
            {saving ? "Salvando…" : initial ? "Salvar alterações" : "Criar produto"}
          </button>
          <button className="btn btn-outline-secondary" type="button" onClick={onCancel} disabled={saving}>Cancelar</button>
        </div>
      </form>
    </>
  );
}
