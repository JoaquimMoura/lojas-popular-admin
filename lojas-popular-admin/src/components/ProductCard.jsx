// src/components/ProductCard.jsx
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { DEFAULT_IMAGE, resolveImageUrl } from "../utils/url";
import "../styles/ProductCard.css";

export default function ProductCard({ produto }) {
  if (!produto) return null;

  const [imagemSrc, setImagemSrc] = useState(() => {
    return resolveImageUrl(produto.imagemUrl);
  });
  const [imagemErro, setImagemErro] = useState(false);

  useEffect(() => {
    setImagemErro(false);
    setImagemSrc(resolveImageUrl(produto.imagemUrl));
  }, [produto.imagemUrl]);

  const handleImageError = () => {
    if (imagemErro) return; // evita loop quando fallback tambem falha
    setImagemErro(true);
    setImagemSrc(DEFAULT_IMAGE);
  };

  const precoFormatado =
    typeof produto.preco === "number"
      ? produto.preco.toFixed(2)
      : Number(produto.preco || 0).toFixed(2);

  const itens = (() => {
    const bruto =
      produto.itens ??
      produto.items ??
      produto.variacoes ??
      produto.componentes ??
      [];

    let lista = [];

    if (Array.isArray(bruto)) {
      lista = bruto;
    } else if (typeof bruto === "string" && bruto.trim().length) {
      try {
        const parsed = JSON.parse(bruto);
        if (Array.isArray(parsed)) {
          lista = parsed;
        }
      } catch {
        lista = bruto.split(/[\r\n,;]+/);
      }
    }

    return lista
      .map((item) => {
        if (!item) return null;
        if (typeof item === "string" || typeof item === "number") {
          return String(item).trim();
        }
        if (typeof item === "object") {
          const quantidade =
            item.quantidade ?? item.qtd ?? item.qtde ?? item.estoque;
          const nome =
            item.nome ??
            item.itemNome ??
            item.titulo ??
            item.descricao ??
            item.name ??
            item.label ??
            item.produtoNome ??
            item.produto?.nome;
          const complemento = item.observacao ?? item.obs ?? item.nota;

          const partes = [];

          if (quantidade) partes.push(`${quantidade}x`);
          if (nome) partes.push(nome);

          if (!partes.length) {
            const fallback = Object.values(item)
              .filter(
                (valor) =>
                  typeof valor === "string" || typeof valor === "number"
              )
              .map((valor) => String(valor).trim())
              .filter(Boolean);

            if (fallback.length) {
              partes.push(fallback.join(" - "));
            }
          }

          if (complemento) partes.push(`(${complemento})`);

          return partes.join(" ").trim() || null;
        }

        return null;
      })
      .filter(Boolean);
  })();

  const possuiItens = itens.length > 0;

  return (
    <div className="card product-card shadow-sm border-0">
      <img
        src={imagemSrc}
        alt={produto.nome || "Produto sem imagem"}
        onError={handleImageError} // fallback extra contra 404
        className="card-img-top img-fluid rounded shadow-sm"
      />
      <div className="card-body text-center">
        <h6 className="fw-bold text-dark">{produto.nome}</h6>
        {produto.descricao && (
          <p className="product-card-description text-muted small mb-2">
            {produto.descricao}
          </p>
        )}
        {possuiItens && (
          <ul className="product-card-items list-unstyled text-start">
            {itens.map((linha, index) => (
              <li key={`${produto.id || "produto"}-item-${index}`}>{linha}</li>
            ))}
          </ul>
        )}
        <p className="text-danger mb-2">R$ {precoFormatado}</p>
        <Link
          to={`/produto/${produto.id}`}
          className="btn btn-outline-warning btn-sm"
        >
          Ver detalhes
        </Link>
      </div>
    </div>
  );
}


