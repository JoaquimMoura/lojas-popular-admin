import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

import ProductCard from "../components/ProductCard";
import WhatsAppButton from "../components/WhatsAppButton";
import { categoriesApi } from "../services/categoriesApi";
import { storeApi } from "../services/storeApi";

export default function CategoryProducts() {
  const { id } = useParams();
  const [categoria, setCategoria] = useState(null);
  const [produtos, setProdutos] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      if (!id) return;
      setLoading(true);
      try {
        const [categoriaResp, produtosResp] = await Promise.allSettled([
          categoriesApi.byId(id),
          storeApi.listProducts({ page: 0, size: 48, categoriaId: id }),
        ]);

        if (categoriaResp.status === "fulfilled") {
          setCategoria(categoriaResp.value);
        } else {
          setCategoria(null);
        }
        if (produtosResp.status === "fulfilled") {
          setProdutos(produtosResp.value ?? []);
        } else {
          setProdutos([]);
        }
      } finally {
        setLoading(false);
      }
    }

    load();
  }, [id]);

  return (
    <div className="container py-4">
      <header className="text-center mb-4">
        <h2 className="text-danger fw-bold">
          {categoria?.nome ?? "Categoria"}
        </h2>
        {categoria?.descricao && (
          <p className="text-muted">{categoria.descricao}</p>
        )}
      </header>

      {loading && <div className="text-center text-muted py-5">Carregando produtos...</div>}

      {!loading && produtos.length === 0 && (
        <div className="text-center text-muted py-5">
          Nenhum produto cadastrado nesta categoria por enquanto.
        </div>
      )}

      <div className="row g-4">
        {produtos.map((produto) => (
          <div className="col-6 col-md-4 col-lg-3" key={produto.id}>
            <ProductCard produto={produto} />
          </div>
        ))}
      </div>

      <div className="text-center mt-5">
        <WhatsAppButton
          text="Ola! Gostaria de falar com um atendente sobre os produtos da categoria."
          label="Falar com um atendente"
        />
      </div>
    </div>
  );
}
