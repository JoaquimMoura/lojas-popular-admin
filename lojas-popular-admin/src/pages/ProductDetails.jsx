import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { storeApi } from "../services/storeApi";
import { resolveImageUrl } from "../utils/url";
import { useCart } from "../context/CartContext";
import WhatsAppButton from "../components/WhatsAppButton";

export default function ProductDetails() {
  const { id } = useParams();
  const { add } = useCart();
  const [p, setP] = useState(null);
  const [active, setActive] = useState(null);

  useEffect(() => {
    (async () => {
      const data = await storeApi.getProduct(id);
      setP(data);
      setActive(resolveImageUrl(data.imagemUrl) ?? null);
    })();
  }, [id]);

  const gallery = useMemo(() => (p?.galeria ?? []).map(resolveImageUrl), [p]);

  if (!p) return <div className="py-4 text-muted">Carregando…</div>;

  return (
    <div className="py-3">
      <div className="row g-4">
        <div className="col-md-6">
          <div className="border rounded p-2 text-center">
            <img
              src={active ?? "https://via.placeholder.com/800x600?text=Produto"}
              alt={p.nome}
              style={{ maxHeight: 420, width: "100%", objectFit: "contain" }}
            />
          </div>

          {gallery.length > 0 && (
            <div className="d-flex gap-2 mt-2 flex-wrap">
              {gallery.map((url, i) => (
                <button
                  key={i}
                  className={`btn p-0 border ${active === url ? "border-primary" : "border-1"}`}
                  onClick={() => setActive(url)}
                  style={{ width: 80, height: 80, overflow: "hidden" }}
                >
                  <img src={url} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} />
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="col-md-6">
          <h3 className="mb-2">{p.nome}</h3>
          <div className="text-muted mb-2">{p.categoria}</div>
          <div className="h4 fw-bold mb-3">R$ {Number(p.preco).toFixed(2)}</div>

          <p className="mb-3">{p.descricao}</p>

          <div className="d-flex gap-2">
            <button className="btn btn-primary" onClick={() => add(p, 1)}>Adicionar ao carrinho</button>
            <a
              className="btn btn-outline-success"
              href={`https://wa.me/5511986789299?text=${encodeURIComponent(`Olá! Quero o produto ${p.nome} (id ${p.id}).`)}&utm_source=site&utm_medium=produto&utm_campaign=whatsapp`}
              target="_blank"
              rel="noreferrer"
            >
              Comprar via WhatsApp
            </a>
          </div>

          <div className="mt-4">
            <small className="text-muted">Estoque: {p.estoque ?? 0} unidades</small>
          </div>
        </div>
      </div>

      <WhatsAppButton />
    </div>
  );
}
