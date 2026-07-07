// src/components/ProductCard.jsx
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { DEFAULT_IMAGE, resolveImageUrl } from "../utils/url";
import { buildWhatsAppUrl } from "../utils/whatsapp";
import "../styles/ProductCard.css";

function WaIconSmall() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="currentColor"
      className="me-1"
      style={{ verticalAlign: "text-bottom", flexShrink: 0 }}
    >
      <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z" />
    </svg>
  );
}

export default function ProductCard({ produto, badge, whatsapp }) {
  const [imagemSrc, setImagemSrc] = useState(() => resolveImageUrl(produto?.imagemUrl));
  const [imagemErro, setImagemErro] = useState(false);

  useEffect(() => {
    setImagemErro(false);
    setImagemSrc(resolveImageUrl(produto?.imagemUrl));
  }, [produto?.imagemUrl]);

  if (!produto) return null;

  const handleImageError = () => {
    if (imagemErro) return;
    setImagemErro(true);
    setImagemSrc(DEFAULT_IMAGE);
  };

  const preco = Number(produto.preco || 0);
  const precoOriginal = produto.precoOriginal ? Number(produto.precoOriginal) : null;
  const temDesconto = precoOriginal && precoOriginal > preco;
  const desconto = temDesconto ? Math.round((1 - preco / precoOriginal) * 100) : 0;

  const parcelas = Number(produto.parcelas || 12);
  const semJuros = produto.semJuros !== false;
  const valorParcela = preco / parcelas;

  const precoFormatado = preco.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const parcelaFormatada = valorParcela.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const originalFormatado = precoOriginal
    ? precoOriginal.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : null;

  const itens = (() => {
    const bruto = produto.itens ?? produto.items ?? produto.variacoes ?? produto.componentes ?? [];
    let lista = [];

    if (Array.isArray(bruto)) {
      lista = bruto;
    } else if (typeof bruto === "string" && bruto.trim().length) {
      try {
        const parsed = JSON.parse(bruto);
        if (Array.isArray(parsed)) lista = parsed;
      } catch {
        lista = bruto.split(/[\r\n,;]+/);
      }
    }

    return lista
      .map((item) => {
        if (!item) return null;
        if (typeof item === "string" || typeof item === "number") return String(item).trim();
        if (typeof item === "object") {
          const quantidade = item.quantidade ?? item.qtd ?? item.qtde ?? item.estoque;
          const nome =
            item.nome ?? item.itemNome ?? item.titulo ?? item.descricao ??
            item.name ?? item.label ?? item.produtoNome ?? item.produto?.nome;
          const complemento = item.observacao ?? item.obs ?? item.nota;
          const partes = [];
          if (quantidade) partes.push(`${quantidade}x`);
          if (nome) partes.push(nome);
          if (!partes.length) {
            const fallback = Object.values(item)
              .filter((v) => typeof v === "string" || typeof v === "number")
              .map((v) => String(v).trim())
              .filter(Boolean);
            if (fallback.length) partes.push(fallback.join(" - "));
          }
          if (complemento) partes.push(`(${complemento})`);
          return partes.join(" ").trim() || null;
        }
        return null;
      })
      .filter(Boolean);
  })();

  const waUrl = buildWhatsAppUrl(
    whatsapp,
    `Olá! Tenho interesse no produto: *${produto.nome}* (R$ ${precoFormatado}). Podem me ajudar?`
  );

  return (
    <div className="card product-card shadow-sm border-0">
      <div className="product-card-img-wrap">
        <img
          src={imagemSrc}
          alt={produto.nome || "Produto sem imagem"}
          onError={handleImageError}
          className="card-img-top img-fluid"
        />
        {temDesconto && (
          <span className="product-badge product-badge--discount">{desconto}% OFF</span>
        )}
        {badge && !temDesconto && (
          <span className="product-badge product-badge--promo">{badge}</span>
        )}
      </div>

      <div className="card-body text-center d-flex flex-column">
        <h6 className="fw-bold text-dark">{produto.nome}</h6>

        {produto.descricao && (
          <p className="product-card-description text-muted small mb-2">{produto.descricao}</p>
        )}

        {itens.length > 0 && (
          <ul className="product-card-items list-unstyled text-start">
            {itens.map((linha, index) => (
              <li key={`${produto.id || "produto"}-item-${index}`}>{linha}</li>
            ))}
          </ul>
        )}

        <div className="mt-auto">
          {temDesconto && (
            <p className="product-card-original text-muted mb-0">
              De: <span className="text-decoration-line-through">R$ {originalFormatado}</span>
            </p>
          )}
          <p className="product-card-price text-danger mb-0 fw-bold">R$ {precoFormatado}</p>
          <p className="product-card-installment text-muted small mb-3">
            ou {parcelas}x de R$ {parcelaFormatada}
            {semJuros && <span className="text-success fw-semibold"> sem juros</span>}
          </p>

          <div className="d-flex flex-column gap-2">
            <Link to={`/produto/${produto.id}`} className="btn btn-outline-warning btn-sm">
              Ver detalhes
            </Link>
            <a
              href={waUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="btn btn-outline-success btn-sm product-card-wa-btn"
            >
              <WaIconSmall />
              Pedir via WhatsApp
            </a>
          </div>
        </div>
      </div>
    </div>
  );
}
