// src/pages/StoreHome.jsx
import { useEffect, useState } from "react";
import { storeApi } from "../services/storeApi";
import ProductCard from "../components/ProductCard";
import WhatsAppButton from "../components/WhatsAppButton";

export default function StoreHome() {
  const [produtos, setProdutos] = useState([]);

  useEffect(() => {
    async function load() {
      try {
        const data = await storeApi.listProducts({ page: 0, size: 24 });
        setProdutos(data);
      } catch (error) {
        console.error("Erro ao carregar produtos:", error);
      }
    }

    load();
  }, []);

  return (
    <div className="container py-4">
      <h2 className="text-center mb-4 text-danger fw-bold">Popular Moveis</h2>
      <p className="text-center text-muted mb-5">
        Moveis com qualidade e preco popular!
      </p>

      <div className="row g-4">
        {produtos.map((produto) => (
          <div className="col-6 col-md-4 col-lg-3" key={produto.id}>
            <ProductCard produto={produto} />
          </div>
        ))}
      </div>

      <div className="text-center mt-5">
        <WhatsAppButton
          text="Ola! Gostaria de falar com um atendente sobre os produtos."
          label="Falar com um atendente"
        />
      </div>
    </div>
  );
}
