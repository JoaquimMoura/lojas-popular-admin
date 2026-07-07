// src/pages/ProductDetails.jsx
import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { storeApi } from "../services/storeApi";
import { resolveImageUrl } from "../utils/url";
import { buildWhatsAppUrl } from "../utils/whatsapp";
import WhatsAppButton from "../components/WhatsAppButton";
import "../styles/Lightbox.css";
import "../styles/ProductDetails.css";
import { useCart } from "../context/CartContext";
import { ToastContainer, toast } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";

export default function ProductDetails() {
  const { id } = useParams();
  const [produto, setProduto] = useState(null);
  const [imagemPrincipal, setImagemPrincipal] = useState(null);
  const [corSelecionada, setCorSelecionada] = useState(null);
  const [lightboxIndex, setLightboxIndex] = useState(null);
  const [zoom, setZoom] = useState(1);
  const zoomRef = useRef(null);
  const { addToCart } = useCart();

  const handleKey = useCallback((e) => {
    if (e.key === "Escape") { setLightboxIndex(null); setZoom(1); }
    if (lightboxIndex !== null) {
      if (e.key === "ArrowRight") setLightboxIndex(p => p === (produto?.galeria?.length - 1) ? 0 : p + 1);
      if (e.key === "ArrowLeft") setLightboxIndex(p => p === 0 ? (produto?.galeria?.length - 1) : p - 1);
    }
  }, [lightboxIndex, produto]);

  useEffect(() => {
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [handleKey]);

  useEffect(() => {
    storeApi.getProduct(id).then(data => {
      let galeria = Array.isArray(data.galeria) ? data.galeria : [];
      setProduto({ ...data, galeria });
      setImagemPrincipal(resolveImageUrl(data.imagemUrl || galeria[0]));
    }).catch(console.error);
  }, [id]);

  if (!produto) {
    return (
      <div className="container py-5 text-center">
        <div className="spinner-border text-danger" role="status" />
        <p className="mt-3 text-muted">Carregando produto...</p>
      </div>
    );
  }

  const preco = Number(produto.preco || 0);
  const precoOriginal = produto.precoOriginal ? Number(produto.precoOriginal) : null;
  const temDesconto = precoOriginal && precoOriginal > preco;
  const desconto = temDesconto ? Math.round((1 - preco / precoOriginal) * 100) : 0;
  const parcelas = 12;
  const valorParcela = preco / parcelas;

  const fmt = (v) => v?.toLocaleString("pt-BR", { minimumFractionDigits: 2 });
  const fmtM = (v) => v != null ? `${Number(v).toFixed(2)} m` : "—";

  const temDimensoes = produto.largura || produto.altura || produto.profundidade || produto.peso;
  const temDiferenciais = produto.diferenciais?.length > 0;
  const temVariacoes = produto.variacoes?.length > 0;

  const todasImagens = [
    ...(produto.imagemUrl ? [produto.imagemUrl] : []),
    ...(produto.galeria || []),
  ].filter(Boolean);

  function selectVariacao(v) {
    setCorSelecionada(corSelecionada?.id === v.id ? null : v);
    if (v.imagemUrl) setImagemPrincipal(resolveImageUrl(v.imagemUrl));
  }

  function handleAddToCart() {
    addToCart({ id: produto.id, nome: produto.nome, preco: produto.preco, imagemUrl: produto.imagemUrl });
    toast.success(`${produto.nome} adicionado ao carrinho!`, {
      position: "top-right", autoClose: 3000, theme: "dark",
      style: { backgroundColor: "#B71C1C", color: "#fff" },
    });
  }

  const handleWheelZoom = (e) => {
    e.preventDefault();
    setZoom(v => Math.min(Math.max(v + (e.deltaY < 0 ? 0.15 : -0.15), 1), 3));
  };

  const currentLightbox = lightboxIndex !== null
    ? resolveImageUrl(todasImagens[lightboxIndex])
    : null;

  return (
    <div className="product-details-page">
      <ToastContainer />

      <div className="row g-4 g-lg-5">
        {/* ── Coluna esquerda: imagens ── */}
        <div className="col-lg-6">
          {/* Imagem principal */}
          <div className="product-main-img-wrap mb-3" onClick={() => setLightboxIndex(0)}>
            {produto.codigo && (
              <span className="product-code-badge">Cód. {produto.codigo}</span>
            )}
            <img
              src={imagemPrincipal || "/assets/no-image.png"}
              alt={produto.nome}
              className="product-main-img"
              onError={e => { e.target.src = "/assets/no-image.png"; }}
            />
            {temDesconto && (
              <span className="product-discount-badge">{desconto}% OFF</span>
            )}
            <span className="product-zoom-hint">Clique para ampliar</span>
          </div>

          {/* Miniaturas galeria */}
          {todasImagens.length > 1 && (
            <div className="product-thumbnails">
              {todasImagens.map((url, i) => {
                const src = resolveImageUrl(url);
                return (
                  <img
                    key={i}
                    src={src}
                    alt=""
                    className={`product-thumb ${src === imagemPrincipal ? "active" : ""}`}
                    onClick={() => setImagemPrincipal(src)}
                    onError={e => { e.target.src = "/assets/no-image.png"; }}
                  />
                );
              })}
            </div>
          )}
        </div>

        {/* ── Coluna direita: informações ── */}
        <div className="col-lg-6">
          {/* Categoria */}
          {produto.categoria && (
            <p className="product-category-tag">{produto.categoria}</p>
          )}

          {/* Nome */}
          <h1 className="product-title">{produto.nome}</h1>

          {/* Preço */}
          <div className="product-price-block">
            {temDesconto && (
              <p className="product-price-original">
                De: <span className="text-decoration-line-through">R$ {fmt(precoOriginal)}</span>
              </p>
            )}
            <p className="product-price-main">R$ {fmt(preco)}</p>
            <p className="product-price-installment">
              ou {parcelas}x de R$ {fmt(valorParcela)}{" "}
              <span className="text-success fw-semibold">sem juros</span>
            </p>
          </div>

          {/* Descrição */}
          {produto.descricao && (
            <p className="product-description">{produto.descricao}</p>
          )}

          {/* Cores / Variações */}
          {temVariacoes && (
            <div className="product-colors mb-3">
              <p className="fw-semibold mb-2 text-muted small">
                CORES DISPONÍVEIS ({produto.variacoes.length})
              </p>
              <div className="d-flex flex-wrap gap-2">
                {produto.variacoes.map((v) => (
                  <button
                    key={v.id}
                    type="button"
                    onClick={() => selectVariacao(v)}
                    className={`product-color-btn ${corSelecionada?.id === v.id ? "active" : ""}`}
                    title={[v.cor, v.tamanho].filter(Boolean).join(" — ")}
                  >
                    {v.imagemUrl ? (
                      <img
                        src={resolveImageUrl(v.imagemUrl)}
                        alt={v.cor}
                        className="product-color-img"
                        onError={e => { e.target.style.display = "none"; }}
                      />
                    ) : (
                      <span className="product-color-label">{v.cor || "?"}</span>
                    )}
                    <span className="product-color-name">{v.cor}</span>
                    {v.tamanho && <span className="product-color-size">{v.tamanho}</span>}
                    {v.adicionalPreco > 0 && (
                      <span className="product-color-adicional">+R$ {fmt(v.adicionalPreco)}</span>
                    )}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Dimensões */}
          {temDimensoes && (
            <div className="product-dimensions mb-3">
              <p className="fw-semibold mb-2 text-muted small">DIMENSÕES</p>
              <div className="dimensions-grid">
                {produto.largura && <div className="dim-item"><span className="dim-label">Largura</span><span className="dim-value">{fmtM(produto.largura)}</span></div>}
                {produto.altura && <div className="dim-item"><span className="dim-label">Altura</span><span className="dim-value">{fmtM(produto.altura)}</span></div>}
                {produto.profundidade && <div className="dim-item"><span className="dim-label">Prof.</span><span className="dim-value">{fmtM(produto.profundidade)}</span></div>}
                {produto.peso && <div className="dim-item"><span className="dim-label">Peso</span><span className="dim-value">{Number(produto.peso).toFixed(1)} kg</span></div>}
                {produto.volumes && <div className="dim-item"><span className="dim-label">Volumes</span><span className="dim-value">{produto.volumes} cx</span></div>}
              </div>
            </div>
          )}

          {/* Diferenciais */}
          {temDiferenciais && (
            <div className="product-diferenciais mb-3">
              <p className="fw-semibold mb-2 text-muted small">DIFERENCIAIS</p>
              <ul className="dif-list">
                {produto.diferenciais.map((d, i) => (
                  <li key={i}><span className="dif-check">&#10003;</span> {d}</li>
                ))}
              </ul>
            </div>
          )}

          {/* CTA */}
          <div className="product-cta d-flex flex-column gap-2 mt-4">
            <button className="btn btn-warning btn-lg fw-bold" onClick={handleAddToCart}>
              Adicionar ao Carrinho
            </button>
            <WhatsAppButton
              text={`Olá! Tenho interesse no produto: *${produto.nome}* (R$ ${fmt(preco)}). Podem me ajudar?`}
              label="Comprar pelo WhatsApp"
              className="btn btn-success btn-lg"
            />
          </div>

          {/* Estoque */}
          {produto.estoque != null && (
            <p className="text-muted small mt-2">
              {produto.estoque > 5
                ? `Em estoque (${produto.estoque} disponíveis)`
                : produto.estoque > 0
                ? `Últimas unidades (${produto.estoque} restantes)`
                : "Indisponível"}
            </p>
          )}
        </div>
      </div>

      {/* ── Lightbox ── */}
      {lightboxIndex !== null && (
        <div className="lightbox-overlay" onClick={() => { setLightboxIndex(null); setZoom(1); }} onWheel={handleWheelZoom}>
          <button className="lightbox-prev btn btn-light btn-lg" onClick={e => { e.stopPropagation(); setLightboxIndex(p => p === 0 ? todasImagens.length - 1 : p - 1); setZoom(1); }}>&#8249;</button>
          <div className="lightbox-container" onClick={e => e.stopPropagation()}>
            <img ref={zoomRef} src={currentLightbox} alt="" className="lightbox-image"
              style={{ transform: `scale(${zoom})`, cursor: zoom > 1 ? "grab" : "zoom-out", transition: "transform 0.25s ease" }}
              onError={e => { e.target.src = "/assets/no-image.png"; }} />
          </div>
          <button className="lightbox-next btn btn-light btn-lg" onClick={e => { e.stopPropagation(); setLightboxIndex(p => p === todasImagens.length - 1 ? 0 : p + 1); setZoom(1); }}>&#8250;</button>
          <div className="lightbox-counter">{lightboxIndex + 1} / {todasImagens.length}</div>
        </div>
      )}
    </div>
  );
}
