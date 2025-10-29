import { useEffect, useState } from "react";
import { storeApi } from "../services/storeApi";
import ProductCard from "../components/ProductCard";
import WhatsAppButton from "../components/WhatsAppButton";

export default function StoreHome() {
  const [items, setItems] = useState([]);

  useEffect(() => {
    (async () => {
      const data = await storeApi.listProducts({ page: 0, size: 24 });
      setItems(data);
    })();
  }, []);

  return (
    <div className="py-3">
      <div className="mb-4 p-4 rounded bg-light border">
        <h4 className="mb-1">Popular Móveis</h4>
        <div className="text-muted">Avenida Presidente Médici, 417</div>
      </div>

      <div className="row row-cols-1 row-cols-sm-2 row-cols-md-3 row-cols-xl-4 g-3">
        {items.map(p => (
          <div className="col" key={p.id}>
            <ProductCard product={p} />
          </div>
        ))}
        {items.length === 0 && (
          <div className="col-12 text-center text-muted py-5">Nenhum produto disponível.</div>
        )}
      </div>

      <WhatsAppButton />
    </div>
  );
}
