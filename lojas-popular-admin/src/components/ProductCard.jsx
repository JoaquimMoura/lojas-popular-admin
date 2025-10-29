import { Link } from "react-router-dom";
import { resolveImageUrl } from "../utils/url";
import { useCart } from "../context/CartContext";

export default function ProductCard({ product }) {
  const { add } = useCart();

  return (
    <div className="card h-100">
      <Link to={`/produto/${product.id}`} className="text-decoration-none text-dark">
        <img
          src={resolveImageUrl(product.imagemUrl) ?? "https://via.placeholder.com/600x400?text=Produto"}
          className="card-img-top"
          alt={product.nome}
          style={{ height: 180, objectFit: "cover" }}
        />
      </Link>
      <div className="card-body d-flex flex-column">
        <h6 className="card-title mb-1">{product.nome}</h6>
        <div className="text-muted mb-2">{product.categoria || "Categoria"}</div>
        <div className="fw-bold mb-3">R$ {Number(product.preco).toFixed(2)}</div>
        <div className="mt-auto d-flex gap-2">
          <Link to={`/produto/${product.id}`} className="btn btn-outline-secondary btn-sm">Detalhes</Link>
          <button className="btn btn-primary btn-sm" onClick={() => add(product, 1)}>
            Adicionar
          </button>
        </div>
      </div>
    </div>
  );
}
